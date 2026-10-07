variable "env" {
  description = "Environment name: dev or prod. Sets SPRING_PROFILES_ACTIVE (JSON logs, no local defaults)."
  type        = string
  validation {
    condition     = contains(["dev", "prod"], var.env)
    error_message = "env must be dev or prod."
  }
}

variable "project_id" {
  description = "GCP project id."
  type        = string
}

variable "region" {
  description = "GCP region for the service."
  type        = string
}

variable "service_name" {
  description = "Cloud Run service name, e.g. grind-api."
  type        = string
}

variable "image" {
  description = "Container image, pinned by digest (…@sha256:…) so a revision always runs the build that was tested."
  type        = string
}

variable "config_file" {
  description = "Path of the service's settings for this environment (deploy/config/<env>/<service>.yaml)."
  type        = string
}

variable "secret_env" {
  description = "Environment variables filled from Secret Manager: variable name => secret id (latest version)."
  type        = map(string)
  default     = {}
}

variable "min_instance_count" {
  description = "Instances kept warm. 0 scales to zero when idle (cheapest, slower first request)."
  type        = number
  default     = 0
}

variable "max_instance_count" {
  description = "Upper bound on instances. Caps database connections (instances x pool size); see modules/services."
  type        = number
  validation {
    condition     = var.max_instance_count >= 1
    error_message = "max_instance_count must be at least 1."
  }
}

variable "cpu" {
  description = "CPU limit per instance."
  type        = string
  default     = "1"
}

variable "memory" {
  description = "Memory limit per instance."
  type        = string
  default     = "512Mi"
}

variable "public" {
  description = "Whether anyone may invoke the service (grind-api: yes, protected by the edge secret and JWTs). The worker is invoked only by Pub/Sub."
  type        = bool
  default     = false
}
