variable "name" {
  description = "Prefix of every resource name, such as itacatl-prod."
  type        = string
}

variable "vpc_id" {
  description = "VPC of the backend's security group."
  type        = string
}

variable "subnet_ids" {
  description = "Public subnets the Auto Scaling group launches the instance in, one per availability zone."
  type        = list(string)

  # A second zone is where a replacement goes when the first has no spot capacity.
  validation {
    condition     = length(var.subnet_ids) >= 2
    error_message = "Give at least two subnets, in different zones, so a spot replacement has somewhere else to go."
  }
}

variable "instance_types" {
  description = "Graviton instance types the spot instance may be, all with 2 GB or more. More types make it rarer for none to have spot capacity."
  type        = list(string)
  default     = ["t4g.small", "c6g.medium", "c7g.medium"]

  # The AMI and the image are arm64, so an x86 type would not boot them. Graviton type names end
  # their family in "g", optionally followed by more letters: t4g, c7g, c6gn, m7gd.
  validation {
    condition = length(var.instance_types) >= 2 && alltrue([
      for type in var.instance_types : can(regex("^[a-z]+[0-9]+g[a-z]*\\.", type))
    ])
    error_message = "Give at least two Graviton (arm64) instance types, such as t4g.small and c7g.medium."
  }
}

variable "root_volume_size" {
  description = "Size of the instance's root volume, in GB."
  type        = number
  default     = 8
}

variable "image_retention_count" {
  description = "Images ECR keeps; older ones are deleted. Every image can be rebuilt from its commit."
  type        = number
  default     = 10
}

variable "log_retention_days" {
  description = "Days CloudWatch keeps the backend's logs."
  type        = number
  default     = 14
}

variable "database_security_group_id" {
  description = "Security group of the database, which this module adds the backend's ingress rule to."
  type        = string
}

variable "database_address" {
  description = "Host name of the database."
  type        = string
}

variable "database_name" {
  description = "Name of the database the application uses."
  type        = string
}

variable "database_resource_id" {
  description = "Resource ID of the database, which rds-db:connect names to let the instance sign in as itacatl_app."
  type        = string
}

variable "database_master_user_secret_arn" {
  description = "ARN of the Secrets Manager secret with the master user's credentials, which Flyway migrates with."
  type        = string
  nullable    = false
}
