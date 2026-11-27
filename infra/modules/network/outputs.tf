output "vpc_id" {
  description = "ID of the VPC."
  value       = aws_vpc.this.id
}

output "vpc_cidr_block" {
  description = "IPv4 range of the VPC."
  value       = aws_vpc.this.cidr_block
}

output "availability_zones" {
  description = "Availability zones the subnets are spread over, in subnet order."
  value       = local.azs
}

output "public_subnet_ids" {
  description = "IDs of the public subnets, one per zone, for the backend's Auto Scaling group."
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "IDs of the private subnets, one per zone, for the database's subnet group."
  value       = aws_subnet.private[*].id
}
