variable "env" {
  description = "Environment name: dev or prod."
  type        = string
}

variable "project_id" {
  description = "GCP project id whose spend is watched."
  type        = string
}

variable "billing_account_id" {
  description = "Billing account id (XXXXXX-XXXXXX-XXXXXX) the project is linked to."
  type        = string
  validation {
    condition     = can(regex("^[0-9A-F]{6}-[0-9A-F]{6}-[0-9A-F]{6}$", var.billing_account_id))
    error_message = "billing_account_id must look like 0123AB-4567CD-89EF01."
  }
}

variable "currency_code" {
  description = "Currency of the billing account (a budget must use the account's own currency)."
  type        = string
  default     = "USD"
}

variable "monthly_limit" {
  description = "Spend that counts as \"not free any more\", in whole units of the currency."
  type        = number
  default     = 1
}
