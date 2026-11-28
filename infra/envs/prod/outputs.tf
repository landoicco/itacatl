output "vpc_id" {
  description = "ID of the environment's VPC."
  value       = module.network.vpc_id
}

output "database_address" {
  description = "Host name of the database."
  value       = module.database.address
}

output "database_master_user_secret_arn" {
  description = "ARN of the Secrets Manager secret with the database's master password."
  value       = module.database.master_user_secret_arn
}

output "backend_repository_url" {
  description = "ECR repository the backend image is pushed to."
  value       = module.backend.repository_url
}

output "backend_image_tag_parameter_name" {
  description = "SSM parameter with the image tag the backend runs, which a deploy updates."
  value       = module.backend.image_tag_parameter_name
}

output "backend_autoscaling_group_name" {
  description = "Auto Scaling group a deploy starts an instance refresh on."
  value       = module.backend.autoscaling_group_name
}

output "backend_origin_domain_name" {
  description = "DNS name of the backend's Elastic IP, CloudFront's origin."
  value       = module.backend.origin_domain_name
}

output "frontend_bucket_name" {
  description = "S3 bucket the frontend's build is uploaded to."
  value       = module.frontend.bucket_name
}

output "frontend_distribution_id" {
  description = "CloudFront distribution a frontend deploy invalidates."
  value       = module.frontend.distribution_id
}

output "url" {
  description = "Address the marketplace is served on."
  value       = module.frontend.url
}

output "dashboard_name" {
  description = "CloudWatch dashboard with every alarm's metric."
  value       = module.observability.dashboard_name
}
