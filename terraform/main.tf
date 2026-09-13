terraform {
  required_version = ">= 1.3"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
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