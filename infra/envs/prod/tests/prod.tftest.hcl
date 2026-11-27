# Runs against a mocked AWS provider: no credentials, no account, nothing created.
# The modules test their own properties; these check how prod wires them together.

mock_provider "aws" {}

override_data {
  target = module.network.data.aws_availability_zones.available
  values = {
    names = ["us-east-1a", "us-east-1b", "us-east-1c"]
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

run "an_invalid_project_name_is_rejected" {
  command = plan

  variables {
    project = "Itacatl_Prod"
  }

  expect_failures = [var.project]
}
