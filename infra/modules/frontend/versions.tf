terraform {
  required_version = "~> 1.17"

  # A module sets only a lower bound; the stack that uses it pins the exact version in its lock.
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 6.73"
    }
  }
}
