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