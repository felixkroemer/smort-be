# Config vars -> SSM Parameter Store (type String)
resource "aws_ssm_parameter" "base_data_dir" {
  name  = "/smort/BASE_DATA_DIR"
  type  = "String"
  value = var.base_data_dir
}

resource "aws_ssm_parameter" "analysis_db_directory_name" {
  name  = "/smort/ANALYSIS_DB_DIRECTORY_NAME"
  type  = "String"
  value = var.analysis_db_directory_name
}

resource "aws_ssm_parameter" "analysis_max_db_size" {
  name  = "/smort/ANALYSIS_MAX_DB_SIZE"
  type  = "String"
  value = var.analysis_max_db_size
}

resource "aws_ssm_parameter" "auth0_issuer_uri" {
  name  = "/smort/AUTH0_ISSUER_URI"
  type  = "String"
  value = var.auth0_issuer_uri
}

resource "aws_ssm_parameter" "smort_allowed_email" {
  name  = "/smort/SMORT_ALLOWED_EMAIL"
  type  = "String"
  value = var.smort_allowed_email
}

resource "aws_ssm_parameter" "openai_model" {
  name  = "/smort/OPENAI_MODEL"
  type  = "String"
  value = var.openai_model
}

# Secrets -> Secrets Manager
resource "aws_secretsmanager_secret" "openai_api_key" {
  name = "smort-openai_api_key"
}

resource "aws_secretsmanager_secret_version" "openai_api_key" {
  secret_id     = aws_secretsmanager_secret.openai_api_key.id
  secret_string = var.openai_api_key
}

resource "aws_secretsmanager_secret" "auth0_client_id" {
  name = "smort-auth0_client_id"
}

resource "aws_secretsmanager_secret_version" "auth0_client_id" {
  secret_id     = aws_secretsmanager_secret.auth0_client_id.id
  secret_string = var.auth0_client_id
}