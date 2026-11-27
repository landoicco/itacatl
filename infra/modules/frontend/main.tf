locals {
  has_domain = var.domain != null
  # Bucket names are global across AWS; the account ID makes this one unique without writing it in
  # the repository, like the state bucket's.
  bucket_name = "${var.name}-frontend-${data.aws_caller_identity.current.account_id}"
  s3_origin   = "frontend"
  api_origin  = "backend"
}

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

# AWS's managed policies, looked up by name rather than by ID so the code says what each one is.
data "aws_cloudfront_cache_policy" "optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_cache_policy" "disabled" {
  name = "Managed-CachingDisabled"
}

# Every header, cookie and query string except Host, which must name the origin, not the viewer's
# domain: the session cookie and the CSRF header reach the backend unchanged.
data "aws_cloudfront_origin_request_policy" "all_viewer_except_host" {
  name = "Managed-AllViewerExceptHostHeader"
}

# HSTS, nosniff, frame options, referrer policy and XSS protection on every response.
data "aws_cloudfront_response_headers_policy" "security_headers" {
  name = "Managed-SecurityHeadersPolicy"
}

# --- The build's files ---

# Every file can be rebuilt from its commit, so a destroy does not stop at a non-empty bucket and
# old versions are not kept.
#trivy:ignore:AVD-AWS-0089 Access logs need a second bucket; CloudFront is the only reader
#trivy:ignore:AVD-AWS-0090 Versioning keeps copies of files that every commit rebuilds
resource "aws_s3_bucket" "this" {
  bucket        = local.bucket_name
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "this" {
  bucket = aws_s3_bucket.this.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ACLs off: the bucket policy alone decides who reads it.
resource "aws_s3_bucket_ownership_controls" "this" {
  bucket = aws_s3_bucket.this.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# S3's own key; a customer-managed one bills monthly and protects nothing more for files the site
# serves to anyone.
#trivy:ignore:AVD-AWS-0132 S3's own encryption; a customer-managed key bills monthly
resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  bucket = aws_s3_bucket.this.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Only this distribution reads the files, signing its requests through origin access control;
# nothing reads them without TLS.
resource "aws_s3_bucket_policy" "this" {
  bucket = aws_s3_bucket.this.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "ReadFromThisDistribution"
        Effect    = "Allow"
        Principal = { Service = "cloudfront.amazonaws.com" }
        Action    = "s3:GetObject"
        Resource  = "${aws_s3_bucket.this.arn}/*"
        Condition = {
          StringEquals = { "AWS:SourceArn" = aws_cloudfront_distribution.this.arn }
        }
      },
      {
        Sid       = "DenyWithoutTls"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource  = [aws_s3_bucket.this.arn, "${aws_s3_bucket.this.arn}/*"]
        Condition = {
          Bool = { "aws:SecureTransport" = "false" }
        }
      },
    ]
  })

  # The public access block must exist first, or S3 may judge the policy before it applies.
  depends_on = [aws_s3_bucket_public_access_block.this]
}

