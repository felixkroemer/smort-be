#!/usr/bin/env bash
set -euo pipefail

REGION="eu-central-1"
REPO_NAME="smort"
APP_JAR="target/smort-0.0.1-SNAPSHOT.jar"
SHA="$(git rev-parse --short HEAD)"
SHA_TAG="${REPO_NAME}:${SHA}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"

# Load .env values into the shell environment.
set -a
source "$ROOT/.env"
set +a

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
(
  cd "$ROOT/terraform" &&
  terraform init &&
  terraform apply -auto-approve \
    -var "image_tag=$SHA" \
    -var "base_data_dir=$BASE_DATA_DIR" \
    -var "analysis_db_directory_name=$ANALYSIS_DB_DIRECTORY_NAME" \
    -var "analysis_max_db_size=$ANALYSIS_MAX_DB_SIZE" \
    -var "auth0_issuer_uri=$AUTH0_ISSUER_URI" \
    -var "smort_allowed_email=$SMORT_ALLOWED_EMAIL" \
    -var "openai_model=${OPENAI_MODEL:-gpt-4o}" \
    -var "openai_api_key=$OPENAI_API_KEY" \
    -var "auth0_client_id=$AUTH0_CLIENT_ID"
)

echo "==> Deploy complete: ${REPO_URL}:${SHA}"
