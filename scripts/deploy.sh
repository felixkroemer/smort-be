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
