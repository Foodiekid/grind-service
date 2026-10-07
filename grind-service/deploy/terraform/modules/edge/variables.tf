variable "env" {
  description = "Environment name: dev or prod."
  type        = string
  validation {
    condition     = contains(["dev", "prod"], var.env)
    error_message = "env must be dev or prod."
  }
}

variable "cloudflare_zone_id" {
  description = "Cloudflare zone (domain) id. Empty until OD-4 (domain) is decided; the module then plans nothing."
  type        = string
  default     = ""
  validation {
    condition     = var.cloudflare_zone_id == "" || can(regex("^[0-9a-f]{32}$", var.cloudflare_zone_id))
    error_message = "cloudflare_zone_id must be empty or a 32-character hexadecimal zone id."
  }
}

variable "api_hostname" {
  description = "Public API hostname, e.g. api.example.com."
  type        = string
  default     = ""
}

variable "api_requests_per_minute" {
  description = "Requests per minute one client (IP and Cloudflare data centre) may send to /grind/api/** before being blocked."
  type        = number
  default     = 100
}

variable "connections_requests_per_minute" {
  description = "Stricter limit for the partner token broker (/grind/api/v1/connections/**): a person connects a partner rarely."
  type        = number
  default     = 10
}

variable "rate_limit_block_seconds" {
  description = "How long a client that went over a limit stays blocked."
  type        = number
  default     = 60
}
