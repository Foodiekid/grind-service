terraform {
  required_version = ">= 1.9"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 8.5"
    }
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 5.27"
    }
    neon = {
      source  = "kislerdm/neon"
      version = "~> 0.18"
    }
  }

  # State lives in a GCS bucket (created by hand once). Configure with: terraform init -backend-config=backend.hcl
  backend "gcs" {}
}
