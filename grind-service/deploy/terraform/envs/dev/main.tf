# GRIND — dev environment. One root module per environment, same modules, separate state.
locals {
  env = "dev"
}

module "services" {
  source = "../../modules/services"

  env        = local.env
  project_id = var.project_id
  region     = var.region
  config_dir = "${path.module}/../../../config/${local.env}"

  api_image                 = var.api_image
  worker_image              = var.worker_image
  partner_image             = var.partner_image
  partner_max_instances     = var.partner_max_instances
  api_min_instances         = var.api_min_instances
  api_max_instances         = var.api_max_instances
  worker_max_instances      = var.worker_max_instances
  database_connection_limit = var.database_connection_limit
  operators                 = var.operators
}

module "pubsub" {
  source     = "../../modules/pubsub"
  env        = local.env
  project_id = var.project_id
}

module "r2" {
  source                = "../../modules/r2"
  env                   = local.env
  cloudflare_account_id = var.cloudflare_account_id
}

module "neon" {
  source      = "../../modules/neon"
  env         = local.env
  neon_region = var.neon_region
}

module "edge" {
  source             = "../../modules/edge"
  env                = local.env
  cloudflare_zone_id = var.cloudflare_zone_id
  api_hostname       = var.api_hostname
}

module "spend_alert" {
  source             = "../../modules/spend_alert"
  env                = local.env
  project_id         = var.project_id
  billing_account_id = var.billing_account_id
}
