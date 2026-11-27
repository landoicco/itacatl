output "vpc_id" {
  description = "ID of the environment's VPC."
  value       = module.network.vpc_id
}
