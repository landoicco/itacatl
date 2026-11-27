# Runs against a mocked AWS provider: no credentials, no account, nothing created.

# Fixed partition, region and account, so the ARNs the module builds are valid.
mock_provider "aws" {
  source = "../../testing/aws"
}

variables {
  name                            = "itacatl-test"
  vpc_id                          = "vpc-0123456789abcdef0"
  subnet_ids                      = ["subnet-0123456789abcdef0", "subnet-0123456789abcdef1"]
  database_security_group_id      = "sg-0123456789abcdef0"
  database_address                = "itacatl-test.abc123.us-east-1.rds.amazonaws.com"
  database_name                   = "itacatl"
  database_resource_id            = "db-ABCDEFGHIJKLMNOPQRSTUVWXYZ"
  database_master_user_secret_arn = "arn:aws:secretsmanager:us-east-1:111111111111:secret:rds!db-test"
}

run "one_spot_instance_and_never_on_demand" {
  command = plan

  assert {
    condition = (
      aws_autoscaling_group.this.min_size == 1 &&
      aws_autoscaling_group.this.max_size == 1 &&
      aws_autoscaling_group.this.desired_capacity == 1
    )
    error_message = "The group must run exactly one instance."
  }

  assert {
    condition = (
      aws_autoscaling_group.this.mixed_instances_policy[0].instances_distribution[0].on_demand_base_capacity == 0 &&
      aws_autoscaling_group.this.mixed_instances_policy[0].instances_distribution[0].on_demand_percentage_above_base_capacity == 0
    )
    error_message = "The instance must be spot only, with no on-demand capacity."
  }

  assert {
    condition     = aws_autoscaling_group.this.mixed_instances_policy[0].instances_distribution[0].spot_allocation_strategy == "price-capacity-optimized"
    error_message = "Spot capacity must be chosen by price and capacity."
  }

  assert {
    condition     = aws_autoscaling_group.this.capacity_rebalance
    error_message = "Capacity rebalancing must be on, so a replacement starts before the instance is reclaimed."
  }
}

run "a_new_instance_serves_only_once_the_app_is_ready" {
  command = plan

  assert {
    condition = anytrue([
      for hook in aws_autoscaling_group.this.initial_lifecycle_hook :
      hook.lifecycle_transition == "autoscaling:EC2_INSTANCE_LAUNCHING" && hook.default_result == "ABANDON"
    ])
    error_message = "A launch hook must hold each new instance until it reports ready, and abandon it otherwise."
  }
}

run "the_instance_can_be_any_of_the_types_in_any_of_the_zones" {
  command = plan

  assert {
    condition     = toset([for override in aws_autoscaling_group.this.mixed_instances_policy[0].launch_template[0].override : override.instance_type]) == toset(var.instance_types)
    error_message = "Every instance type given must be offered to the group."
  }

  assert {
    condition     = aws_autoscaling_group.this.vpc_zone_identifier == toset(var.subnet_ids)
    error_message = "The group must launch only in the subnets it is given, the public ones."
  }
}

run "the_instance_is_hardened" {
  command = plan

  assert {
    condition     = aws_launch_template.this.metadata_options[0].http_tokens == "required"
    error_message = "The instance metadata service must require tokens (IMDSv2)."
  }

  assert {
    condition     = aws_launch_template.this.metadata_options[0].http_put_response_hop_limit == 2
    error_message = "The container is one hop away from the instance: the hop limit must be 2."
  }

  assert {
    condition     = aws_launch_template.this.block_device_mappings[0].ebs[0].encrypted == "true"
    error_message = "The root volume must be encrypted."
  }

  assert {
    condition     = startswith(aws_launch_template.this.image_id, "resolve:ssm:/aws/service/ami-amazon-linux-latest/") && endswith(aws_launch_template.this.image_id, "-arm64")
    error_message = "The instance must boot the latest Amazon Linux AMI for arm64, resolved at launch."
  }

  assert {
    condition     = aws_launch_template.this.credit_specification[0].cpu_credits == "standard"
    error_message = "T instances must run in standard mode, which never bills extra CPU."
  }
}

run "only_cloudfront_reaches_the_instance" {
  command = plan

  assert {
    condition = (
      aws_vpc_security_group_ingress_rule.from_cloudfront.prefix_list_id == data.aws_ec2_managed_prefix_list.cloudfront.id &&
      aws_vpc_security_group_ingress_rule.from_cloudfront.cidr_ipv4 == null &&
      aws_vpc_security_group_ingress_rule.from_cloudfront.from_port == 8080 &&
      aws_vpc_security_group_ingress_rule.from_cloudfront.to_port == 8080
    )
    error_message = "The instance must admit only CloudFront's addresses, on the app's port."
  }
}

