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
