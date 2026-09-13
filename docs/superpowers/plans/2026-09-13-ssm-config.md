# SSM / Secrets Config Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Have Terraform read the repo-root `.env`, create SSM Parameter Store entries and Secrets Manager secrets from it, and wire their ARNs into the ECS task definition's `secrets` block so the container receives them as environment variables.

**Architecture:** A new root `terraform/secrets.tf` parses `.env` into a map, splits keys into config (SSM `String`) and secret (Secrets Manager), creates the stores, and exposes a `KEY => ARN` map. `terraform/main.tf` passes that map to the `ecs` module, which renders a `secrets` array in `container_definitions`.

**Tech Stack:** Terraform >= 1.3, AWS provider ~> 5.0, AWS SSM Parameter Store, AWS Secrets Manager, ECS Fargate.

## Global Constraints

- Terraform root is `terraform/`; `.env` is at repo root, resolved as `${path.module}/../.env`.
- SSM parameter name: `/smort/<KEY>`, type `String`.
- Secrets Manager secret name: `smort-<key-lowercased>`.
- Secret keys (default): `OPENAI_API_KEY`, `AUTH0_CLIENT_ID`; all other `.env` keys are config.
- No IAM changes (execution role already has `AmazonECSTaskExecutionRolePolicy`).
- Do NOT run `terraform init`/`plan`/`apply` against the real backend; do NOT run the Maven build. `.env` may be absent in the worktree. Only `terraform fmt` (offline) is permitted as a check.

---

### Task 1: Root `secrets.tf` — parse `.env`, create stores, expose ARN map

**Files:**
- Create: `terraform/secrets.tf`

**Interfaces:**
- Consumes: repo-root `.env`.
- Produces: `local.secret_arns` — `map(string)` of `KEY => ARN` (SSM + Secrets Manager), for Task 2.

- [ ] **Step 1: Create `terraform/secrets.tf`**

```hcl
variable "secret_env_names" {
  type        = list(string)
  description = "Keys from .env stored in Secrets Manager instead of SSM."
  default     = ["OPENAI_API_KEY", "AUTH0_CLIENT_ID"]
}

locals {
  env_lines = [
    for line in split("\n", file("${path.module}/../.env")) :
    trimspace(line)
    if trimspace(line) != "" && !startswith(trimspace(line), "#")
  ]

  env_vars = {
    for line in local.env_lines :
    regex("^([A-Za-z_][A-Za-z0-9_]*)=(.*)$", line)[0] => regex("^([A-Za-z_][A-Za-z0-9_]*)=(.*)$", line)[1]
  }

  secret_env_vars = { for k, v in local.env_vars : k => v if contains(var.secret_env_names, k) }
  config_env_vars = { for k, v in local.env_vars : k => v if !contains(var.secret_env_names, k) }
}

resource "aws_ssm_parameter" "app" {
  for_each = local.config_env_vars

  name  = "/smort/${each.key}"
  type  = "String"
  value = each.value
}

resource "aws_secretsmanager_secret" "app" {
  for_each = local.secret_env_vars

  name = "smort-${lower(each.key)}"
}

resource "aws_secretsmanager_secret_version" "app" {
  for_each = local.secret_env_vars

  secret_id     = aws_secretsmanager_secret.app[each.key].id
  secret_string = each.value
}

locals {
  secret_arns = merge(
    { for k, p in aws_ssm_parameter.app : k => p.arn },
    { for k, s in aws_secretsmanager_secret.app : k => s.arn },
  )
}
```

- [ ] **Step 2: Format check**

Run: `terraform fmt -check terraform/secrets.tf`
Expected: exits 0, no output (already formatted). Do NOT run `init`/`plan`/`apply`.

- [ ] **Step 3: Commit**

```bash
git add terraform/secrets.tf
git commit -m "feat(infra): create ssm/secrets from .env"
```

---

### Task 2: Wire `secret_arns` into the ECS task definition

**Files:**
- Modify: `terraform/main.tf` (ecs module block, after `container_image`)
- Modify: `terraform/modules/ecs/variables.tf` (append variable)
- Modify: `terraform/modules/ecs/main.tf` (`container_definitions`)

**Interfaces:**
- Consumes: `local.secret_arns` from Task 1.
- Produces: ECS task definition with a `secrets` array of `{ name, valueFrom }`.

- [ ] **Step 1: Pass the map in `terraform/main.tf`**

In the `module "ecs"` block, add this line after the `container_image` line:

```hcl
  secret_arns           = local.secret_arns
```

- [ ] **Step 2: Add the variable in `terraform/modules/ecs/variables.tf`**

Append:

```hcl
variable "secret_arns" {
  type        = map(string)
  description = "Map of environment variable name to SSM/Secrets Manager ARN injected into the container."
  default     = {}
}
```

- [ ] **Step 3: Render `secrets` in `terraform/modules/ecs/main.tf`**

Replace the `container_definitions` argument (currently lines 56-66) with:

```hcl
  container_definitions = jsonencode([
    merge(
      {
        name  = var.name
        image = var.container_image
        portMappings = [
          {
            containerPort = var.container_port
          }
        ]
      },
      length(var.secret_arns) > 0 ? {
        secrets = [
          for name, arn in var.secret_arns : {
            name      = name
            valueFrom = arn
          }
        ]
      } : {}
    )
  ])
```

- [ ] **Step 4: Format check**

Run: `terraform fmt -check terraform/main.tf terraform/modules/ecs/main.tf terraform/modules/ecs/variables.tf`
Expected: exits 0, no output. Do NOT run `init`/`plan`/`apply`.

- [ ] **Step 5: Commit**

```bash
git add terraform/main.tf terraform/modules/ecs/main.tf terraform/modules/ecs/variables.tf
git commit -m "feat(infra): inject ssm/secrets into ecs task definition"
```

---

## Self-Review

- **Spec coverage:** `.env` parsing (Task 1 Step 1), SSM `String` `/smort/<KEY>` (Task 1), Secrets Manager `smort-<key>` (Task 1), secret/config split with default secret keys (Task 1), ARN map (Task 1), pass to ECS (Task 2 Step 1), `secrets` block (Task 2 Step 3), no IAM change (noted, no task needed). All covered.
- **Placeholder scan:** no TBD/TODO; all steps contain concrete code.
- **Type consistency:** `local.secret_arns` is `map(string)` (Task 1) and matches `variable "secret_arns" { type = map(string) }` (Task 2) and the `for name, arn in var.secret_arns` iteration (Task 2). Names consistent.
- **Note:** `.env` needs `BASE_DATA_DIR`, `ANALYSIS_DB_DIRECTORY_NAME`, `ANALYSIS_MAX_DB_SIZE` added for the app to boot; stores are only created for keys present in `.env` (documented in spec). Human adds them to `.env`.

**Verification note:** Compilation/`terraform plan`/`apply` are owned by the human and must not be run by implementers. `terraform fmt -check` is the only permitted offline check.