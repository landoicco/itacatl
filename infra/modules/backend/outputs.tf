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