# Applied against the mock, because the security groups' IDs are unknown in a plan.
run "the_backend_and_only_it_reaches_the_database" {
  command = apply

  assert {
    condition = (
      aws_vpc_security_group_ingress_rule.database_from_backend.security_group_id == var.database_security_group_id &&
      aws_vpc_security_group_ingress_rule.database_from_backend.referenced_security_group_id == aws_security_group.this.id &&
      aws_vpc_security_group_ingress_rule.database_from_backend.from_port == 5432
    )
    error_message = "The database must admit the backend's security group on 5432."
  }

  assert {
    condition = (
      aws_vpc_security_group_egress_rule.to_database.referenced_security_group_id == var.database_security_group_id &&
      aws_vpc_security_group_egress_rule.https.from_port == 443 &&
      aws_vpc_security_group_egress_rule.https.to_port == 443
    )
    error_message = "The instance may only start connections to the database and to HTTPS."
  }
}

# Applied against the mock, because the policy names resources whose ARNs are unknown in a plan.
run "each_permission_names_its_resource" {
  command = apply

  assert {
    condition = alltrue([
      for statement in jsondecode(aws_iam_role_policy.this.policy).Statement :
      statement.Resource != "*" if statement.Sid != "EcrSignIn"
    ])
    error_message = "Only ECR's sign-in may apply to every resource."
  }

  assert {
    condition = anytrue([
      for statement in jsondecode(aws_iam_role_policy.this.policy).Statement :
      statement.Action == "rds-db:connect" && try(endswith(statement.Resource, ":dbuser:${var.database_resource_id}/itacatl_app"), false)
    ])
    error_message = "The instance must sign in to the database as itacatl_app only."
  }

  assert {
    condition = anytrue([
      for statement in jsondecode(aws_iam_role_policy.this.policy).Statement :
      statement.Action == "secretsmanager:GetSecretValue" && try(statement.Resource == var.database_master_user_secret_arn, false)
    ])
    error_message = "The instance must read only the database's master secret."
  }
}

# Applied against the mock, because the user data names resources that are unknown in a plan.
run "the_instance_runs_the_app_with_the_aws_profile" {
  command = apply

  assert {
    condition = alltrue([
      for expected in [
        "SPRING_PROFILES_ACTIVE=aws",
        "DB_HOST=\"${var.database_address}\"",
        "DB_MASTER_SECRET_ARN=\"${var.database_master_user_secret_arn}\"",
        "--allocation-id \"${aws_eip.this.allocation_id}\"",
        "--auto-scaling-group-name \"${aws_autoscaling_group.this.name}\"",
      ] : strcontains(base64decode(aws_launch_template.this.user_data), expected)
    ])
    error_message = "The user data must run the image with the aws profile and the database's details, take the Elastic IP, and report to this group's launch hook."
  }
}

run "images_are_immutable_and_scanned" {
  command = plan

  assert {
    condition     = aws_ecr_repository.this.image_tag_mutability == "IMMUTABLE"
    error_message = "A tag must always name the same image."
  }

  assert {
    condition     = aws_ecr_repository.this.image_scanning_configuration[0].scan_on_push
    error_message = "Images must be scanned when pushed."
  }

  assert {
    condition = anytrue([
      for rule in jsondecode(aws_ecr_lifecycle_policy.this.policy).rules :
      rule.selection.tagStatus == "tagged" && rule.selection.countType == "imageCountMoreThan" && rule.selection.countNumber == 10
    ])
    error_message = "ECR must keep the last ten tagged images, counted apart from untagged ones."
  }

  assert {
    condition = alltrue([
      for rule in jsondecode(aws_ecr_lifecycle_policy.this.policy).rules :
      rule.selection.tagStatus != "any"
    ])
    error_message = "No rule may count tagged and untagged images together: leftovers would push deployable tags out."
  }
}

run "logs_are_kept_two_weeks" {
  command = plan

  assert {
    condition     = aws_cloudwatch_log_group.this.retention_in_days == 14
    error_message = "Logs must be kept for 14 days, then deleted."
  }
}

run "one_subnet_is_rejected" {
  command = plan

  variables {
    subnet_ids = ["subnet-0123456789abcdef0"]
  }

  expect_failures = [var.subnet_ids]
}

run "an_x86_instance_type_is_rejected" {
  command = plan

  variables {
    instance_types = ["t4g.small", "t3.small"]
  }

  expect_failures = [var.instance_types]
}

run "a_single_instance_type_is_rejected" {
  command = plan

  variables {
    instance_types = ["t4g.small"]
  }

  expect_failures = [var.instance_types]
}
