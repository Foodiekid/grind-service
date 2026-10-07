variable "env" {
  description = "Environment name: dev or prod."
  type        = string
}

variable "project_id" {
  description = "GCP project id."
  type        = string
}

variable "region" {
  description = "GCP region."
  type        = string
}

variable "config_dir" {
  description = "Folder with this environment's settings: deploy/config/<env>."
  type        = string
}

variable "api_image" {
  description = "grind-api image, pinned by digest."
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
  description = "Upper bound on grind-partner instances (no database, so not part of the connection budget)."
  type        = number
}

variable "api_max_instances" {
  description = "Upper bound on grind-api instances."
  type        = number
}

variable "worker_max_instances" {
  description = "Upper bound on grind-worker instances."
  type        = number
}

variable "api_min_instances" {
  description = "grind-api instances kept warm (0 scales to zero)."
  type        = number
  default     = 0
}

variable "database_connection_limit" {
  description = "Connections the database accepts for the application (Neon: the compute's max_connections, or the pooler's limit), minus what you keep for migrations and admin."
  type        = number
}

variable "operators" {
  description = "Who may run deploy checks (impersonate grind-ops), as IAM members, e.g. [\"user:you@example.com\"]."
  type        = list(string)
  default     = []
}

