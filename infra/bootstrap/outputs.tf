output "state_bucket" {
  description = "Name of the state bucket, passed to every stack's backend with -backend-config."
  value       = aws_s3_bucket.state.bucket
}
