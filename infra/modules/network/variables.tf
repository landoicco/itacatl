variable "name" {
  description = "Prefix of every resource's Name tag, such as itacatl-prod."
  type        = string
}

variable "cidr_block" {
  description = "IPv4 range of the VPC. Each subnet's prefix is 8 bits longer: a /24 in a /16."
  type        = string
  default     = "10.0.0.0/16"

  # Subnets are 8 bits longer than the VPC and AWS's smallest subnet is a /28, so the VPC can be
  # at most a /20; AWS's largest VPC is a /16. The regex rejects IPv6 and other prefixes;
  # cidrsubnet rejects octets out of range and host bits set (10.0.1.0/16), which AWS refuses.
  validation {
    condition = (
      can(regex("^(\\d{1,3}\\.){3}\\d{1,3}/(1[6-9]|20)$", var.cidr_block)) &&
      try(cidrsubnet(var.cidr_block, 0, 0) == var.cidr_block, false)
    )
    error_message = "The VPC range must be an IPv4 network address from /16 to /20, such as 10.0.0.0/16."
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
