output "repository_url" {
  description = "URL of the ECR repository that deploys push the backend image to."
  value       = aws_ecr_repository.this.repository_url
}

output "image_tag_parameter_name" {
  description = "SSM parameter holding the image tag new instances run, which a deploy updates."
  value       = aws_ssm_parameter.image_tag.name
}

output "autoscaling_group_name" {
  description = "Name of the Auto Scaling group, which a deploy starts an instance refresh on."
  value       = aws_autoscaling_group.this.name
}

output "origin_domain_name" {
  description = "Public DNS name of the backend's Elastic IP, for CloudFront's origin."
  value       = aws_eip.this.public_dns
}

output "public_ip" {
  description = "The backend's Elastic IP."
  value       = aws_eip.this.public_ip
}

output "security_group_id" {
  description = "Security group of the backend instance."
  value       = aws_security_group.this.id
}

output "log_group_name" {
  description = "CloudWatch log group with the backend's output."
  value       = aws_cloudwatch_log_group.this.name
}

output "subnet_ids" {
  description = "Subnets the Auto Scaling group launches the instance in."
  value       = aws_autoscaling_group.this.vpc_zone_identifier
}

output "origin_port" {
  description = "Port the backend answers on over HTTP, for CloudFront's origin."
  value       = local.app_port
}

output "metrics_namespace" {
  description = "CloudWatch namespace of the instance's memory and disk metrics, published by the CloudWatch agent."
  value       = local.metrics_namespace
}

output "origin_secret" {
  description = "Secret CloudFront sends in the X-Origin-Verify header, which the app requires on every request but its health checks."
  value       = random_password.origin_secret.result
  sensitive   = true
}
