output "state_bucket" {
  description = "Name of the state bucket, for every stack's backend.hcl."
  value       = aws_s3_bucket.state.bucket
}

output "region" {
  description = "Region of the state bucket, for every stack's backend.hcl."
  value       = var.region
}
