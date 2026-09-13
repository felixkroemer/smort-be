variable "image_tag" {
  type        = string
  description = "Image tag to run from the ECR repository."
}

variable "base_data_dir" {
  type        = string
  description = "Base data dir for the smort app."
}

variable "analysis_db_directory_name" {
  type        = string
  description = "Directory name of the analysis DB under the base data dir."
}

variable "analysis_max_db_size" {
  type        = string
  description = "Max analysis DB size in bytes."
  default     = "52428800"
}

variable "auth0_issuer_uri" {
  type        = string
  description = "Auth0 issuer URI for JWT verification."
}

variable "smort_allowed_email" {
  type        = string
  description = "Email allowed to use the app."
}

variable "openai_model" {
  type        = string
  description = "OpenAI model to use."
  default     = "gpt-4o"
}

variable "openai_api_key" {
  type        = string
  description = "OpenAI API key."
  sensitive   = true
}

variable "auth0_client_id" {
  type        = string
  description = "Auth0 client ID (JWT audience)."
  sensitive   = true
}