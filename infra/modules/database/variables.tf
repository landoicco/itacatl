variable "name" {
  description = "Identifier of the database instance and prefix of its other resources, such as itacatl-prod."
  type        = string
}

variable "vpc_id" {
  description = "VPC of the database's security group."
  type        = string
}

variable "subnet_ids" {
  description = "Private subnets the database can be placed in, one per availability zone."
  type        = list(string)

  # RDS requires a subnet group to span two zones, even for a single-AZ database.
  validation {
    condition     = length(var.subnet_ids) >= 2
    error_message = "Give at least two subnets, in different zones: RDS requires it."
  }
}

variable "instance_class" {
  description = "Instance class of the database."
  type        = string
  default     = "db.t4g.micro"
}

variable "allocated_storage" {
  description = "Storage of the database, in GB."
  type        = number
  default     = 20
}

variable "database_name" {
  description = "Name of the database the application uses."
  type        = string
  default     = "itacatl"
}

variable "master_username" {
  description = "Master user, which owns the tables and runs the migrations. RDS keeps its password in Secrets Manager."
  type        = string
  default     = "itacatl"
}

variable "backup_retention_days" {
  description = "Days the automated backups are kept."
  type        = number
  default     = 7

  # The data is the one thing the environment cannot rebuild, so backups are never turned off.
  validation {
    condition     = var.backup_retention_days >= 1 && var.backup_retention_days <= 35
    error_message = "Keep the backups between 1 and 35 days: 0 would turn them off."
  }
}

variable "deletion_protection" {
  description = "Whether the database refuses to be deleted. Set it to false, and apply, only right before a destroy."
  type        = bool
  default     = true
}
