output "api_url" {
  description = "grind-api run.app URL (Cloudflare forwards api.<domain> here)."
  value       = module.grind_api.url
}

output "worker_url" {
  description = "grind-worker run.app URL; the Pub/Sub push endpoint is this plus /internal/events."
  value       = module.grind_worker.url
}

output "api_service_account_email" {
  description = "grind-api's identity (needs publish rights on the events topic)."
  value       = module.grind_api.service_account_email
}

output "worker_service_account_email" {
  description = "grind-worker's identity."
  value       = module.grind_worker.service_account_email
}

output "peak_database_connections" {
  description = "Connections both services could open at full scale."
  value       = local.peak_connections
}

output "partner_url" {
  description = "grind-partner run.app URL (Cloudflare forwards api.<domain>/grind/api/v1/connections/** here)."
  value       = module.grind_partner.url
}

output "scheduled_jobs" {
  description = "Worker jobs started by Cloud Scheduler, with their schedules (UTC)."
  value       = local.scheduled_jobs
}

output "scheduler_service_account_email" {
  description = "Cloud Scheduler's identity; set it as grind.worker.scheduler.service-account."
  value       = google_service_account.scheduler.email
}

output "ops_service_account_email" {
  description = "Operator account for deploy checks; set it as grind.ops.service-account in every service."
  value       = google_service_account.ops.email
}

