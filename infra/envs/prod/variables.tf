variable "region" {
  description = "AWS region of the whole environment."
  type        = string
  default     = "us-east-1"
}

variable "project" {
  description = "Prefix of every resource name."
  type        = string
  default     = "itacatl"

  validation {
    condition     = can(regex("^[a-z0-9]([a-z0-9-]{0,40}[a-z0-9])?$", var.project))
    error_message = "The project must be 1 to 42 lowercase letters, digits or hyphens, not starting or ending with a hyphen."
  }
}
