variable "secret_env_names" {
  type        = list(string)
  description = "Keys from .env stored in Secrets Manager instead of SSM."
  default     = ["OPENAI_API_KEY", "AUTH0_CLIENT_ID"]
}

locals {
  env_lines = [
    for line in split("\n", file("${path.module}/../.env")) :
    trimspace(line)
    if trimspace(line) != "" && !startswith(trimspace(line), "#")
  ]

  env_vars = {
    for line in local.env_lines :
    regex("^([A-Za-z_][A-Za-z0-9_]*)=(.*)$", line)[0] => regex("^([A-Za-z_][A-Za-z0-9_]*)=(.*)$", line)[1]
  }

  secret_env_vars = { for k, v in local.env_vars : k => v if contains(var.secret_env_names, k) }
  config_env_vars = { for k, v in local.env_vars : k => v if !contains(var.secret_env_names, k) }
}

resource "aws_ssm_parameter" "app" {
  for_each = local.config_env_vars

  name  = "/smort/${each.key}"
  type  = "String"
  value = each.value
}

resource "aws_secretsmanager_secret" "app" {
  for_each = local.secret_env_vars

  name = "smort-${lower(each.key)}"
}

resource "aws_secretsmanager_secret_version" "app" {
  for_each = local.secret_env_vars

  secret_id     = aws_secretsmanager_secret.app[each.key].id
  secret_string = each.value
}

locals {
  secret_arns = merge(
    { for k, p in aws_ssm_parameter.app : k => p.arn },
    { for k, s in aws_secretsmanager_secret.app : k => s.arn },
  )
}
