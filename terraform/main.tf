terraform {
  required_version = ">= 1.3"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
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
  az   = "eu-central-1a"
}

module "alb" {
  source = "./modules/alb"

  name             = "smort"
  vpc_id           = module.vpc.vpc_id
  public_subnet_id = module.vpc.public_subnet_id
}

module "ecs" {
  source = "./modules/ecs"

  name                  = "smort"
  vpc_id                = module.vpc.vpc_id
  private_subnet_id     = module.vpc.private_subnet_id
  alb_security_group_id = module.alb.alb_security_group_id
  target_group_arn      = module.alb.target_group_arn
}