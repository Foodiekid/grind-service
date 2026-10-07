variable "env" {
  description = "Environment name: dev or prod."
  type        = string
  validation {
    condition     = contains(["dev", "prod"], var.env)
    error_message = "env must be dev or prod."
  }
}

variable "neon_region" {
  description = "Neon region id, e.g. aws-us-east-2."
  type        = string
}
