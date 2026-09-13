# Terraform `vpc` Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a reusable `modules/vpc` Terraform submodule (VPC, public/private subnets, IGW, NAT gateway, routing, and a DynamoDB gateway endpoint) instantiated from a single shared `infra/` root caller, with local state.

**Architecture:** A single-AZ VPC (`10.0.0.0/16`) split into a public subnet (`10.0.0.0/24`) hosting the IGW route and a NAT gateway (with Elastic IP), and a private subnet (`10.0.1.0/24`) for future app instances. The private route table routes `0.0.0.0/0` through the NAT and includes a DynamoDB gateway endpoint so DynamoDB traffic stays inside the AWS network.

**Tech Stack:** Terraform (HashiCorp AWS provider), AWS (eu-central-1).

## Global Constraints

- Region: `eu-central-1` (Frankfurt).
- VPC CIDR: `10.0.0.0/16`; public subnet `10.0.0.0/24`; private subnet `10.0.1.0/24`.
- Single AZ — both subnets in the same `availability_zone` (input `az`).
- Module named `vpc`; reusable submodule at `modules/vpc`, single shared root caller at `infra/` (future modules added as `module "..."` blocks in `infra/main.tf`).
- State is local (`terraform.tfstate` in `infra/`).
- **No security groups** in this module (YAGNI); the follow-on compute module owns instance security groups.
- AWS provider version `~> 5.0`.
- **Terraform is NOT installed in the dev environment.** The implementing agent must NOT attempt to install or run Terraform (no `terraform apply`, `plan`, or `validate`). The human owns verification. Include the exact commands in each task for the human to run.

---

### Task 1: Scaffold `modules/vpc` (variables, VPC, subnets, route tables, outputs)

**Files:**
- Create: `modules/vpc/variables.tf`
- Create: `modules/vpc/main.tf`
- Create: `modules/vpc/outputs.tf`

**Interfaces:**
- Produces module inputs: `name`, `region`, `az`, `vpc_cidr`, `public_cidr`, `private_cidr`, `tags`.
- Produces outputs: `vpc_id`, `public_subnet_id`, `private_subnet_id`, `public_route_table_id`, `private_route_table_id`, `nat_gateway_id`.

- [ ] **Step 1: Create `modules/vpc/variables.tf`**

```hcl
variable "name" {
  type        = string
  description = "Resource name prefix/tag."
}

variable "region" {
  type        = string
  description = "AWS region; used to build the DynamoDB gateway endpoint."
}

variable "az" {
  type        = string
  description = "Availability zone for the single-AZ subnets."
}

variable "vpc_cidr" {
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_cidr" {
  type        = string
  default     = "10.0.0.0/24"
}

variable "private_cidr" {
  type        = string
  default     = "10.0.1.0/24"
}

variable "tags" {
  type        = map(string)
  default     = {}
}
```

- [ ] **Step 2: Create `modules/vpc/main.tf` — VPC, subnets, route tables**

```hcl
resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = merge(var.tags, { Name = var.name })
}

resource "aws_subnet" "public" {
  vpc_id            = aws_vpc.this.id
  cidr_block        = var.public_cidr
  availability_zone = var.az

  tags = merge(var.tags, { Name = "${var.name}-public" })
}

resource "aws_subnet" "private" {
  vpc_id            = aws_vpc.this.id
  cidr_block        = var.private_cidr
  availability_zone = var.az

  tags = merge(var.tags, { Name = "${var.name}-private" })
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, { Name = "${var.name}-public" })
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, { Name = "${var.name}-private" })
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "private" {
  subnet_id      = aws_subnet.private.id
  route_table_id = aws_route_table.private.id
}
```

- [ ] **Step 3: Create `modules/vpc/outputs.tf`**

```hcl
output "vpc_id" {
  value = aws_vpc.this.id
}

output "public_subnet_id" {
  value = aws_subnet.public.id
}

output "private_subnet_id" {
  value = aws_subnet.private.id
}

output "public_route_table_id" {
  value = aws_route_table.public.id
}

output "private_route_table_id" {
  value = aws_route_table.private.id
}
```

- [ ] **Step 4: Format check**

Run: `terraform fmt -check -recursive modules/vpc`
Expected: PASS (no diff) once formatted. If it reports diffs, run `terraform fmt modules/vpc` and re-check.

- [ ] **Step 5: Commit**

```bash
git add modules/vpc/variables.tf modules/vpc/main.tf modules/vpc/outputs.tf
git commit -m "feat(vpc): scaffold module with VPC, subnets, route tables"
```

---

### Task 2: Add IGW and NAT gateway routing

**Files:**
- Modify: `modules/vpc/main.tf`

**Interfaces:**
- Consumes: `aws_vpc.this.id`, `aws_subnet.public.id`, `aws_route_table.public`, `aws_route_table.private`.
- Produces: `aws_nat_gateway.this` (used by the `nat_gateway_id` output).

- [ ] **Step 1: Append internet gateway, NAT, and routes to `modules/vpc/main.tf`**

