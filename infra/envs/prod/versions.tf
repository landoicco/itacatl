terraform {
  required_version = "~> 1.17"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.73"
    }
  }

  # The bucket's name contains the account ID, so it and the region come from backend.hcl
  # (git-ignored): terraform init -backend-config=backend.hcl. CI runs init -backend=false.
  backend "s3" {
    key          = "prod/terraform.tfstate"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = var.project
      Env       = "prod"
      ManagedBy = "terraform"
    }
  }
}
