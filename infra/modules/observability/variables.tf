variable "name" {
  description = "Prefix of every resource name, such as itacatl-prod."
  type        = string
}

variable "alarm_email" {
  description = "Address the alarms are sent to. AWS emails it a link that must be followed before any alarm arrives."
  type        = string
  nullable    = false

  validation {
    condition     = can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.alarm_email))
    error_message = "Give an email address, such as alerts@example.com."
  }
}

variable "autoscaling_group_name" {
  description = "Auto Scaling group of the backend."
  type        = string
}

variable "backend_metrics_namespace" {
  description = "CloudWatch namespace the backend instance's agent publishes its memory and disk to."
  type        = string
}

variable "database_identifier" {
  description = "Identifier of the RDS instance."
  type        = string
}

variable "distribution_id" {
  description = "ID of the CloudFront distribution that serves the app and the API."
  type        = string
}
