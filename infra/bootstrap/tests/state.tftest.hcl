# Runs against a mocked AWS provider: no credentials, no account, nothing created.
# Plan only, since prevent_destroy would stop the cleanup that follows an apply.

mock_provider "aws" {}

override_data {
  target = data.aws_caller_identity.current
  values = {
    account_id = "123456789012"
  }
}

run "bucket_name_is_unique_per_account" {
  command = plan

  assert {
    condition     = aws_s3_bucket.state.bucket == "itacatl-tfstate-123456789012"
    error_message = "The state bucket must be named after the project and the account ID."
  }
}

run "state_is_versioned_and_encrypted" {
  command = plan

  assert {
    condition     = aws_s3_bucket_versioning.state.versioning_configuration[0].status == "Enabled"
    error_message = "Versioning must be on, so a bad state can be rolled back."
  }

  assert {
    condition = alltrue([
      for rule in aws_s3_bucket_server_side_encryption_configuration.state.rule :
      rule.apply_server_side_encryption_by_default[0].sse_algorithm == "AES256"
    ])
    error_message = "State must be encrypted at rest with SSE-S3."
  }
}

run "bucket_is_never_public" {
  command = plan

  assert {
    condition = alltrue([
      aws_s3_bucket_public_access_block.state.block_public_acls,
      aws_s3_bucket_public_access_block.state.block_public_policy,
      aws_s3_bucket_public_access_block.state.ignore_public_acls,
      aws_s3_bucket_public_access_block.state.restrict_public_buckets,
    ])
    error_message = "Every public access block setting must be on."
  }

  assert {
    condition     = aws_s3_bucket_ownership_controls.state.rule[0].object_ownership == "BucketOwnerEnforced"
    error_message = "ACLs must be disabled."
  }
}

run "plain_http_is_denied" {
  command = plan

  assert {
    condition = anytrue([
      for statement in jsondecode(aws_s3_bucket_policy.state.policy).Statement :
      statement.Effect == "Deny" && statement.Condition.Bool["aws:SecureTransport"] == "false"
    ])
    error_message = "The bucket policy must deny requests without TLS."
  }
}

run "old_versions_expire" {
  command = plan

  variables {
    noncurrent_version_days = 30
  }

  assert {
    condition     = aws_s3_bucket_lifecycle_configuration.state.rule[0].noncurrent_version_expiration[0].noncurrent_days == 30
    error_message = "Old state versions must expire after the configured days."
  }

  assert {
    condition     = aws_s3_bucket_lifecycle_configuration.state.rule[0].noncurrent_version_expiration[0].newer_noncurrent_versions == 10
    error_message = "The newest old versions must be kept whatever their age, so a rarely applied stack keeps its rollback points."
  }
}

run "retention_must_be_positive" {
  command = plan

  variables {
    noncurrent_version_days = 0
  }

  expect_failures = [var.noncurrent_version_days]
}

run "project_must_be_a_valid_bucket_prefix" {
  command = plan

  variables {
    project = "Itacatl_Prod"
  }

  expect_failures = [var.project]
}
