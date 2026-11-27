# Runs against a mocked AWS provider: no credentials, no account, nothing created.
# The modules test their own properties; these check how prod wires them together.

# Fixed partition, region and account, so the ARNs the modules build are valid.
mock_provider "aws" {
  source = "../../testing/aws"
}

override_data {
  target = module.network.data.aws_availability_zones.available
  values = {
    names = ["us-east-1a", "us-east-1b", "us-east-1c"]
  }
}

# RDS creates the master secret because the module asks it to; the mock does not.
override_resource {
  target = module.database.aws_db_instance.this
  values = {
    master_user_secret = [{
      secret_arn = "arn:aws:secretsmanager:us-east-1:111111111111:secret:rds!db-test"
    }]
  }
}

run "resources_are_named_after_the_project_and_env" {
  command = plan

  variables {
    project = "demo"
  }

  assert {
    condition     = local.name == "demo-prod"
    error_message = "Every module must be named <project>-prod."
  }
}

# Applied against the mock, because subnet IDs are unknown in a plan.
run "the_database_is_in_the_private_subnets" {
  command = apply

  assert {
    condition     = module.database.subnet_ids == toset(module.network.private_subnet_ids)
    error_message = "The database must be placed in the network's private subnets, never the public ones."
  }
}

# Applied against the mock, because subnet IDs are unknown in a plan. The module's own tests check
# that the database admits the backend.
run "the_backend_is_in_the_public_subnets" {
  command = apply

  assert {
    condition     = module.backend.subnet_ids == toset(module.network.public_subnet_ids)
    error_message = "The backend must run in the network's public subnets, the only ones with a route to CloudFront and AWS's APIs."
  }
}

run "an_invalid_project_name_is_rejected" {
  command = plan

  variables {
    project = "Itacatl_Prod"
  }

  expect_failures = [var.project]
}
