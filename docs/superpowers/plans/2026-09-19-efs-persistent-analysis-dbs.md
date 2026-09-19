# EFS Persistent Analysis DBs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mount an EFS filesystem into the Fargate task so uploaded analysis SQLite DBs survive task restarts.

**Architecture:** Create an `efs` Terraform module (filesystem, access point with UID/GID 0, one mount target per AZ, a security group allowing NFS from the ECS task SG). Reference filesystem + access point as a `volumes` entry on the task definition and mount it at `BASE_DATA_DIR`. Drop the `user.home` prefix in `SmortProperties.getAnkiDbDirectory()` so the app writes directly under the mounted path.

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

### Task 1: Create the EFS module

**Files:**
- Create: `terraform/modules/efs/variables.tf`
- Create: `terraform/modules/efs/main.tf`
- Create: `terraform/modules/efs/outputs.tf`

**Interfaces:**
- Consumes: root module inputs `name` (string), `vpc_id` (string), `private_subnet_ids` (list(string)), `ecs_security_group_id` (string).
- Produces: outputs `file_system_id`, `access_point_id`, `security_group_id` (all strings), consumed by the ECS module wiring in Task 3.

- [ ] **Step 1: Create `terraform/modules/efs/variables.tf`**

```hcl
variable "name" {
  type        = string
  description = "Resource name prefix, used in Name tags."
}

variable "vpc_id" {
  type        = string
  description = "VPC the EFS mount targets are deployed into."
}

variable "private_subnet_ids" {
  type        = list(string)
  description = "Private subnets to place EFS mount targets in."
}

variable "ecs_security_group_id" {
  type        = string
  description = "Security group of the ECS tasks allowed to mount the filesystem."
}
```

- [ ] **Step 2: Create `terraform/modules/efs/main.tf`**

```hcl
resource "aws_efs_file_system" "this" {
  creation_token = "${var.name}-data"

  encrypted = true

  lifecycle {
    prevent_destroy = true
  }

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
    security_groups = [var.ecs_security_group_id]
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

- [ ] **Step 3: Create `terraform/modules/efs/outputs.tf`**

```hcl
output "file_system_id" {
  value = aws_efs_file_system.this.id
}

output "access_point_id" {
  value = aws_efs_access_point.this.id
}

output "security_group_id" {
  value = aws_security_group.efs.id
}
```

- [ ] **Step 4: Verify formatting**

Run: `terraform fmt -recursive terraform/modules/efs`
Expected: reformats (or reports no changes); no other files modified.

- [ ] **Step 5: Commit**

```bash
git add terraform/modules/efs
git commit -m "feat: add EFS module for persistent analysis DB storage"
```

---

### Task 2: Wire EFS volume into the ECS task definition

**Files:**
- Modify: `terraform/modules/ecs/variables.tf` (append three variables)
- Modify: `terraform/modules/ecs/main.tf:130-138` (add `volumes` to the task definition; add `mountPoints` to the container definitions)
- Modify: `terraform/modules/ecs/outputs.tf` (add `task_security_group_id` output)

**Interfaces:**
- Consumes: `module.efs` outputs `file_system_id`, `access_point_id`; root-provided `data_dir_path` (string).
- Produces: output `task_security_group_id` (string), consumed by the EFS module (via root wiring in Task 3).

- [ ] **Step 1: Append variables to `terraform/modules/ecs/variables.tf`**

```hcl
variable "efs_file_system_id" {
  type        = string
  description = "EFS filesystem ID for persistent analysis DBs."
}

variable "efs_access_point_id" {
  type        = string
  description = "EFS access point ID for persistent analysis DBs."
}

variable "data_dir_path" {
  type        = string
  description = "Container path (BASE_DATA_DIR) to mount the EFS volume at."
}
```

- [ ] **Step 2: Add `volumes` to the task definition in `terraform/modules/ecs/main.tf`**

Insert between the `memory` line (line 135) and `execution_role_arn` (line 136):

```hcl
  volumes = [
    {
      name = "efs-data"
      efsVolumeConfiguration = {
        fileSystemId      = var.efs_file_system_id
        transitEncryption = "ENABLED"
        authorizationConfig = {
          accessPointId = var.efs_access_point_id
          iam           = "DISABLED"
        }
      }
    }
  ]
```

- [ ] **Step 3: Add `mountPoints` to the container definitions**

Inside the `container_definitions` JSON block of the task definition (after the `logConfiguration` block), add a second top-level key in the container object:

```hcl
      mountPoints = [
        {
          sourceVolume  = "efs-data"
          containerPath = var.data_dir_path
        }
      ]
```

The container object must contain both `logConfiguration` and `mountPoints` as siblings.

- [ ] **Step 4: Add `task_security_group_id` to `terraform/modules/ecs/outputs.tf`**

```hcl
output "task_security_group_id" {
  value = aws_security_group.task.id
}
```

- [ ] **Step 5: Verify formatting**

Run: `terraform fmt -recursive terraform/modules/ecs`
Expected: reformats; no other files modified.

- [ ] **Step 6: Commit**

```bash
git add terraform/modules/ecs
git commit -m "feat: mount EFS volume into Fargate task"
```

---

### Task 3: Instantiate the EFS module and pass the data dir path

**Files:**
- Modify: `terraform/main.tf:32-37` (add `module "efs"` block) and `terraform/main.tf:59-78` (add EFS inputs to the ECS module call)

**Interfaces:**
- Consumes: `module.vpc.vpc_id`, `module.vpc.private_subnet_ids`, `aws_ssm_parameter.base_data_dir.value`, `module.efs` outputs, and (for the EFS module's `ecs_security_group_id`) the Task 2 output `module.ecs.task_security_group_id`.
- Produces: nothing downstream; this is the wiring root.

- [ ] **Step 1: Add the `efs` module to `terraform/main.tf`**

After the `dynamodb` module block (after line 57), add:

```hcl
module "efs" {
  source = "./modules/efs"

  name                  = "smort"
  vpc_id                = module.vpc.vpc_id
  private_subnet_ids    = module.vpc.private_subnet_ids
  ecs_security_group_id = module.ecs.task_security_group_id
}
```

- [ ] **Step 2: Pass EFS inputs into the ECS module call**

Inside the existing `module "ecs"` block (lines 59-78), add after `dynamodb_table_arn`:

```hcl
  efs_file_system_id  = module.efs.file_system_id
  efs_access_point_id = module.efs.access_point_id
  data_dir_path       = aws_ssm_parameter.base_data_dir.value
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
git commit -m "feat: wire EFS module into ECS task for persistent analysis DBs"
```

---

### Task 4: Drop the user.home prefix in the app's DB path

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

### Task 5: Human verification

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
- EFS module with filesystem, access point, mount targets per AZ, SG ingress from task SG → Task 1. ✅
- Task definition `volumes` + `mountPoints`, `transitEncryption = ENABLED`, `iam = DISABLED` → Task 2. ✅
- Mount point and `BASE_DATA_DIR` share the same source (`aws_ssm_parameter.base_data_dir.value`) → Task 3. ✅
- `SmortProperties` drops `user.home` → Task 4. ✅
- Verification (plan/apply, SSM sync, upload + task-replacement check) → Task 5. ✅
- Caveat on single-task SQLite locking noted in design; no action required at `desired_count = 1`. ✅

**Placeholder scan:** no TBD/TODO; all steps contain concrete code or commands. ✅

**Type consistency:** `data_dir_path` is a string used for both `mountPoints.containerPath` and the `BASE_DATA_DIR` env (from SSM value). Output names match across tasks: `file_system_id`, `access_point_id`, `task_security_group_id`. ✅