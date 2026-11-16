data "aws_caller_identity" "current" {}

locals {
  # Bucket names are global across AWS; the account ID makes this one unique without
  # writing it in the repository.
  state_bucket     = "${var.project}-tfstate-${data.aws_caller_identity.current.account_id}"
  state_bucket_arn = "arn:aws:s3:::${local.state_bucket}"
}

resource "aws_s3_bucket" "state" {
  bucket = local.state_bucket

  # Deleting it would lose every stack's state, and with it Terraform's record of what exists.
  # It outlives the ephemeral deploys; removing it on purpose is in docs/terraform.md.
  lifecycle {
    prevent_destroy = true
  }
}

# Every apply writes a new version, so a corrupted or wrong state can be rolled back.
resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id

  versioning_configuration {
    status = "Enabled"
  }
}

# SSE-S3 rather than a customer managed KMS key, which would cost $1/month for no extra
# protection here: only this account's principals can read the bucket anyway.
resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket = aws_s3_bucket.state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# State holds resource attributes, some of them sensitive: refuse any request over plain HTTP.
resource "aws_s3_bucket_policy" "state" {
  bucket = aws_s3_bucket.state.id

  # jsonencode instead of the aws_iam_policy_document data source, so the tests can read the
  # policy: a mocked provider returns made-up values for data sources.
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "DenyInsecureTransport"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource  = [local.state_bucket_arn, "${local.state_bucket_arn}/*"]
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }]
  })

  depends_on = [aws_s3_bucket_public_access_block.state]
}

resource "aws_s3_bucket_lifecycle_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    id     = "expire-old-state-versions"
    status = "Enabled"

    filter {}

    # A version expires only once it is older than the days AND not among the newest kept,
    # so a stack applied rarely never loses its last rollback points.
    noncurrent_version_expiration {
      noncurrent_days           = var.noncurrent_version_days
      newer_noncurrent_versions = var.noncurrent_versions_kept
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }

  depends_on = [aws_s3_bucket_versioning.state]
}
