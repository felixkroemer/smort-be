# EFS Persistent Analysis DBs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mount an EFS filesystem into the Fargate task so uploaded analysis SQLite DBs survive task restarts.

**Architecture:** Define the EFS resources directly inside the existing `ecs` Terraform module (filesystem, access point with UID/GID 0, one mount target per AZ, a security group allowing NFS from the ECS task SG, referenced in-module). Reference filesystem + access point in a `volume` block on the task definition and mount it at `BASE_DATA_DIR`. Drop the `user.home` prefix in `SmortProperties.getAnkiDbDirectory()` so the app writes directly under the mounted path.

**Tech Stack:** Terraform (AWS provider ~> 5.0), Amazon EFS, Amazon ECS (Fargate), Java/Spring Boot.

## Global Constraints

- Work happens on branch `feat/efs-persistent-data` in the worktree at `.worktrees/efs-persistent-data`. Never commit to main.
- Terraform module style mirrors existing modules: each has `main.tf`, `variables.tf`, `outputs.tf`; `name` prefix in `Name` tags.
- `transitEncryption = "ENABLED"` on the EFS volume; `iam = "DISABLED"` in `authorizationConfig`.
- EFS mount point container path must equal the `BASE_DATA_DIR` SSM value (sourced as `aws_ssm_parameter.base_data_dir.value` in the root module).
- Java build (`./mvnw compile`, `./mvnw test`) is explicitly skipped per AGENTS.md; the human compiles later. Do not attempt to build.
- No unit tests: this is infrastructure plus a one-line path change. Human verification is `terraform plan`/`apply` followed by an upload + task-replacement check.
- Terraform check/validation used in tasks (`terraform fmt`, `terraform validate`) is local and read-only; only `terraform plan`/`apply` is owned by the human.

---

## Mid-Execution Deviation (2026-09-19)

The first plan version created a standalone `terraform/modules/efs` module and wired it from the root module. During execution `terraform validate`/`plan` analysis surfaced two plan defects:

1. The task-definition code used a `volumes = [...]` list with camelCase keys (`efsVolumeConfiguration`, `fileSystemId`, ...). The AWS provider schema for `aws_ecs_task_definition` instead takes repeatable `volume` blocks with snake_case nested blocks (`efs_volume_configuration`, `file_system_id`, `transit_encryption`, `authorization_config`).
2. The root wiring created an unresolvable module dependency cycle: `module "efs"` imported `module.ecs.task_security_group_id`, while `module "ecs"` imported `module.efs.file_system_id` / `module.efs.access_point_id`.

The human chose structure **C**: define the EFS resources directly inside the `ecs` module (its SG ingress references `aws_security_group.task.id` in the same module, so no cross-module SG reference exists). The standalone `terraform/modules/efs/` module and the root `module "efs"` block are removed. Tasks below are rewritten for the chosen structure.

Later the human requested `terraform destroy` be obstacle-free: the `lifecycle { prevent_destroy = true }` block on the EFS filesystem was removed (DynamoDB had none), and `force_delete = true` was added to `aws_ecr_repository` so a non-empty repository does not block destroy.

---

### Task 1: Integrate EFS into the ECS module

**Files:**
- Modify: `terraform/modules/ecs/variables.tf` (keep only `data_dir_path` from the earlier additions; drop `efs_file_system_id` and `efs_access_point_id`)
- Modify: `terraform/modules/ecs/main.tf` (add EFS resources; replace the camelCase `volumes` list with a `volume` block; add `mountPoints` to the container definitions)
- Modify: `terraform/modules/ecs/outputs.tf` (remove `task_security_group_id` — nothing consumes it now)
- Delete: `terraform/modules/efs/` (whole directory, including variables/main/outputs)

**Interfaces:**
- Consumes: existing `name`, `vpc_id`, `private_subnet_ids` inputs (already present) plus root-provided `data_dir_path` (string).
- Produces: nothing new downstream; the EFS resources are internal to the ECS module.

- [ ] **Step 1: `terraform/modules/ecs/variables.tf`** — remove `efs_file_system_id` and `efs_access_point_id`; keep:

```hcl
variable "data_dir_path" {
  type        = string
  description = "Container path (BASE_DATA_DIR) to mount the EFS volume at."
}
```

- [ ] **Step 2: Add EFS resources to `terraform/modules/ecs/main.tf`** (e.g. after the `aws_security_group.task` resource):

```hcl
resource "aws_efs_file_system" "this" {
  creation_token = "${var.name}-data"

  encrypted = true

  tags = { Name = "${var.name}-data" }
}

resource "aws_efs_access_point" "this" {
  file_system_id = aws_efs_file_system.this.id

  posix_user {
    gid = 0
    uid = 0
  }

  root_directory {
    path = "/"
  }

  tags = { Name = "${var.name}-data" }
}

resource "aws_efs_mount_target" "this" {
  count           = length(var.private_subnet_ids)
  file_system_id  = aws_efs_file_system.this.id
  subnet_id       = var.private_subnet_ids[count.index]
  security_groups = [aws_security_group.efs.id]
}

resource "aws_security_group" "efs" {
  name   = "${var.name}-efs"
  vpc_id = var.vpc_id

  ingress {
    from_port       = 2049
    to_port         = 2049
    protocol        = "tcp"
    security_groups = [aws_security_group.task.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.name}-efs" }
}
```

The EFS SG ingress references `aws_security_group.task.id` — defined in the same module, so there is no cross-module SG reference.

- [ ] **Step 3: Replace the `volumes` list with a `volume` block in `terraform/modules/ecs/main.tf`**

Remove the earlier `volumes = [...]` list; insert between the `memory` line and `execution_role_arn`:

