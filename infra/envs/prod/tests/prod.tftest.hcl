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

variables {
  alarm_email = "alerts@example.com"
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

# Applied against the mock, because the Elastic IP's DNS name is unknown in a plan.
run "cloudfront_sends_the_api_to_the_backends_address" {
  command = apply

  assert {
    condition = (
      module.frontend.api_origin_domain_name == module.backend.origin_domain_name &&
      module.frontend.api_origin_port == module.backend.origin_port
    )
    error_message = "CloudFront's /api origin must be the backend's Elastic IP, on the port the backend answers on."
  }

  assert {
    condition     = module.frontend.api_origin_secret == module.backend.origin_secret
    error_message = "CloudFront must send the backend the secret the backend checks."
  }

  assert {
    condition     = module.frontend.url == "https://${module.frontend.distribution_domain_name}"
    error_message = "Without a domain, prod must be served on CloudFront's own."
  }
}

# Applied against the mock, because the distribution's ID is unknown in a plan.
run "the_alarms_watch_this_environments_resources" {
  command = apply

  assert {
    condition = (
      length(module.observability.alarms) > 0 &&
      alltrue([for name, _ in module.observability.alarms : startswith(name, "${local.name}-")])
    )
    error_message = "The alarms must be named after the environment."
  }

  assert {
    condition = alltrue([
      for alarm in module.observability.alarms :
      alarm.namespace == module.backend.metrics_namespace ||
      try(alarm.dimensions.AutoScalingGroupName == module.backend.autoscaling_group_name, false) ||
      try(alarm.dimensions.DBInstanceIdentifier == module.database.identifier, false) ||
      try(alarm.dimensions.DistributionId == module.frontend.distribution_id, false)
    ])
    error_message = "Every alarm must watch this environment's backend group or instance, database or distribution."
  }
}

run "an_invalid_project_name_is_rejected" {
  command = plan

  variables {
    project = "Itacatl_Prod"
  }

  expect_failures = [var.project]
}