resource "aws_cloudfront_origin_access_control" "this" {
  name                              = "${var.name}-frontend"
  description                       = "CloudFront signs its requests to the frontend bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# The app's routes (/shops/..., /manage/...) are not files: every path without an extension gets
# index.html, and React Router takes over. A function on the default behavior only, unlike a
# custom error response, which would also replace the API's own 404s.
resource "aws_cloudfront_function" "spa_routes" {
  name    = "${var.name}-spa-routes"
  comment = "Serves index.html for the app's routes"
  runtime = "cloudfront-js-2.0"
  publish = true
  code    = file("${path.module}/spa-routes.js")
}

# --- The certificate and DNS, only with a domain ---

resource "aws_acm_certificate" "this" {
  count = local.has_domain ? 1 : 0

  domain_name       = var.domain.name
  validation_method = "DNS"

  lifecycle {
    # A renewed or changed certificate must exist before CloudFront lets go of the old one.
    create_before_destroy = true

    # CloudFront only takes certificates from us-east-1.
    precondition {
      condition     = data.aws_region.current.region == "us-east-1"
      error_message = "CloudFront's certificate must be in us-east-1; apply this stack there."
    }
  }
}

# One domain, so one validation record. Counted on the domain, which is known when planning,
# rather than on the validation options, which are not until the certificate exists.
resource "aws_route53_record" "certificate_validation" {
  count = local.has_domain ? 1 : 0

  zone_id = var.domain.zone_id
  name    = one(aws_acm_certificate.this[0].domain_validation_options).resource_record_name
  type    = one(aws_acm_certificate.this[0].domain_validation_options).resource_record_type
  records = [one(aws_acm_certificate.this[0].domain_validation_options).resource_record_value]
  ttl     = 300
  # The record's name is the same for every certificate of this domain in the account, so one
  # left by an earlier certificate is taken over instead of failing the apply.
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "this" {
  count = local.has_domain ? 1 : 0

  certificate_arn         = aws_acm_certificate.this[0].arn
  validation_record_fqdns = [aws_route53_record.certificate_validation[0].fqdn]
}

resource "aws_route53_record" "alias" {
  for_each = local.has_domain ? toset(["A", "AAAA"]) : toset([])

  zone_id = var.domain.zone_id
  name    = var.domain.name
  type    = each.value

  alias {
    name                   = aws_cloudfront_distribution.this.domain_name
    zone_id                = aws_cloudfront_distribution.this.hosted_zone_id
    evaluate_target_health = false
  }
}

# --- The distribution ---

# Logs and WAF are decided with the observability module and the rate limiting decision.
#trivy:ignore:AVD-AWS-0010 Access logs bill for their storage; decided with the observability module
#trivy:ignore:AVD-AWS-0011 WAF bills monthly; rate limiting is an open decision of the infra plan
resource "aws_cloudfront_distribution" "this" {
  enabled         = true
  comment         = "${var.name}: the app and /api on one origin"
  aliases         = local.has_domain ? [var.domain.name] : []
  is_ipv6_enabled = true
  http_version    = "http2and3"
  # No default_root_object: the routes function already gives "/" index.html, so one mechanism
  # decides what every path of the app serves.
  # North America (Mexico included) and Europe: the cheapest price class.
  price_class = "PriceClass_100"

  origin {
    origin_id                = local.s3_origin
    domain_name              = aws_s3_bucket.this.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.this.id
  }

  # Plain HTTP to the instance: it has no certificate for its address, and its security group
  # admits only CloudFront (docs/infra-plan.md).
  origin {
    origin_id   = local.api_origin
    domain_name = var.backend_origin_domain_name

    custom_origin_config {
      origin_protocol_policy = "http-only"
      http_port              = var.backend_origin_port
      https_port             = 443
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  # The build's files. Their names carry a content hash, so they are cached for long; index.html
  # is uploaded with Cache-Control: no-cache, so a deploy shows at once.
  default_cache_behavior {
    target_origin_id           = local.s3_origin
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    cache_policy_id            = data.aws_cloudfront_cache_policy.optimized.id
    response_headers_policy_id = data.aws_cloudfront_response_headers_policy.security_headers.id

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa_routes.arn
    }
  }

  # The API: never cached, every method, the viewer's cookies and headers forwarded. Not
  # compressed: CloudFront only compresses what a cache policy with gzip or Brotli allows, which
  # CachingDisabled does not.
  ordered_cache_behavior {
    path_pattern               = "/api/*"
    target_origin_id           = local.api_origin
    viewer_protocol_policy     = "https-only"
    allowed_methods            = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods             = ["GET", "HEAD"]
    cache_policy_id            = data.aws_cloudfront_cache_policy.disabled.id
    origin_request_policy_id   = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
    response_headers_policy_id = data.aws_cloudfront_response_headers_policy.security_headers.id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # With a domain, its certificate and TLS 1.2 or later. Without one, CloudFront's own
  # certificate, which does not let the minimum version be raised.
  viewer_certificate {
    cloudfront_default_certificate = !local.has_domain
    acm_certificate_arn            = local.has_domain ? aws_acm_certificate_validation.this[0].certificate_arn : null
    ssl_support_method             = local.has_domain ? "sni-only" : null
    minimum_protocol_version       = local.has_domain ? "TLSv1.2_2021" : "TLSv1"
  }
}
