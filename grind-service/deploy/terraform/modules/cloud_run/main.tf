# One Cloud Run service with its own service account and settings.
#
# Settings: the service's yaml from deploy/config/<env> is stored as a Secret Manager secret and mounted at
# /etc/grind/config/application.yaml. Each change creates a new secret version, which changes the revision template,
# so `terraform apply` rolls out a new revision of the same image. The service refuses to start without the file.
#
# Secrets (passwords, keys) are separate Secret Manager secrets passed as environment variables; their values are
# added outside Terraform, so they never reach the state file.

locals {
  config_mount = "/etc/grind/config"
}

resource "google_service_account" "service" {
  project      = var.project_id
  account_id   = var.service_name
  display_name = "${var.service_name} (${var.env})"
}

resource "google_secret_manager_secret" "config" {
  project   = var.project_id
  secret_id = "${var.service_name}-config"
  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "config" {
  secret      = google_secret_manager_secret.config.id
  secret_data = file(var.config_file)
}

# The service may read its own settings and the secrets it is given, nothing else.
resource "google_secret_manager_secret_iam_member" "config" {
  project   = var.project_id
  secret_id = google_secret_manager_secret.config.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = google_service_account.service.member
}

resource "google_secret_manager_secret_iam_member" "secret_env" {
  for_each  = var.secret_env
  project   = var.project_id
  secret_id = each.value
  role      = "roles/secretmanager.secretAccessor"
  member    = google_service_account.service.member
}

resource "google_cloud_run_v2_service" "service" {
  project             = var.project_id
  name                = var.service_name
  location            = var.region
  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = var.env == "prod"

  template {
    service_account = google_service_account.service.email

    scaling {
      min_instance_count = var.min_instance_count
      max_instance_count = var.max_instance_count
    }

    containers {
      image = var.image

      ports {
        container_port = 8080
      }

      resources {
        limits = {
          cpu    = var.cpu
          memory = var.memory
        }
        cpu_idle = true
      }

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = var.env
      }
      env {
        # No "optional:" prefix: a missing settings file stops the service at start-up.
        name  = "SPRING_CONFIG_ADDITIONAL_LOCATION"
        value = "file:${local.config_mount}/"
      }

      dynamic "env" {
        for_each = var.secret_env
        content {
          name = env.key
          value_source {
            secret_key_ref {
              secret  = env.value
              version = "latest"
            }
          }
        }
      }

      volume_mounts {
        name       = "config"
        mount_path = local.config_mount
      }

      startup_probe {
        http_get {
          path = "/readyz"
          port = 8080
        }
        period_seconds    = 3
        failure_threshold = 20
      }

      liveness_probe {
        http_get {
          path = "/livez"
          port = 8080
        }
        period_seconds = 30
      }
    }

    volumes {
      name = "config"
      secret {
        secret = google_secret_manager_secret.config.secret_id
        items {
          # A pinned version, not "latest": a settings change is a new revision, and a rollback restores old settings.
          version = google_secret_manager_secret_version.config.version
          path    = "application.yaml"
        }
      }
    }
  }

  depends_on = [
    google_secret_manager_secret_iam_member.config,
    google_secret_manager_secret_iam_member.secret_env,
  ]
}

resource "google_cloud_run_v2_service_iam_member" "public" {
  count    = var.public ? 1 : 0
  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.service.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}
