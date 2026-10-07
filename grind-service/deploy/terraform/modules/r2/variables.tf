variable "env" {
  description = "Environment name: dev or prod."
  type        = string
  validation {
    condition     = contains(["dev", "prod"], var.env)
    error_message = "env must be dev or prod."
  }
}

variable "cloudflare_account_id" {
  description = "Cloudflare account id that owns the bucket."
  type        = string
}
