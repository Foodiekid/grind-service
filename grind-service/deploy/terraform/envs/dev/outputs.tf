# Read by deploy/status.sh.
output "api_url" {
  value = module.services.api_url
}

output "worker_url" {
  value = module.services.worker_url
}

output "partner_url" {
  value = module.services.partner_url
}

output "ops_service_account_email" {
  value = module.services.ops_service_account_email
}
