# Deploy Script Design

Date: 2026-09-13
Status: Draft
Branch: feat/deploy-script

## Overview

A single bash script `scripts/deploy.sh` that builds the application jar, builds and
pushes a Docker image to ECR, then deploys the infrastructure with Terraform using the
pushed image tag. One command takes the latest committed code to a running ECS service.

## Goals

- End-to-end deploy with a single command: build -> image -> push -> terraform apply.
- Deterministic, traceable image tag tied to the current git commit.
- Fail fast at the first failing step; no silent partial deploys.
- Runs from any directory (computes repo root from its own path).

## Non-Goals

- No dev/prod environment split.
- No rollback logic.
- No image/docker cleanup (`docker system prune`).
- No interactive confirmation prompts (uses `terraform apply -auto-approve`).

## Design

### Location

`scripts/deploy.sh`, executable, shebang `#!/usr/bin/env bash`.

### Constants

- `REGION=eu-central-1` (matches Terraform provider and state bucket region).
- `REPO_NAME=smort`.
- `APP_JAR=target/smort-0.0.1-SNAPSHOT.jar` (matches the jar copied by the Dockerfile).
- `SHA=$(git rev-parse --short HEAD)`.
- Local image tag `smort:$SHA`; ECR tag `<account>.dkr.ecr.eu-central-1.amazonaws.com/smort:$SHA`.

### Steps

1. **Build jar**: `./mvnw -DskipTests package`.
2. **Build image**: `docker build -t "$SHA_TAG" .` where `SHA_TAG=smort:$SHA`.
3. **Resolve ECR account**: `ACCOUNT=$(aws sts get-caller-identity --query Account --output text)`.
   Construct `REPO_URL="$ACCOUNT.dkr.ecr.$REGION.amazonaws.com/$REPO_NAME"`.
4. **Login**: `aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$ACCOUNT.dkr.ecr.$REGION.amazonaws.com"`.
5. **Tag & push**: `docker tag "$SHA_TAG" "$REPO_URL:$SHA"` then `docker push "$REPO_URL:$SHA"`.
6. **Deploy**: `cd terraform && terraform init && terraform apply -auto-approve -var "image_tag=$SHA"`.

### Behavior details

- `set -euo pipefail` so the script aborts on any error, including the `get-login-password | docker login` pipe.
- Prints the computed image tag and repo URL up front for visibility.
- Root-relative paths derived via `SCRIPT_DIR` from `$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)`; `ROOT="$SCRIPT_DIR/.."`.
- `cd terraform` runs against `ROOT/terraform` so the s3 backend, modules, and state resolve correctly.

## Deployment mechanics

Changing `image_tag` changes the `container_image` of the ECS task definition, producing a
new task definition revision referenced by the ECS service, which triggers an automatic new
deployment. No manual `--force-new-deployment` needed.

## Error handling

`set -euo pipefail` is the sole error-handling mechanism. Each step aborts on failure,
leaving a clear exit code and the last command's error output.

## Testing

The script is not unit-tested (bash). Verification is a manual end-to-end run by the human:
build, push, and `terraform apply` are side-effectful and require AWS/Docker access.

## Open questions

None.