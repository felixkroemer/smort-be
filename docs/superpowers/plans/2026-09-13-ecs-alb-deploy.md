# ECS Fargate + ALB Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deploy a placeholder container to AWS via ECS Fargate behind an internet-facing ALB, defined in Terraform with separate `alb` and `ecs` modules.

**Architecture:** An internet-facing ALB in the existing single public subnet (`eu-central-1a`) forwards HTTP :80 to a Fargate task in the private subnet. The task runs a placeholder `nginx:latest` image on port 80 and reaches the internet via the existing NAT gateway. Two new Terraform modules (`alb`, `ecs`) mirror the existing `vpc` module's hardcoded-values style.

**Tech Stack:** Terraform (AWS provider ~> 5.0), ECS Fargate, Application Load Balancer, IAM task-execution role.

## Global Constraints

- Terraform provider `aws` `~> 5.0`, `required_version >= 1.3` (per root `main.tf`).
- Region `eu-central-1`, single AZ `eu-central-1a`.
- Do NOT modify or touch the existing `vpc` module.
- Resource `Name` tags follow existing style: `"${var.name}-<resource>"`. Provider-level `Project = "smort"` tag is applied automatically — do not duplicate it.
- Hardcode module input values in the root `main.tf` (existing repo style; no root variables).
- No ECR, no real image, no Dockerfile — use placeholder `nginx:latest`.
- Do NOT run `./mvnw compile`/`test` or `terraform apply`. Verification is `terraform fmt`, `terraform validate`, `terraform plan`.
- Write tests only if explicitly requested (none here).

---

### Task 1: Create the ALB module

**Files:**
- Create: `terraform/modules/alb/main.tf`
- Create: `terraform/modules/alb/outputs.tf`
- Create: `terraform/modules/alb/variables.tf`

**Interfaces:**
- Consumes: nothing from other modules.
- Produces: `aws_lb` (id, arn, security group, dns_name), `aws_lb_target_group` (arn), `aws_lb_listener` (arn), plus a `aws_security_group` for the ALB.
- Root `main.tf` (Task 3) passes `name`, `vpc_id`, `public_subnet_id`.
- `ecs` module (Task 2) consumes `alb_security_group_id` and `target_group_arn`.

- [ ] **Step 1: Create `terraform/modules/alb/variables.tf`**

```hcl
variable "name" {
  type        = string
  description = "Resource name prefix, used in Name tags."
}

variable "vpc_id" {
  type        = string
  description = "VPC the ALB is deployed into."
}

variable "public_subnet_id" {
  type        = string
  description = "Public subnet the ALB is deployed into."
}
```

- [ ] **Step 2: Create `terraform/modules/alb/main.tf`**

```hcl
resource "aws_security_group" "alb" {
  name   = "${var.name}-alb"
  vpc_id = var.vpc_id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.name}-alb" }
}

resource "aws_lb" "this" {
  name               = "${var.name}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = [var.public_subnet_id]

  tags = { Name = "${var.name}-alb" }
}

resource "aws_lb_target_group" "this" {
  name     = "${var.name}-tg"
  port     = 80
  protocol = "HTTP"
  vpc_id   = var.vpc_id

  target_type = "ip"

  health_check {
    path                = "/"
    protocol            = "HTTP"
    healthy_threshold   = 3
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 5
  }

  tags = { Name = "${var.name}-tg" }
}

resource "aws_lb_listener" "this" {
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.this.arn
  }
}
```

- [ ] **Step 3: Create `terraform/modules/alb/outputs.tf`**

```hcl
output "alb_id" {
  value = aws_lb.this.id
}

output "alb_arn" {
  value = aws_lb.this.arn
}

output "alb_security_group_id" {
  value = aws_security_group.alb.id
}

output "alb_dns_name" {
  value = aws_lb.this.dns_name
}

output "target_group_arn" {
  value = aws_lb_target_group.this.arn
}

output "listener_arn" {
  value = aws_lb_listener.this.arn
}
```

- [ ] **Step 4: Format and validate**

Run: `terraform fmt terraform/modules/alb/ && terraform validate`
Expected: `fmt` reports no changes; `validate` succeeds with "Success!".

- [ ] **Step 5: Commit**

```bash
git add terraform/modules/alb
git commit -m "chore(infra): add alb module"
```

---

### Task 2: Create the ECS module

**Files:**
- Create: `terraform/modules/ecs/main.tf`
- Create: `terraform/modules/ecs/outputs.tf`
- Create: `terraform/modules/ecs/variables.tf`

**Interfaces:**
- Consumes: `alb_security_group_id` and `target_group_arn` from the `alb` module (Task 1); `name`, `vpc_id`, `private_subnet_id` from root `main.tf` (Task 3).
- Produces: `cluster_name`, `service_name`, `task_definition_arn`.
- Root `outputs.tf` (Task 3) surfaces `cluster_name` and `service_name`.

- [ ] **Step 1: Create `terraform/modules/ecs/variables.tf`**

