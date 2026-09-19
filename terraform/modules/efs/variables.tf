variable "name" {
  type        = string
  description = "Resource name prefix, used in Name tags."
}

variable "vpc_id" {
  type        = string
  description = "VPC the EFS mount targets are deployed into."
}

variable "private_subnet_ids" {
  type        = list(string)
  description = "Private subnets to place EFS mount targets in."
}

variable "ecs_security_group_id" {
  type        = string
  description = "Security group of the ECS tasks allowed to mount the filesystem."
}