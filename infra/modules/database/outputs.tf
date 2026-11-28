output "address" {
  description = "Host name of the database, for the backend's JDBC URL."
  value       = aws_db_instance.this.address
}

output "port" {
  description = "Port the database listens on."
  value       = aws_db_instance.this.port
}

output "database_name" {
  description = "Name of the database the application uses."
  value       = aws_db_instance.this.db_name
}

output "master_username" {
  description = "Master user, which Flyway migrates as."
  value       = aws_db_instance.this.username
}

output "master_user_secret_arn" {
  description = "ARN of the Secrets Manager secret holding the master password, which the backend reads to migrate."
  value       = one(aws_db_instance.this.master_user_secret[*].secret_arn)
}

output "resource_id" {
  description = "Resource ID of the database, which IAM policies name in rds-db:connect to let a role sign in as a database user."
  value       = aws_db_instance.this.resource_id
}

output "security_group_id" {
  description = "Security group of the database, which the backend module adds its ingress rule to."
  value       = aws_security_group.this.id
}

output "subnet_ids" {
  description = "Subnets the database can be placed in."
  value       = aws_db_subnet_group.this.subnet_ids
}

output "identifier" {
  description = "Identifier of the database instance, the dimension of its CloudWatch metrics."
  value       = aws_db_instance.this.identifier
}
