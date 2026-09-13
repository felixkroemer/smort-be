variable "name" {
  type        = string
  description = "Resource name prefix, used in Name tags."
}

variable "vpc_id" {
  type        = string
  description = "VPC the ALB is deployed into."
}

variable "public_subnet_ids" {
  type        = list(string)
  description = "Public subnets the ALB is deployed into."
}