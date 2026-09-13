# SSM / Secrets Config for ECS Deployment

Date: 2026-09-13
Status: Draft
Branch: feat/ssm-config

## Overview

Centralize application configuration in AWS parameter/secret stores and have the ECS
task definition reference them, instead of hardcoding env values in Terraform. The
values come from the repo-root `.env` file, which Terraform reads at apply time.

## Architecture (approach A)

Terraform reads `.env` and creates:
- **SSM Parameter Store** (type `String`) for non-secret config vars.
- **Secrets Manager** secrets for sensitive vars.

The ECS task definition then references the resulting ARNs via a `secrets` block, so
the container receives them as environment variables at runtime. ECS fetches the values
from the stores; they never appear in the task definition JSON.

## Classification

Secret vars (go to Secrets Manager), by default:
- `OPENAI_API_KEY`
- `AUTH0_CLIENT_ID`

All other keys present in `.env` are config vars (go to SSM Parameter Store).

## Naming

- SSM parameter name: `/smort/<KEY>`
- Secrets Manager secret name: `smort-<key-lowercased>`

## Terraform changes

### New: `terraform/secrets.tf` (root module)

- `locals.env_vars`: parse `.env` (via `file("${path.module}/../.env")`) into a
  `KEY => value` map. Skip blank lines and lines starting with `#`.
- `variable "secret_env_names"`: list of keys treated as secrets, default
  `["OPENAI_API_KEY", "AUTH0_CLIENT_ID"]`.
- `locals.config_env_vars` / `locals.secret_env_vars`: split `env_vars` by that list.
- `aws_ssm_parameter.app` (`for_each = config_env_vars`): `type = "String"`,
  `name = "/smort/${each.key}"`, `value = each.value`.
- `aws_secretsmanager_secret.app` + `aws_secretsmanager_secret_version.app`
  (`for_each = secret_env_vars`): name `smort-${lower(each.key)}`, `secret_string = each.value`.
- `locals.secret_arns`: merged map `KEY => ARN` across both stores.

### Modify: `terraform/main.tf`

- Pass `secret_arns = local.secret_arns` to the `ecs` module.

### Modify: `terraform/modules/ecs/variables.tf`

- Add `variable "secret_arns" { type = map(string); default = {} }`.

### Modify: `terraform/modules/ecs/main.tf`

- In `container_definitions`, add a `secrets` array built from
  `[for name, arn in var.secret_arns : { name = name, valueFrom = arn }]`.
  Kept empty (not rendered) when `secret_arns` is empty.

## IAM

No change needed. The execution role already attaches `AmazonECSTaskExecutionRolePolicy`,
which grants `ssm:GetParameters` and `secretsmanager:GetSecretValue`.

## Runtime effect

Changing a value in `.env` and re-applying updates the SSM parameter / secret. ECS picks
up new values on the next task deployment (a new task definition revision from the changed
`image_tag`, or a force-new-deployment).

## .env path and git

- `.env` is git-ignored and repo-root; path resolved as `terraform/../.env`.
- `.env` may be absent in a fresh worktree; Terraform apply is run where `.env` exists
  (the main checkout). Implementers must not run apply.

## Data-flow / error handling

- `set -e`-style behavior is not Terraform's concern; if `.env` is missing,
  `file()` fails at plan with a clear error.
- `.env` values pass through Terraform state (accepted for approach A).

## Testing

No unit tests. Verification is a manual `terraform plan` by the human against the live
account to confirm the `secrets` block and store resources render correctly. Implementers
must not run plan/apply against the real backend.

## Open questions

None.