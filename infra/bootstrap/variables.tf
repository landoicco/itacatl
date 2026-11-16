variable "region" {
  description = "AWS region of the state bucket."
  type        = string
  default     = "us-east-1"
}

variable "project" {
  description = "Prefix of every resource name."
  type        = string
  default     = "itacatl"

  # It starts the state bucket's name, which S3 limits to lowercase letters, digits and hyphens,
  # 63 characters in all: the "-tfstate-<account ID>" suffix takes 21.
  validation {
    condition     = can(regex("^[a-z0-9]([a-z0-9-]{0,40}[a-z0-9])?$", var.project))
    error_message = "The project must be 1 to 42 lowercase letters, digits or hyphens, not starting or ending with a hyphen."
  }
}

variable "noncurrent_version_days" {
  description = "Days an overwritten state version is kept before it expires."
  type        = number
  default     = 90

  validation {
    condition     = var.noncurrent_version_days >= 1
    error_message = "Old state versions must be kept at least one day."
  }
}

variable "noncurrent_versions_kept" {
  description = "Most recent overwritten state versions kept whatever their age, so a stack left untouched for months can still be rolled back."
  type        = number
  default     = 10

  validation {
    condition     = var.noncurrent_versions_kept >= 1 && var.noncurrent_versions_kept <= 100
    error_message = "Keep between 1 and 100 old state versions, the range S3 accepts."
  }
}
