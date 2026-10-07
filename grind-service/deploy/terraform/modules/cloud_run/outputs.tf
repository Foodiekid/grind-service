output "url" {
  description = "The service's run.app URL."
  value       = google_cloud_run_v2_service.service.uri
}

output "service_account_email" {
  description = "Identity the service runs as; grant it access to other resources (Pub/Sub topic, ...)."
  value       = google_service_account.service.email
}

output "service_name" {
  description = "Cloud Run service name."
  value       = google_cloud_run_v2_service.service.name
}
