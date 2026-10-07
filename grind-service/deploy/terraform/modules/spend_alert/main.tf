# GRIND is meant to run inside Google Cloud's free tiers. Google has no hard spending cap, so this budget is the
# tripwire: the billing account's admins get an email at the first cent of real spend, again at the monthly limit,
# and when the month is forecast to pass it. It alerts only; it never stops a service.
#
# Needs the Cloud Billing Budget API (billingbudgets.googleapis.com) and the Billing Account Costs Manager role for
# whoever runs terraform apply.

data "google_project" "watched" {
  project_id = var.project_id
}

resource "google_billing_budget" "spend" {
  billing_account = var.billing_account_id
  display_name    = "GRIND ${var.env}: any spend"

  budget_filter {
    projects = ["projects/${data.google_project.watched.number}"]
  }

  amount {
    specified_amount {
      currency_code = var.currency_code
      units         = tostring(var.monthly_limit)
    }
  }

  # 1% of a 1-unit limit is the first cent.
  threshold_rules {
    threshold_percent = 0.01
  }

  threshold_rules {
    threshold_percent = 1.0
  }

  threshold_rules {
    threshold_percent = 1.0
    spend_basis       = "FORECASTED_SPEND"
  }
}
