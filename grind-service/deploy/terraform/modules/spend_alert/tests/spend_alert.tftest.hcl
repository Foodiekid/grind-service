# Plans the spend alert against a mock Google provider (no cloud account needed): it must watch only this project,
# alert at the first cent, and refuse a malformed billing account id.
# Run: cd deploy/terraform/modules/spend_alert && terraform init && terraform test

mock_provider "google" {
  mock_data "google_project" {
    defaults = {
      number = "123456789012"
    }
  }
}

variables {
  env                = "prod"
  project_id         = "grind-test"
  billing_account_id = "0123AB-4567CD-89EF01"
}

run "alerts_at_the_first_cent_for_this_project_only" {
  command = plan

  assert {
    condition     = google_billing_budget.spend.budget_filter[0].projects == toset(["projects/123456789012"])
    error_message = "The budget must watch only this environment's project."
  }

  assert {
    condition     = google_billing_budget.spend.amount[0].specified_amount[0].units == "1"
    error_message = "The monthly limit should be 1 USD."
  }

  assert {
    condition     = google_billing_budget.spend.threshold_rules[0].threshold_percent == 0.01
    error_message = "The first alert should fire at the first cent."
  }
}

run "rejects_a_malformed_billing_account" {
  command = plan

  variables {
    billing_account_id = "billingAccounts/0123AB"
  }

  expect_failures = [var.billing_account_id]
}
