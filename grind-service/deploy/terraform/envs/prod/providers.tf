# Credentials come from the environment, never from files in git:
#   Google: gcloud auth application-default login   ·   Cloudflare: CLOUDFLARE_API_TOKEN   ·   Neon: NEON_API_KEY
provider "google" {
  project = var.project_id
  region  = var.region
}

provider "cloudflare" {}

provider "neon" {}
