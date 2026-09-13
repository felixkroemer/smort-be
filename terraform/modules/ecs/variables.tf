variable "name" {
  type        = string
  description = "Resource name prefix, used in Name tags."
}

variable "vpc_id" {
  type        = string
  description = "VPC the ECS tasks run in."
}

variable "private_subnet_ids" {
  type        = list(string)
  description = "Private subnets the Fargate task runs in."
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
  default     = 8080
}

variable "secret_arns" {
  type        = map(string)
  description = "Map of environment variable name to SSM/Secrets Manager ARN injected into the container."
  default     = {}
}