# Design: Terraform `vpc` module

Date: 2026-09-13
Status: Approved

## Purpose

Provide the AWS networking foundation (VPC, subnets, routing, NAT, and a
DynamoDB gateway endpoint) for the SMORT backend. This is the first of several
Terraform modules; the VPC is a prerequisite for later compute, storage, and
other modules.

## Context

SMORT is a Spring Boot backend that:
- Uses DynamoDB (via `dynamodb-enhanced`).
- Validates JWTs against Auth0 (`spring.security.oauth2.resourceserver.jwt`).
- Calls the OpenAI API.

Application instances therefore need both internet egress (Auth0 JWKS + OpenAI)
and DynamoDB access. The design places instances in a **private subnet** with
egress via a NAT gateway, and uses a **VPC gateway endpoint for DynamoDB** so
DynamoDB traffic stays inside the AWS network instead of traversing the NAT/IGW.

## Decisions

| Decision | Choice |
|----------|--------|
| Availability | Single AZ (cheapest), suitable for a small single-user app |
| State backend | Local state (`terraform.tfstate` in the root caller dir) |
| Module structure | Reusable `modules/vpc` submodule + single shared `infra/` root caller |
| Module name | `vpc` |
| Region | `eu-central-1` (Frankfurt) |
| VPC CIDR | `10.0.0.0/16` |
| Security groups | None in this module (YAGNI); follow-on compute module owns instance SG |

## Architecture

```
              IGW
               │  0.0.0.0/0
   PUBLIC  [10.0.0.0/24] ── NAT (EIP) ──┐ 0.0.0.0/0
              ▲                          │
   PRIVATE [10.0.1.0/24] ────────────────┘
              │  DynamoDB gateway endpoint (dynamodb.eu-central-1)
        DynamoDB (no NAT/IGW traversal)
```

- **Public subnet** `10.0.0.0/24`: internet-facing route table → IGW; hosts the
  NAT gateway (with an Elastic IP).
- **Private subnet** `10.0.1.0/24`: future app instances; route table → NAT for
  egress, plus the DynamoDB gateway endpoint.

## Resources (in `modules/vpc`)

- `aws_vpc` — CIDR `10.0.0.0/16`, `enable_dns_hostnames` and `enable_dns_support`
  true, tagged with `name`.
- `aws_subnet.public` (`10.0.0.0/24`) and `aws_subnet.private` (`10.0.1.0/24`).
- `aws_internet_gateway` + `aws_route` (public route table → IGW, `0.0.0.0/0`).
- `aws_eip` (for NAT) + `aws_nat_gateway` (in public subnet, attached to public
  route table) + `aws_route` (private route table → NAT, `0.0.0.0/0`).
- `aws_vpc_endpoint` for **DynamoDB** (Gateway type, service
  `com.amazonaws.eu-central-1.dynamodb`, attached to private route table) via
  `aws_vpc_endpoint_route_table_association`.
- Two route tables: public (→ IGW) and private (→ NAT + DynamoDB endpoint).

## Security

- No security groups in this module. The follow-on compute module will define a
  restrictive instance security group (deny-all inbound by default, minimal
  outbound).
- The VPC default security group is left untouched.
- The DynamoDB gateway endpoint is scoped to the private subnet via route-table
  association, so only private-subnet traffic uses it.

## Module interface

### `modules/vpc` inputs
- `name` (string) — resource name prefix/tag.
- `region` (string) — AWS region.
- `az` (string) — availability zone for the single-AZ public and private subnets.
- `vpc_cidr` (string, default `10.0.0.0/16`).
- `public_cidr` (string, default `10.0.0.0/24`).
- `private_cidr` (string, default `10.0.1.0/24`).
- `tags` (map(string), optional) — additional tags.

### `modules/vpc` outputs
- `vpc_id`
- `public_subnet_id`
- `private_subnet_id`
- `public_route_table_id`
- `private_route_table_id`
- `nat_gateway_id`

## Root caller (`infra/`)

- A single shared root that instantiates all modules, giving one state file
  and letting module references resolve in one graph.
- Local state: `terraform.tfstate` in `infra/`.
- AWS provider pinned to `eu-central-1`.
- Invokes `modules/vpc` with the default CIDRs and a name like `smort`.
- Follow-on modules (compute, DynamoDB tables, etc.) are added as additional
  `module "..."` blocks in `infra/main.tf`.

## File layout

```
infra/
├── main.tf                   # root caller: module "vpc" { ... }, future modules added here
├── variables.tf
├── outputs.tf
├── terraform.tfstate         # local state
└── modules/
    └── vpc/
        ├── main.tf
        ├── variables.tf
        └── outputs.tf
```

## Verification

- `terraform fmt` — formatting.
- `terraform validate` — configuration validity.
- `terraform plan` — confirm resource graph without applying.
- No `terraform apply` in this task; application requires AWS credentials and
  creates real, billed resources. Compilation/build of the Java app is out of
  scope for infra work.