```hcl
  volume {
    name = "efs-data"

    efs_volume_configuration {
      file_system_id     = aws_efs_file_system.this.id
      transit_encryption = "ENABLED"

      authorization_config {
        access_point_id = aws_efs_access_point.this.id
        iam             = "DISABLED"
      }
    }
  }
```

- [ ] **Step 4: Add `mountPoints` to the container definitions**

Inside the `container_definitions` JSON block of the task definition, add as a sibling of `logConfiguration` in the container object:

```hcl
      mountPoints = [
        {
          sourceVolume  = "efs-data"
          containerPath = var.data_dir_path
        }
      ]
```

- [ ] **Step 5: `terraform/modules/ecs/outputs.tf`** — remove the `task_security_group_id` output (nothing consumes it).

- [ ] **Step 6: Delete the standalone EFS module**

Delete the whole `terraform/modules/efs/` directory (its resources now live in the ECS module).

- [ ] **Step 7: Verify formatting**

Run: `terraform fmt -recursive terraform/modules/ecs`
Expected: reformats; no other files modified.

- [ ] **Step 8: Commit**

```bash
git add -A terraform/modules/ecs terraform/modules/efs
git commit -m "feat: define EFS storage inside the ECS module"
```

---

### Task 2: Update root wiring

**Files:**
- Modify: `terraform/main.tf` (remove `module "efs"`; remove the EFS inputs from the ECS module call; keep `data_dir_path`)

**Interfaces:**
- Consumes: `aws_ssm_parameter.base_data_dir.value` (passed as `data_dir_path` into `module "ecs"`).
- Produces: nothing downstream; this is the wiring root.

- [ ] **Step 1: Remove the `module "efs"` block**

Delete the `module "efs"` block (created by the earlier plan version) entirely from `terraform/main.tf`.

- [ ] **Step 2: Remove EFS inputs from the ECS module call**

Inside the existing `module "ecs"` block, remove `efs_file_system_id` and `efs_access_point_id`; keep:

```hcl
  data_dir_path = aws_ssm_parameter.base_data_dir.value
```

- [ ] **Step 3: Verify formatting**

Run: `terraform fmt -recursive terraform`
Expected: reformats; no other files modified.

- [ ] **Step 4: Validate locally**

Run: `terraform init -backend=false && terraform validate`
Expected: `Success! The configuration is valid.`

- [ ] **Step 5: Commit**

```bash
git add terraform/main.tf
git commit -m "feat: wire EFS volume data dir through the root module"
```

---

### Task 3: Drop the user.home prefix in the app's DB path

**Files:**
- Modify: `src/main/java/com/felixkroemer/smort/common/config/SmortProperties.java:20-22`

**Interfaces:**
- Consumes: `baseDir` (String, from `smort.base-data-dir` = `BASE_DATA_DIR`), `analysisDbDirectoryName` (String).
- Produces: `getAnkiDbDirectory()` now returns `Path.of(baseDir, analysisDbDirectoryName)`; no callers change.

- [ ] **Step 1: Update `getAnkiDbDirectory()`**

Replace the method body in `SmortProperties.java`:

```java
  public Path getAnkiDbDirectory() {
    return Path.of(baseDir, analysisDbDirectoryName);
  }
```

The `import java.nio.file.Path;` stays. Remove nothing else.

- [ ] **Step 2: Compilation note**

Per AGENTS.md, do not run `./mvnw compile` or `./mvnw test`. Note in the commit/report that compilation is left to the human.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/felixkroemer/smort/common/config/SmortProperties.java
git commit -m "fix: resolve analysis DB directory without user.home prefix"
```

---

### Task 4: Human verification

Owned by the human; not executed by an implementing agent.

- [ ] **Step 1: Deploy infrastructure**

Run: `terraform plan` then `terraform apply` from the repo root.
Expected: creates the EFS filesystem, access point, 2 mount targets, EFS security group; updates the task definition with the EFS volume (`efs-data`) mounted at `BASE_DATA_DIR`; rolls the ECS service with the new task definition (with `desired_count = 1`).

- [ ] **Step 2: Confirm the SSM value**

Confirm the SSM parameter `/smort/BASE_DATA_DIR` equals the container mount path (e.g. `/data`). If it differs, update it so the mount and `SmortProperties` agree.

- [ ] **Step 3: Verify persistence**

1. In the app, upload an Anki collection and confirm the analysis/deck loading works.
2. Force a task replacement (e.g. `aws ecs update-service --cluster smort --service smort --force-new-deployment`).
3. Reopen the same analysis and confirm its notes are still readable (the SQLite file is now on EFS and survived the new task).

---

## Self-Review

**Spec coverage:**
- EFS resources (filesystem, access point, mount targets per AZ, SG) inside the ECS module, SG ingress from the in-module task SG → Task 1. ✅
- Task definition `volume` block (`efs_volume_configuration`, `transit_encryption = ENABLED`, `authorization_config` with `iam = DISABLED`) + `mountPoints` → Task 1. ✅
- Standalone EFS module removed; mount point and `BASE_DATA_DIR` share the same source (`aws_ssm_parameter.base_data_dir.value`) → Task 2. ✅
- `SmortProperties` drops `user.home` → Task 3. ✅
- Verification (plan/apply, SSM sync, upload + task-replacement check) → Task 4. ✅
- Caveat on single-task SQLite locking noted in design; no action required at `desired_count = 1`. ✅

**Placeholder scan:** no TBD/TODO; all steps contain concrete code or commands. ✅

**Type consistency:** `data_dir_path` is a string used for both `mountPoints.containerPath` and the `BASE_DATA_DIR` env (from SSM value). The EFS file system and access point IDs are referenced in-module (no cross-module values). ✅