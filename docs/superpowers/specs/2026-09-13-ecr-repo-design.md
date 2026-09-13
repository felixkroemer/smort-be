# ECR Repository + ECS Image Wiring Design

## Purpose

Add a Terraform module that provisions an Amazon ECR repository for the smort
application, and configure the existing ECS task definition to run an image
from that repository. Image builds are handled by a separate local bash script
the human maintains; this change is Terraform-only.

## Goals & Non-Goals

### Goals
- Provision an ECR repository for the smort app images.
- Point the ECS task definition at an image from that repository.
- Keep the ECR repository URL derived in Terraform so it is never hard-coded
  in a variable.
- Add an ECR image lifecycle policy to prune old images.

### Non-Goals
- Building or pushing images (handled by a separate local bash script).
- Dockerfile / CI-CD image build pipeline.
- Changing the existing `ecs` module's resources (its `container_image` input
  and ECS exec role already cover ECR pulls).

## Existing State

The root `terraform/main.tf` wires `vpc`, `alb`, and `ecs` modules. The `ecs`
module already:
- Accepts a `container_image` input (currently defaulting to `nginx:latest`).
- Uses the ECS task execution role with the AWS-managed
  `AmazonECSTaskExecutionRolePolicy`, which already grants ECR
  `GetAuthorizationToken`, `BatchGetImage`, and `GetDownloadUrlForLayer` for
  private repo pulls. No additional IAM is needed.

## Architecture

```
                    ┌──────────────────────┐
  bash script ──►   │  ECR (eu-central-1)   │
  (build + push)    │  smort images         │
                    └──────────┬───────────┘
                               │ image pull
                    ┌──────────▼───────────┐
                    │  Fargate task        │
                    │  <ecr-url>:<tag>     │
                    └──────────────────────┘
```

## New Module: `terraform/modules/ecr/`

- `aws_ecr_repository` — name `var.name`, `image_tag_mutability = "MUTABLE"`
  (the local script pushes `:latest` / concrete tags), `scan_on_push = true`.
- `aws_ecr_lifecycle_policy` — keep the most recent `max_image_count` images,
  expire older ones.
- **Inputs**: `name`, `max_image_count` (default 5).
- **Outputs**: `repository_url`, `repository_name`.

## Root `terraform/main.tf` Wiring

- Add `module "ecr"` (source `./modules/ecr`, `name = "smort"`).
- Add a root `variable "image_tag"` (default `"latest"`) — only the tag is
  parameterized; the repository URL/domain is always derived.
- Pass to `module "ecs"`:
  `container_image = "${module.ecr.repository_url}:${var.image_tag}"`.

The `ecs` module is unchanged; it already accepts `container_image`.

## Root `terraform/outputs.tf` Additions

- `ecr_repository_url` → `module.ecr.repository_url`.
- `ecr_repository_name` → `module.ecr.repository_name`.

## Standard Patterns / Deliberate Defaults

- Repository URL is always `${module.ecr.repository_url}`; only the image tag
  is passed as a variable.
- Default image tag `latest`, overridable via `terraform apply -var=image_tag=...`.
- MUTABLE tags and lifecycle pruning (keep 5) to match the local build/rollout
  workflow.

## Script Flow (owned by the human, out of scope)

build → push `:tag` to the repo → `terraform apply -var=image_tag=<tag>` →
Terraform registers a new task definition and updates the service.

## Error Handling & Rollout

- `terraform plan` before `apply`; changes are additive (new ECR module) and
  the ECS task image reference updates.
- If the task fails to start, verify an image with the referenced tag has been
  pushed to the repo.

## Testing

- No automated Terraform tests (per repo conventions; tests only when
  explicitly requested).
- Compilation/build steps are skipped per AGENTS.md (human owns the build).

## Future Work (Out of Scope)

- Real Dockerfile + image build/push tooling.
- CI-CD pipeline for image builds.