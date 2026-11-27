output "bucket_name" {
  description = "S3 bucket that deploys upload the frontend's build to."
  value       = aws_s3_bucket.this.bucket
}

output "distribution_id" {
  description = "ID of the CloudFront distribution, which a deploy invalidates index.html on."
  value       = aws_cloudfront_distribution.this.id
}

output "distribution_domain_name" {
  description = "CloudFront's own domain for the distribution (*.cloudfront.net)."
  value       = aws_cloudfront_distribution.this.domain_name
}

output "api_origin_domain_name" {
  description = "Address CloudFront forwards /api/* to."
  value       = one([for origin in aws_cloudfront_distribution.this.origin : origin.domain_name if origin.origin_id == local.api_origin])
}

output "api_origin_port" {
  description = "Port CloudFront forwards /api/* to, over HTTP."
  value       = one([for origin in aws_cloudfront_distribution.this.origin : origin.custom_origin_config[0].http_port if origin.origin_id == local.api_origin])
}

output "url" {
  description = "Address the marketplace is served on: the domain when one is given, CloudFront's otherwise."
  value       = "https://${local.has_domain ? var.domain.name : aws_cloudfront_distribution.this.domain_name}"
}
