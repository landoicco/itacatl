variable "region" {
  description = "AWS region of the whole environment."
  type        = string
  default     = "us-east-1"
}

variable "project" {
  description = "Prefix of every resource name."
  type        = string
  default     = "itacatl"

  # The same rule as in bootstrap, whose state bucket name sets the 42-character limit, so one
  # project name works in both stacks. Modules whose names have tighter limits check their own.
  validation {
    condition     = can(regex("^[a-z0-9]([a-z0-9-]{0,40}[a-z0-9])?$", var.project))
    error_message = "The project must be 1 to 42 lowercase letters, digits or hyphens, not starting or ending with a hyphen."
  }
}

variable "database_deletion_protection" {
  description = "Whether the database refuses to be deleted. Set it to false and apply only right before a destroy (see docs/terraform.md)."
  type        = bool
  default     = true
}
