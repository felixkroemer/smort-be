variable "name" {
  type        = string
  description = "ECR repository name."
}

variable "max_image_count" {
  type        = number
  description = "Maximum images to retain before the lifecycle policy expires older ones."
  default     = 5
}