# Runs against a mocked AWS provider: no credentials, no account, nothing created.

# Fixed partition, region and account, so the bucket's name and the region check are predictable.
mock_provider "aws" {
  source = "../../testing/aws"
}

variables {
  name                       = "itacatl-test"
  backend_origin_domain_name = "ec2-192-0-2-10.compute-1.amazonaws.com"
}

run "the_bucket_is_private_and_named_per_account" {
  command = plan

  assert {
    condition     = aws_s3_bucket.this.bucket == "itacatl-test-frontend-111111111111"
    error_message = "The bucket must be named <name>-frontend-<account ID>."
  }

  assert {
    condition = (
      aws_s3_bucket_public_access_block.this.block_public_acls &&
      aws_s3_bucket_public_access_block.this.block_public_policy &&
      aws_s3_bucket_public_access_block.this.ignore_public_acls &&
      aws_s3_bucket_public_access_block.this.restrict_public_buckets
    )
    error_message = "The bucket must block every kind of public access."
  }

  assert {
    condition     = aws_s3_bucket_ownership_controls.this.rule[0].object_ownership == "BucketOwnerEnforced"
    error_message = "ACLs must be off, so only the bucket policy grants access."
  }
}

# Applied against the mock, because the distribution's ARN is unknown in a plan.
run "only_this_distribution_reads_the_files" {
  command = apply

  assert {
    condition = anytrue([
      for statement in jsondecode(aws_s3_bucket_policy.this.policy).Statement :
      statement.Effect == "Allow" && statement.Principal == { Service = "cloudfront.amazonaws.com" } &&
      try(statement.Condition.StringEquals["AWS:SourceArn"] == aws_cloudfront_distribution.this.arn, false)
    ])
    error_message = "Only this distribution may read the bucket."
  }

  assert {
    condition = anytrue([
      for statement in jsondecode(aws_s3_bucket_policy.this.policy).Statement :
      statement.Effect == "Deny" && try(statement.Condition.Bool["aws:SecureTransport"] == "false", false)
    ])
    error_message = "The bucket policy must deny requests without TLS."
  }
}

run "the_app_is_served_from_the_bucket_over_https" {
  command = plan

  assert {
    condition = (
      aws_cloudfront_distribution.this.default_cache_behavior[0].target_origin_id == "frontend" &&
      aws_cloudfront_distribution.this.default_cache_behavior[0].viewer_protocol_policy == "redirect-to-https" &&
      toset(aws_cloudfront_distribution.this.default_cache_behavior[0].allowed_methods) == toset(["GET", "HEAD"])
    )
    error_message = "The app must come from the bucket, over HTTPS, read-only."
  }

  assert {
    condition = anytrue([
      for association in aws_cloudfront_distribution.this.default_cache_behavior[0].function_association :
      association.event_type == "viewer-request"
    ])
    error_message = "The app's routes must be rewritten to index.html on the viewer's request."
  }

  assert {
    condition     = aws_cloudfront_distribution.this.price_class == "PriceClass_100"
    error_message = "The distribution must use the cheapest price class."
  }
}

run "the_api_goes_to_the_backend_uncached" {
  command = plan

  assert {
    condition = anytrue([
      for behavior in aws_cloudfront_distribution.this.ordered_cache_behavior :
      behavior.path_pattern == "/api/*" &&
      behavior.target_origin_id == "backend" &&
      behavior.viewer_protocol_policy == "https-only" &&
      behavior.cache_policy_id == data.aws_cloudfront_cache_policy.disabled.id &&
      behavior.origin_request_policy_id == data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id &&
      contains(behavior.allowed_methods, "POST") && contains(behavior.allowed_methods, "DELETE")
    ])
    error_message = "/api/* must go to the backend, over HTTPS, never cached, with every method and the viewer's cookies."
  }

  assert {
    condition = anytrue([
      for origin in aws_cloudfront_distribution.this.origin :
      origin.origin_id == "backend" &&
      origin.domain_name == var.backend_origin_domain_name &&
      origin.custom_origin_config[0].origin_protocol_policy == "http-only" &&
      origin.custom_origin_config[0].http_port == 8080
    ])
    error_message = "The backend's origin must be its address, on the app's port."
  }
}

run "without_a_domain_cloudfront_serves_its_own" {
  command = plan

  assert {
    condition = (
      length(aws_acm_certificate.this) == 0 &&
      length(aws_route53_record.certificate_validation) == 0 &&
      length(aws_route53_record.alias) == 0
    )
    error_message = "Without a domain there must be no certificate and no DNS records."
  }

  assert {
    condition = (
      aws_cloudfront_distribution.this.viewer_certificate[0].cloudfront_default_certificate &&
      length(aws_cloudfront_distribution.this.aliases) == 0
    )
    error_message = "Without a domain the distribution must use CloudFront's own certificate and domain."
  }
}

run "a_domain_gets_a_certificate_and_dns_records" {
  command = plan

  variables {
    domain = {
      name    = "shop.example.com"
      zone_id = "Z0123456789ABCDEFGHIJ"
    }
  }

  assert {
    condition = (
      length(aws_acm_certificate.this) == 1 &&
      aws_acm_certificate.this[0].domain_name == "shop.example.com" &&
      aws_acm_certificate.this[0].validation_method == "DNS"
    )
    error_message = "A domain must get a certificate, validated through DNS."
  }

  assert {
    condition     = toset(keys(aws_route53_record.alias)) == toset(["A", "AAAA"])
    error_message = "The domain must point at the distribution for IPv4 and IPv6."
  }

  assert {
    condition = (
      aws_cloudfront_distribution.this.aliases == toset(["shop.example.com"]) &&
      aws_cloudfront_distribution.this.viewer_certificate[0].ssl_support_method == "sni-only" &&
      aws_cloudfront_distribution.this.viewer_certificate[0].minimum_protocol_version == "TLSv1.2_2021"
    )
    error_message = "With a domain, the distribution must answer on it with its certificate and TLS 1.2 or later."
  }
}

run "a_domain_outside_us_east_1_is_rejected" {
  command = plan

  variables {
    domain = {
      name    = "shop.example.com"
      zone_id = "Z0123456789ABCDEFGHIJ"
    }
  }

  override_data {
    target = data.aws_region.current
    values = { region = "mx-central-1" }
  }

  expect_failures = [aws_acm_certificate.this]
}

run "a_name_too_long_for_the_bucket_is_rejected" {
  command = plan

  variables {
    name = "a-project-name-that-is-far-too-long-for-s3"
  }

  expect_failures = [var.name]
}
