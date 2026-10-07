# Plans the services module against a mock Google provider (no cloud account needed): the safety checks must stop a
# plan that could exhaust database connections or still has CHANGE-ME settings.
# Run: cd deploy/terraform/modules/services && terraform init && terraform test

mock_provider "google" {}

variables {
  env                       = "prod"
  project_id                = "grind-test"
  region                    = "us-central1"
  config_dir                = "./tests/complete"
  api_image                 = "example/grind-api@sha256:0"
  worker_image              = "example/grind-worker@sha256:0"
  partner_image             = "example/grind-partner@sha256:0"
  partner_max_instances     = 4
  api_max_instances         = 10
  worker_max_instances      = 6
  database_connection_limit = 90
}

run "plans_within_the_connection_budget" {
  command = plan

  assert {
    condition     = output.peak_database_connections == 80
    error_message = "10 api x 5 + 6 workers x 5 should be 80 connections."
  }

  assert {
    condition     = length(output.scheduled_jobs) == 3
    error_message = "The three worker jobs should be scheduled."
  }
}

run "rejects_more_connections_than_the_database_accepts" {
  command = plan

  variables {
    api_max_instances = 20
  }

  expect_failures = [terraform_data.connection_budget]
}

run "rejects_settings_with_change_me_left" {
  command = plan

  variables {
    config_dir = "./tests/incomplete"
  }

  expect_failures = [terraform_data.config_complete]
}
