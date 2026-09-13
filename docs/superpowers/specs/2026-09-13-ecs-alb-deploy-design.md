# ECS Fargate + ALB Deployment Design

## Purpose

Deploy the smort Spring Boot application to AWS using ECS Fargate behind an
internet-facing Application Load Balancer, all defined in Terraform. This
first iteration proves the wiring end-to-end using a placeholder container
image; the real image build is a separate follow-up.

## Goals & Non-Goals

### Goals
- Stand up an ECS Fargate cluster and service.
- Stand up an internet-facing ALB routing HTTP traffic to the Fargate service.
- Use standard, minimal-customization AWS patterns.
- Keep everything in the existing `terraform/` layout, adding separate
  `alb` and `ecs` modules that mirror the existing `vpc` module style.
- Provide a running, verifiable task via a placeholder image.

### Non-Goals
- Building/pushing a real application image (no ECR yet).
- Multi-AZ resilience (VPC is single-AZ by existing decision).
- Persistent file storage (BASE_DATA_DIR is ephemeral by existing decision).
- CI/CD pipeline.

## Existing State

The repo already has a Terraform VPC module (`terraform/modules/vpc`) deployed
in `eu-central-1`, single AZ `eu-central-1a`, containing:
- One public subnet (`10.0.0.0/24`) and one private subnet (`10.0.1.0/24`).
- Internet gateway, NAT gateway (in the public subnet) for private egress.
- DynamoDB gateway endpoint (not relevant to this change, left untouched).

Root `terraform/main.tf` wires the VPC module; `terraform/outputs.tf` exposes
`vpc_id`, `public_subnet_id`, `private_subnet_id`, etc.

## Architecture

```
                      ┌─────────────┐
  Internet ──► HTTP ─►│  ALB (pub)  │
                      └──────┬──────┘
                             │ :80
                      ┌──────▼──────┐
                      │ Fargate task│  (private subnet)
                      │ nginx:latest│
                      └──────┬──────┘
                             │ egress via NAT
                             ▼
                         Internet (image pull)
```

- ALB lives in the **public** subnet (`eu-central-1a`).
- Fargate task lives in the **private** subnet, egress via the existing NAT.
- ALB reaches the task within the VPC (cross-subnet in the same VPC/AZ).

## New Modules

### `terraform/modules/alb/`

Internet-facing application load balancer.

- `aws_lb` — internet-facing, `application`, security groups, in the public
  subnet (`eu-central-1a`).
- `aws_lb_target_group` — target type `ip`, protocol HTTP, port 80; health
  check path `/`, healthy/unhealthy thresholds and interval at AWS defaults.
- `aws_lb_listener` — HTTP on port 80, default action forwards to the target
  group.
- `aws_security_group` for the ALB:
  - Ingress: HTTP (80) from `0.0.0.0/0`.
  - Egress: to the task security group on port 80 (or all, standard).
- **Inputs**: `name`, `vpc_id`, `public_subnet_id`.
- **Outputs**: `alb_id`, `alb_arn`, `alb_security_group_id`, `alb_dns_name`,
  `target_group_arn`, `listener_arn`.

### `terraform/modules/ecs/`

Fargate cluster and service.

- `aws_ecs_cluster` — standard Fargate cluster (no capacity providers).
- `aws_iam_role` + attachment — ECS task execution role using the AWS managed
  policy `AmazonECSTaskExecutionRolePolicy`.
- `aws_ecs_task_definition` — FARGATE launch type, family `smort`; single
  container using the placeholder image (default `nginx:latest`), container
  port 80; requires compatible Fargate CPU/memory (e.g. 256/512).
- `aws_ecs_service` — FARGATE launch type, placed in the private subnet,
  network mode `awsvpc`, attached to the ALB target group/listener via a load
  balancer block, using the task security group.
- `aws_security_group` for tasks:
  - Ingress: HTTP (80) from the ALB security group.
  - Egress: all (outbound via NAT).
- **Inputs**: `name`, `vpc_id`, `private_subnet_id`, `alb_security_group_id`,
  `target_group_arn`, `container_image` (default `nginx:latest`),
  `container_port` (default 80).
- **Outputs**: `cluster_name`, `service_name`, `task_definition_arn`.

## Root `terraform/main.tf` Wiring

- Add `module "alb"` referencing `module.vpc.public_subnet_id` and
  `module.vpc.vpc_id`.
- Add `module "ecs"` referencing `module.vpc.private_subnet_id`,
  `module.vpc.vpc_id`, and the ALB module's outputs.

## Root `terraform/outputs.tf` Additions

- `alb_dns_name`
- `ecs_cluster_name`
- `ecs_service_name`

## Standard Patterns / Deliberate Defaults

- ALB, target group, listener, health check, and IAM all use AWS defaults
  where possible — no custom tuning.
- Single-AZ single-subnet ALB (accepted limitation per existing VPC design).
- Placeholder image `nginx:latest`; container port 80; health check on `/`.
- The real smort image (Dockerfile + ECR) is intentionally out of scope.

## Error Handling & Rollout

- `terraform plan` before `apply`; changes limited to new resources, existing
  VPC untouched.
- If the ALB fails health checks, verify task is running (`aws ecs
  describe-services`) and the task security group ingress/egress rules.
- No teardown logic needed beyond removing the module blocks.

## Testing

- No automated tests for Terraform (per repo conventions; tests only when
  explicitly requested).
- Manual verification: `terraform apply`, then curl the `alb_dns_name` and
  confirm the placeholder returns HTTP 200 and a healthy target.
- Compilation/build steps are skipped per AGENTS.md (human owns the build).

## Future Work (Out of Scope)

- Real Dockerfile + ECR repo and image.
- Persistent storage (EFS) for `BASE_DATA_DIR`.
- Multi-AZ resilience.
- CI/CD pipeline for image builds.