```hcl
resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, { Name = var.name })
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}

resource "aws_eip" "nat" {
  domain = "vpc"

  tags = merge(var.tags, { Name = "${var.name}-nat" })
}

resource "aws_nat_gateway" "this" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public.id

  tags = merge(var.tags, { Name = "${var.name}-nat" })
}

resource "aws_route" "private_internet" {
  route_table_id         = aws_route_table.private.id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.this.id
}
```

- [ ] **Step 2: Add the `nat_gateway_id` output to `modules/vpc/outputs.tf`**

```hcl
output "nat_gateway_id" {
  value = aws_nat_gateway.this.id
}
```

- [ ] **Step 3: Format check**

Run: `terraform fmt -check -recursive modules/vpc`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add modules/vpc/main.tf modules/vpc/outputs.tf
git commit -m "feat(vpc): add internet gateway and NAT routing"
```

---

### Task 3: Add DynamoDB gateway endpoint

**Files:**
- Modify: `modules/vpc/main.tf`

**Interfaces:**
- Consumes: `aws_vpc.this.id`, `aws_route_table.private.id`.
- Produces: `aws_vpc_endpoint.dynamodb` scoped to the private route table.

- [ ] **Step 1: Append the DynamoDB gateway endpoint to `modules/vpc/main.tf`**

```hcl
data "aws_vpc_endpoint_service" "dynamodb" {
  service      = "dynamodb"
  service_type = "Gateway"
}

resource "aws_vpc_endpoint" "dynamodb" {
  vpc_id            = aws_vpc.this.id
  service_name      = data.aws_vpc_endpoint_service.dynamodb.service_name
  vpc_endpoint_type = "Gateway"

  tags = merge(var.tags, { Name = "${var.name}-dynamodb" })
}

resource "aws_vpc_endpoint_route_table_association" "dynamodb" {
  route_table_id  = aws_route_table.private.id
  vpc_endpoint_id = aws_vpc_endpoint.dynamodb.id
}
```

- [ ] **Step 2: Format check**

Run: `terraform fmt -check -recursive modules/vpc`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add modules/vpc/main.tf
git commit -m "feat(vpc): add DynamoDB gateway endpoint on private route table"
```

---

### Task 4: Root caller `infra/` and provider setup

**Files:**
- Create: `infra/main.tf`
- Create: `infra/variables.tf`
- Create: `infra/outputs.tf`
- Create: `infra/.gitignore`

**Interfaces:**
- Consumes: `modules/vpc` outputs (`vpc_id`, `public_subnet_id`, `private_subnet_id`, `public_route_table_id`, `private_route_table_id`, `nat_gateway_id`).
- Produces: root-level outputs mirroring the module outputs for downstream modules. Future modules (compute, DynamoDB tables) are added as additional `module "..."` blocks in `infra/main.tf`.

- [ ] **Step 1: Create `infra/.gitignore`**

```
.terraform/
*.tfstate
*.tfstate.backup
*.tfstate.lock.info
```

- [ ] **Step 2: Create `infra/variables.tf`**

```hcl
variable "name" {
  type    = string
  default = "smort"
}

variable "region" {
  type    = string
  default = "eu-central-1"
}

variable "az" {
  type    = string
  default = "eu-central-1a"
}
```

- [ ] **Step 3: Create `infra/main.tf`**

```hcl
terraform {
  required_version = ">= 1.3"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.region
}

module "vpc" {
  source = "../modules/vpc"

  name   = var.name
  region = var.region
  az     = var.az
}
```

- [ ] **Step 4: Create `infra/outputs.tf`**

```hcl
output "vpc_id" {
  value = module.vpc.vpc_id
}

output "public_subnet_id" {
  value = module.vpc.public_subnet_id
}

output "private_subnet_id" {
  value = module.vpc.private_subnet_id
}

output "public_route_table_id" {
  value = module.vpc.public_route_table_id
}

output "private_route_table_id" {
  value = module.vpc.private_route_table_id
}

output "nat_gateway_id" {
  value = module.vpc.nat_gateway_id
}
```

- [ ] **Step 5: Format check**

Run: `terraform fmt -check -recursive infra`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add infra/
git commit -m "feat(vpc): add root caller with provider and outputs"
```

---

### Task 5: Full verification (human-run)

**Files:** none (verification only).

- [ ] **Step 1: Init, format, and validate**

Run (from `infra/`):
```bash
terraform init
terraform fmt -check -recursive ./
terraform validate
```
Expected: init downloads the AWS provider; fmt passes; validate reports `Success! The configuration is valid.`

- [ ] **Step 2: Review the planned changes (does NOT apply)**

Run (from `infra/`, requires AWS credentials):
```bash
terraform plan
```
Expected: plan shows creation of the VPC, subnets, route tables/associations, internet gateway, EIP, NAT gateway, routes, and the DynamoDB gateway endpoint + association. No `terraform apply`.

- [ ] **Step 3: Commit any format adjustments**

If `terraform fmt` reformatted any file in step 1, commit the formatting:
```bash
git add -A
git commit -m "chore(vpc): terraform fmt"
```
If nothing changed, skip this commit.