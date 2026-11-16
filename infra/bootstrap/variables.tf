variable "region" {
  description = "AWS region of the state bucket."
  type        = string
  default     = "us-east-1"
}

variable "project" {
  description = "Prefix of every resource name."
  type        = string
  default     = "itacatl"
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
