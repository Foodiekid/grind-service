# The GRIND services for one environment (grind-api, grind-worker, grind-partner): the Secret Manager secrets they read, the two Cloud Run services, and two
# checks that fail `terraform plan`: settings files with CHANGE-ME left in them, and more database connections at
# full scale than the database accepts.
#
# Secret values are never in Terraform: after the first apply, add each one with
#   gcloud secrets versions add <secret-id> --data-file=-
# and redeploy. Rotating a secret is the same command; new instances pick up the latest version.

locals {
  # Environment variable => secret id. The variables are referenced as ${NAME} in deploy/config/<env>/*.yaml.
  api_secrets = {
    DB_APP_PASSWORD      = "db-app-password"
    DB_OWNER_PASSWORD    = "db-owner-password"
    R2_ACCESS_KEY_ID     = "r2-access-key-id"
    R2_SECRET_ACCESS_KEY = "r2-secret-access-key"
    EDGE_SECRET          = "edge-secret"
  }
  worker_secrets = {
    DB_APP_PASSWORD           = "db-app-password"
    R2_ACCESS_KEY_ID          = "r2-access-key-id"
    R2_SECRET_ACCESS_KEY      = "r2-secret-access-key"
    SUPABASE_SERVICE_ROLE_KEY = "supabase-service-role-key"
  }
  # Partner client secrets live only in grind-partner, which has no database.
  partner_secrets = {
    EDGE_SECRET         = "edge-secret"
    OURA_CLIENT_SECRET  = "oura-client-secret"
    WHOOP_CLIENT_SECRET = "whoop-client-secret"
  }

  api_config_file     = "${var.config_dir}/grind-api.yaml"
  worker_config_file  = "${var.config_dir}/grind-worker.yaml"
  partner_config_file = "${var.config_dir}/grind-partner.yaml"

  # Pool size per instance, read from the same settings the services use (5 is the default in the jar).
  api_pool_size    = try(yamldecode(file(local.api_config_file)).spring.datasource.hikari["maximum-pool-size"], 5)
  worker_pool_size = try(yamldecode(file(local.worker_config_file)).spring.datasource.hikari["maximum-pool-size"], 5)
  peak_connections = var.api_max_instances * local.api_pool_size + var.worker_max_instances * local.worker_pool_size
}

resource "google_secret_manager_secret" "runtime" {
  for_each  = toset(concat(values(local.api_secrets), values(local.worker_secrets), values(local.partner_secrets)))
  project   = var.project_id
  secret_id = each.value
  replication {
    auto {}
  }
}

resource "terraform_data" "config_complete" {
  input = [local.api_config_file, local.worker_config_file, local.partner_config_file]

  lifecycle {
    precondition {
      condition = alltrue([
        for config_file in [local.api_config_file, local.worker_config_file, local.partner_config_file] :
        !strcontains(file(config_file), "CHANGE-ME")
      ])
      error_message = "A settings file in ${var.config_dir} still contains CHANGE-ME; fill in the real values first."
    }
  }
}

resource "terraform_data" "connection_budget" {
  input = local.peak_connections

  lifecycle {
    precondition {
      condition     = local.peak_connections <= var.database_connection_limit
      error_message = "At full scale the services could open ${local.peak_connections} database connections (max instances x maximum-pool-size), more than database_connection_limit (${var.database_connection_limit}). Lower the max instances or the pool sizes, or raise the database's limit."
    }
  }
}

module "grind_api" {
  source = "../cloud_run"

  env                = var.env
  project_id         = var.project_id
  region             = var.region
  service_name       = "grind-api"
  image              = var.api_image
  config_file        = local.api_config_file
  secret_env         = { for name, id in local.api_secrets : name => google_secret_manager_secret.runtime[id].secret_id }
  min_instance_count = var.api_min_instances
  max_instance_count = var.api_max_instances
  public             = true

  depends_on = [terraform_data.config_complete, terraform_data.connection_budget]
}

module "grind_worker" {
  source = "../cloud_run"

  env                = var.env
  project_id         = var.project_id
  region             = var.region
  service_name       = "grind-worker"
  image              = var.worker_image
  config_file        = local.worker_config_file
  secret_env         = { for name, id in local.worker_secrets : name => google_secret_manager_secret.runtime[id].secret_id }
  max_instance_count = var.worker_max_instances
  public             = false

  depends_on = [terraform_data.config_complete, terraform_data.connection_budget]
}

# No database, so it is not part of the connection budget.
module "grind_partner" {
  source = "../cloud_run"

  env                = var.env
  project_id         = var.project_id
  region             = var.region
  service_name       = "grind-partner"
  image              = var.partner_image
  config_file        = local.partner_config_file
  secret_env         = { for name, id in local.partner_secrets : name => google_secret_manager_secret.runtime[id].secret_id }
  max_instance_count = var.partner_max_instances
  public             = true

  depends_on = [terraform_data.config_complete]
}

# Cloud Scheduler starts the worker's housekeeping jobs (POST /internal/jobs/<name>) with its own identity, which may
# invoke the worker and nothing else; the worker only accepts it on the jobs endpoint. Each run works for at most
# grind.worker.jobs.time-budget (20 min), inside the 30-minute attempt deadline, and is safe to retry.
locals {
  scheduled_jobs = {
    "account-deletion-sweep"  = "15 * * * *" # hourly
    "orphan-blob-sweep"       = "30 3 * * *" # daily 03:30 UTC
    "storage-usage-reconcile" = "0 4 * * 0"  # weekly, Sunday 04:00 UTC
  }
}

resource "google_service_account" "scheduler" {
  project      = var.project_id
  account_id   = "grind-scheduler"
  display_name = "Cloud Scheduler for grind-worker jobs (${var.env})"
}

resource "google_cloud_run_v2_service_iam_member" "scheduler_invokes_worker" {
  project  = var.project_id
  location = var.region
  name     = module.grind_worker.service_name
  role     = "roles/run.invoker"
  member   = google_service_account.scheduler.member
}

resource "google_cloud_scheduler_job" "worker" {
  for_each = local.scheduled_jobs

  project          = var.project_id
  region           = var.region
  name             = "grind-${each.key}"
  schedule         = each.value
  time_zone        = "Etc/UTC"
  attempt_deadline = "1800s"

  retry_config {
    retry_count = 2
  }

  http_target {
    http_method = "POST"
    uri         = "${module.grind_worker.url}/internal/jobs/${each.key}"

    # The audience is the worker's URL; deploy/config/<env>/grind-worker.yaml must name the same URL and this
    # service account under grind.worker.scheduler.
    oidc_token {
      service_account_email = google_service_account.scheduler.email
      audience              = module.grind_worker.url
    }
  }

  depends_on = [google_cloud_run_v2_service_iam_member.scheduler_invokes_worker]
}

# Operators check a deploy with deploy/status.sh, which calls GET /internal/status on each service with a Google token
# from this account (impersonated, so nobody holds a key). Each service accepts only this account for its own URL.
resource "google_service_account" "ops" {
  project      = var.project_id
  account_id   = "grind-ops"
  display_name = "Deploy checks: GET /internal/status (${var.env})"
}

resource "google_service_account_iam_member" "operators_impersonate_ops" {
  for_each = toset(var.operators)

  service_account_id = google_service_account.ops.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = each.value
}

# The worker is private; the operator account may call it (only /internal/status lets it through).
resource "google_cloud_run_v2_service_iam_member" "ops_invokes_worker" {
  project  = var.project_id
  location = var.region
  name     = module.grind_worker.service_name
  role     = "roles/run.invoker"
  member   = google_service_account.ops.member
}

