# Runs against a mocked AWS provider: no credentials, no account, nothing created. The random
# provider is the real one: it only generates the snapshot suffix, locally.

mock_provider "aws" {}

variables {
  name       = "itacatl-test"
  vpc_id     = "vpc-0123456789abcdef0"
  subnet_ids = ["subnet-0123456789abcdef0", "subnet-0123456789abcdef1"]
}

run "the_database_is_private" {
  command = plan

  assert {
    condition     = !aws_db_instance.this.publicly_accessible
    error_message = "The database must not be reachable from the internet."
  }

  assert {
    condition     = aws_db_subnet_group.this.subnet_ids == toset(var.subnet_ids)
    error_message = "The database must be placed only in the subnets it is given, the private ones."
  }
}

run "the_database_is_single_az" {
  command = plan

  assert {
    condition     = !aws_db_instance.this.multi_az
    error_message = "A standby doubles the cost: the database must be single-AZ."
  }
}

run "the_data_is_protected" {
  command = plan

  assert {
    condition     = aws_db_instance.this.storage_encrypted
    error_message = "The storage must be encrypted."
  }

  assert {
    condition     = aws_db_instance.this.backup_retention_period == 7
    error_message = "Automated backups must be kept for seven days."
  }

  assert {
    condition     = aws_db_instance.this.deletion_protection
    error_message = "The database must refuse to be deleted by default."
  }
}

# Applied against the mock, because the suffix is unknown in a plan.
run "a_destroy_leaves_a_final_snapshot_with_its_own_name" {
  command = apply

  assert {
    condition = (
      !aws_db_instance.this.skip_final_snapshot &&
      can(regex("^itacatl-test-final-[0-9a-f]{8}$", aws_db_instance.this.final_snapshot_identifier))
    )
    error_message = "A destroy must leave a final snapshot, named with a random suffix so it never collides with an earlier one."
  }
}

run "no_password_is_handled_by_terraform" {
  command = plan

  assert {
    condition     = aws_db_instance.this.manage_master_user_password && aws_db_instance.this.password == null
    error_message = "RDS must manage the master password in Secrets Manager, never Terraform."
  }

  assert {
    condition     = aws_db_instance.this.iam_database_authentication_enabled
    error_message = "IAM authentication must be on: the application signs in with a token."
  }
}

run "connections_must_use_tls" {
  command = plan

  assert {
    condition     = anytrue([for parameter in aws_db_parameter_group.this.parameter : parameter.name == "rds.force_ssl" && parameter.value == "1"])
    error_message = "The parameter group must require TLS (rds.force_ssl = 1)."
  }

  assert {
    condition     = aws_db_parameter_group.this.family == "postgres${aws_db_instance.this.engine_version}"
    error_message = "The parameter group's family must match the engine's major version."
  }
}

# Applied against the mock, because the rules a security group gets are unknown in a plan.
run "nothing_can_reach_the_database_yet" {
  command = apply

  assert {
    condition     = length(aws_security_group.this.ingress) == 0 && length(aws_security_group.this.egress) == 0
    error_message = "The database's security group must have no rules of its own: clients add theirs."
  }

  assert {
    condition     = aws_security_group.this.vpc_id == var.vpc_id
    error_message = "The security group must belong to the VPC it is given."
  }
}

run "deletion_protection_can_be_lifted_for_a_destroy" {
  command = plan

  variables {
    deletion_protection = false
  }

  assert {
    condition     = !aws_db_instance.this.deletion_protection && !aws_db_instance.this.skip_final_snapshot
    error_message = "Lifting the protection must still leave a final snapshot."
  }
}

run "one_subnet_is_rejected" {
  command = plan

  variables {
    subnet_ids = ["subnet-0123456789abcdef0"]
  }

  expect_failures = [var.subnet_ids]
}

run "backups_cannot_be_turned_off" {
  command = plan

  variables {
    backup_retention_days = 0
  }

  expect_failures = [var.backup_retention_days]
}
