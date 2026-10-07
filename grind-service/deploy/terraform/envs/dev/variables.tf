variable "project_id" {
  description = "GCP project id."
  type        = string
}

variable "billing_account_id" {
  description = "Billing account the project is linked to (gcloud billing projects describe PROJECT). Watched by a $1 spend alert."
  type        = string
}

variable "region" {
  description = "GCP region."
  type        = string
  default     = "us-central1"
}

variable "cloudflare_account_id" {
  description = "Cloudflare account id."
  type        = string
}

variable "cloudflare_zone_id" {
  description = "Cloudflare zone id (empty until a domain is chosen, OD-4)."
  type        = string
  default     = ""
}

variable "api_hostname" {
  description = "Public API hostname (empty until OD-4)."
  type        = string
  default     = ""
}

variable "neon_region" {
  description = "Neon region id."
  type        = string
  default     = "aws-us-east-2"
}

variable "api_image" {
  description = "grind-api image, pinned by digest (REGION-docker.pkg.dev/PROJECT/grind/grind-api@sha256:...)."
  type        = string
}

variable "worker_image" {
  description = "grind-worker image, pinned by digest."
  type        = string
}

variable "partner_image" {
  description = "grind-partner image, pinned by digest."
  type        = string
}

variable "partner_max_instances" {
  description = "Upper bound on grind-partner instances."
  type        = number
}

variable "api_min_instances" {
  description = "grind-api instances kept warm (0 scales to zero)."
  type        = number
  default     = 0
}

variable "api_max_instances" {
  description = "Upper bound on grind-api instances."
  type        = number
}

variable "worker_max_instances" {
  description = "Upper bound on grind-worker instances."
  type        = number
}

variable "database_connection_limit" {
  description = "Database connections available to the services (Neon compute max_connections minus a reserve for migrations and admin). terraform plan fails if max instances x pool size exceeds it."
  type        = number
}

variable "operators" {
  description = "Who may run deploy/status.sh (impersonate grind-ops), e.g. [\"user:you@example.com\"]."
  type        = list(string)
  default     = []
}

