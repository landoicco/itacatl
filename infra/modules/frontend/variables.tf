variable "name" {
  description = "Prefix of every resource name, such as itacatl-prod."
  type        = string

  # The bucket's name adds "-frontend-" and the 12-digit account ID, within S3's 63 characters.
  validation {
    condition     = length(var.name) <= 41
    error_message = "The name must be at most 41 characters, so the bucket's name fits S3's limit."
  }
}

variable "backend_origin_domain_name" {
  description = "DNS name the backend answers on, which CloudFront forwards /api/* to."
  type        = string
}

variable "backend_origin_port" {
  description = "Port the backend listens on, over HTTP."
  type        = number
  default     = 8080
}

variable "domain" {
  description = "The marketplace's own domain and its Route 53 hosted zone. Null serves it on CloudFront's *.cloudfront.net domain, with no certificate or DNS records; the domain is a deployment setting, chosen by whoever launches."
  type = object({
    name    = string
    zone_id = string
  })
  default = null
}
