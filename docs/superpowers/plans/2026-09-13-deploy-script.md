# Deploy Script Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create `scripts/deploy.sh`, a single command that builds the jar, builds and pushes a Docker image to ECR, and applies Terraform with the pushed image tag.

**Architecture:** A standalone bash script using `set -euo pipefail`. It computes the repo root from its own path, builds the app jar and Docker image, resolves the ECR repo URL from the AWS account id, authenticates Docker to ECR, pushes the image tagged with the current git short SHA, then runs `terraform init && terraform apply -auto-approve -var image_tag=<SHA>`.

**Tech Stack:** bash, `./mvnw` (Maven), Docker, AWS CLI, Terraform.

## Global Constraints

- Script lives at `scripts/deploy.sh`, executable, shebang `#!/usr/bin/env bash`.
- Region hardcoded `eu-central-1`.
- Repo name `smort`.
- Jar path `target/smort-0.0.1-SNAPSHOT.jar`.
- Image tag = `git rev-parse --short HEAD`.
- Uses default AWS credential chain (no profile assumptions).
- `set -euo pipefail` at top.
- Runs `terraform apply -auto-approve` (no interactive prompts).
- No tests written (bash, side-effectful end-to-end deploy; human verifies).

---

### Task 1: Create `scripts/deploy.sh`

**Files:**
- Create: `scripts/deploy.sh`

**Interfaces:**
- Consumes: nothing (self-contained).
- Produces: executable script run via `scripts/deploy.sh` from any directory.

- [ ] **Step 1: Create the script**

```bash
#!/usr/bin/env bash
set -euo pipefail

REGION="eu-central-1"
REPO_NAME="smort"
APP_JAR="target/smort-0.0.1-SNAPSHOT.jar"
SHA="$(git rev-parse --short HEAD)"
SHA_TAG="${REPO_NAME}:${SHA}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"

echo "==> Building application jar"
( cd "$ROOT" && ./mvnw -DskipTests package )

echo "==> Building docker image ${SHA_TAG}"
( cd "$ROOT" && docker build -t "$SHA_TAG" . )

echo "==> Resolving ECR repository"
ACCOUNT="$(aws sts get-caller-identity --query Account --output text)"
ECR_ENDPOINT="${ACCOUNT}.dkr.ecr.${REGION}.amazonaws.com"
REPO_URL="${ECR_ENDPOINT}/${REPO_NAME}"

echo "==> Logging in to ECR"
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$ECR_ENDPOINT"

echo "==> Pushing image ${REPO_URL}:${SHA}"
docker tag "$SHA_TAG" "${REPO_URL}:${SHA}"
docker push "${REPO_URL}:${SHA}"

echo "==> Deploying with terraform (image_tag=${SHA})"
( cd "$ROOT/terraform" && terraform init && terraform apply -auto-approve -var "image_tag=$SHA" )

echo "==> Deploy complete: ${REPO_URL}:${SHA}"
```

- [ ] **Step 2: Make it executable and sanity-check syntax**

Run: `chmod +x scripts/deploy.sh && bash -n scripts/deploy.sh`
Expected: exits 0, no output (syntax valid).

- [ ] **Step 3: Commit**

```bash
git add scripts/deploy.sh
git commit -m "feat: add end-to-end deploy script"
```

---

## Self-Review

- **Spec coverage:** script location (`scripts/deploy.sh`), constants (region `eu-central-1`, repo `smort`, jar path, SHA tag), build step (`./mvnw -DskipTests package`), image build, ECR account resolution via `sts get-caller-identity`, Docker login, tag & push, and `terraform init` + `apply -auto-approve -var image_tag=<SHA>` — all implemented in Task 1.
- **Placeholder scan:** no TBD/TODO; all steps contain concrete code.
- **Type consistency:** no cross-task signatures; single task. Root resolution (`SCRIPT_DIR`/`ROOT`) used consistently in subshells.

**Verification note:** Compilation and the side-effectful deploy (Docker push, Terraform apply) are owned by the human. The script is not unit-tested. Human runs `scripts/deploy.sh` end-to-end to verify.