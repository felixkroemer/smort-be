terraform {
  required_version = ">= 1.3"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }

  backend "s3" {
    bucket = "smort-terraform-state"
    key    = "terraform.tfstate"
    region = "eu-central-1"
  }
}

provider "aws" {
  region = "eu-central-1"

  default_tags {
    tags = {
      Project = "smort"
    }
  }
}

module "vpc" {
  source = "./modules/vpc"

  name = "smort"
  azs  = ["eu-central-1a", "eu-central-1b"]
}

module "alb" {
  source = "./modules/alb"

  name              = "smort"
  vpc_id            = module.vpc.vpc_id
  public_subnet_ids = module.vpc.public_subnet_ids
}

module "ecr" {
  source = "./modules/ecr"

  name = "smort"
}

module "dynamodb" {
  source = "./modules/dynamodb"

  table_name = "common-table"
}

module "ecs" {
  source = "./modules/ecs"

  name                           = "smort"
  region                         = "eu-central-1"
  vpc_id                         = module.vpc.vpc_id
  private_subnet_ids             = module.vpc.private_subnet_ids
  alb_security_group_id          = module.alb.alb_security_group_id
  target_group_arn               = module.alb.target_group_arn
  container_image                = "${module.ecr.repository_url}:${var.image_tag}"
  base_data_dir_arn              = aws_ssm_parameter.base_data_dir.arn
  analysis_db_directory_name_arn = aws_ssm_parameter.analysis_db_directory_name.arn
  analysis_max_db_size_arn       = aws_ssm_parameter.analysis_max_db_size.arn
  auth0_issuer_uri_arn           = aws_ssm_parameter.auth0_issuer_uri.arn
  smort_allowed_email_arn        = aws_ssm_parameter.smort_allowed_email.arn
  openai_model_arn               = aws_ssm_parameter.openai_model.arn
  openai_api_key_arn             = aws_secretsmanager_secret.openai_api_key.arn
  auth0_client_id_arn            = aws_secretsmanager_secret.auth0_client_id.arn
  dynamodb_table_arn             = module.dynamodb.table_arn
  data_dir_path                  = aws_ssm_parameter.base_data_dir.value
}