```hcl
variable "name" {
  type        = string
  description = "Resource name prefix, used in Name tags."
}

variable "vpc_id" {
  type        = string
  description = "VPC the ECS tasks run in."
}

variable "private_subnet_id" {
  type        = string
  description = "Private subnet the Fargate task runs in."
}

variable "alb_security_group_id" {
  type        = string
  description = "Security group of the ALB allowed to reach the tasks."
}

variable "target_group_arn" {
  type        = string
  description = "ALB target group the service registers with."
}

variable "container_image" {
  type        = string
  description = "Container image to run (placeholder until real image exists)."
  default     = "nginx:latest"
}

variable "container_port" {
  type        = number
  description = "Container port the task listens on."
  default     = 80
}
```

- [ ] **Step 2: Create `terraform/modules/ecs/main.tf`**

```hcl
resource "aws_ecs_cluster" "this" {
  name = var.name

  setting {
    name  = "containerInsights"
    value = "disabled"
  }
}

resource "aws_iam_role" "execution" {
  name = "${var.name}-ecs-execution"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_security_group" "task" {
  name   = "${var.name}-ecs-task"
  vpc_id = var.vpc_id

  ingress {
    from_port       = var.container_port
    to_port         = var.container_port
    protocol        = "tcp"
    security_groups = [var.alb_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.name}-ecs-task" }
}

resource "aws_ecs_task_definition" "this" {
  family                   = var.name
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = aws_iam_role.execution.arn

  container_definitions = jsonencode([
    {
      name      = var.name
      image     = var.container_image
      essential = true
      portMappings = [
        {
          containerPort = var.container_port
          protocol      = "tcp"
        }
      ]
    }
  ])

  tags = { Name = "${var.name}-task-definition" }
}

resource "aws_ecs_service" "this" {
  name            = var.name
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.this.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = [var.private_subnet_id]
    security_groups  = [aws_security_group.task.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = var.target_group_arn
    container_name   = var.name
    container_port   = var.container_port
  }

  depends_on = [aws_iam_role_policy_attachment.execution]
}
```

- [ ] **Step 3: Create `terraform/modules/ecs/outputs.tf`**

```hcl
output "cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "service_name" {
  value = aws_ecs_service.this.name
}

output "task_definition_arn" {
  value = aws_ecs_task_definition.this.arn
}
```

- [ ] **Step 4: Format and validate**

Run: `terraform fmt terraform/modules/ecs/ && terraform validate`
Expected: `fmt` reports no changes; `validate` succeeds.

- [ ] **Step 5: Commit**

```bash
git add terraform/modules/ecs
git commit -m "chore(infra): add ecs module"
```

---

### Task 3: Wire modules into the root Terraform config

**Files:**
- Modify: `terraform/main.tf`
- Modify: `terraform/outputs.tf`

**Interfaces:**
- Consumes: `module.vpc.public_subnet_id`, `module.vpc.private_subnet_id`, `module.vpc.vpc_id`; ALB module outputs `alb_security_group_id` and `target_group_arn`.
- Produces: `alb_dns_name`, `ecs_cluster_name`, `ecs_service_name` root outputs.

- [ ] **Step 1: Add the `alb` and `ecs` modules to `terraform/main.tf`**

Append after the existing `module "vpc"` block:

```hcl
module "alb" {
  source = "./modules/alb"

  name             = "smort"
  vpc_id           = module.vpc.vpc_id
  public_subnet_id = module.vpc.public_subnet_id
}

module "ecs" {
  source = "./modules/ecs"

  name                  = "smort"
  vpc_id                = module.vpc.vpc_id
  private_subnet_id     = module.vpc.private_subnet_id
  alb_security_group_id = module.alb.alb_security_group_id
  target_group_arn      = module.alb.target_group_arn
}
```

- [ ] **Step 2: Add root outputs to `terraform/outputs.tf`**

Append:

```hcl
output "alb_dns_name" {
  value = module.alb.alb_dns_name
}

output "ecs_cluster_name" {
  value = module.ecs.cluster_name
}

output "ecs_service_name" {
  value = module.ecs.service_name
}
```

- [ ] **Step 3: Format, init, and validate**

Run: `terraform fmt terraform/ && terraform init -backend=false && terraform validate`
Expected: `fmt` reports no changes; `init` succeeds; `validate` succeeds with "Success!".

- [ ] **Step 4: Plan to verify wiring**

Run: `terraform plan -out=/tmp/ecs-alb.plan`
Expected: plan shows only new resources (`aws_lb`, `aws_lb_target_group`, `aws_lb_listener`, `aws_ecs_cluster`, `aws_ecs_task_definition`, `aws_ecs_service`, IAM role/attachment, security groups). No changes to existing VPC resources.

- [ ] **Step 5: Commit**

```bash
git add terraform/main.tf terraform/outputs.tf
git commit -m "chore(infra): wire alb and ecs modules into root config"
```