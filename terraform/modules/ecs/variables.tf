variable "name" {
  type        = string
  description = "Resource name prefix, used in Name tags."
}

variable "region" {
  type        = string
  description = "AWS region for the awslogs driver."
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

variable "base_data_dir_arn" {
  type        = string
  description = "SSM/Secrets ARN for BASE_DATA_DIR."
}

variable "analysis_db_directory_name_arn" {
  type        = string
  description = "SSM/Secrets ARN for ANALYSIS_DB_DIRECTORY_NAME."
}

variable "analysis_max_db_size_arn" {
  type        = string
  description = "SSM/Secrets ARN for ANALYSIS_MAX_DB_SIZE."
}

variable "auth0_issuer_uri_arn" {
  type        = string
  description = "SSM/Secrets ARN for AUTH0_ISSUER_URI."
}

variable "smort_allowed_email_arn" {
  type        = string
  description = "SSM/Secrets ARN for SMORT_ALLOWED_EMAIL."
}

variable "openai_model_arn" {
  type        = string
  description = "SSM/Secrets ARN for OPENAI_MODEL."
}

variable "openai_api_key_arn" {
  type        = string
  description = "Secrets ARN for OPENAI_API_KEY."
}

variable "auth0_client_id_arn" {
  type        = string
  description = "Secrets ARN for AUTH0_CLIENT_ID."
}

variable "dynamodb_table_arn" {
  type        = string
  description = "ARN of the DynamoDB table the task role is allowed to access."
}

variable "efs_file_system_id" {
  type        = string
  description = "EFS filesystem ID for persistent analysis DBs."
}

variable "efs_access_point_id" {
  type        = string
  description = "EFS access point ID for persistent analysis DBs."
}

variable "data_dir_path" {
  type        = string
  description = "Container path (BASE_DATA_DIR) to mount the EFS volume at."
}
