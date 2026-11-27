variable "name" {
  description = "Prefix of every resource's Name tag, such as itacatl-prod."
  type        = string
}

variable "cidr_block" {
  description = "IPv4 range of the VPC. Each subnet's prefix is 8 bits longer: a /24 in a /16."
  type        = string
  default     = "10.0.0.0/16"

  # Subnets are 8 bits longer than the VPC and AWS's smallest subnet is a /28, so the VPC can be
  # at most a /20; AWS's largest VPC is a /16.
  validation {
    condition     = can(cidrhost(var.cidr_block, 0)) && tonumber(split("/", var.cidr_block)[1]) >= 16 && tonumber(split("/", var.cidr_block)[1]) <= 20
    error_message = "The VPC range must be a valid IPv4 CIDR block from /16 to /20."
  }
}

variable "az_count" {
  description = "Availability zones to spread the subnets over."
  type        = number
  default     = 2

  # An RDS subnet group needs subnets in at least two zones, even for a single-AZ database.
  validation {
    condition     = var.az_count >= 2 && var.az_count <= 6
    error_message = "Use between 2 and 6 availability zones: RDS needs at least two."
  }
}
