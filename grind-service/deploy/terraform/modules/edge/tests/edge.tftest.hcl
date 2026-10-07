# Plans the edge module against a mock Cloudflare provider: nothing before a domain exists; with one, TLS 1.2+,
# HSTS, an API-only firewall, two rate limits (broker stricter) and no caching of the API.
# Run: cd deploy/terraform/modules/edge && terraform init && terraform test

mock_provider "cloudflare" {}

variables {
  env                = "prod"
  cloudflare_zone_id = "0123456789abcdef0123456789abcdef"
}

run "creates_nothing_before_a_domain_exists" {
  command = plan

  variables {
    cloudflare_zone_id = ""
  }

  assert {
    condition     = length(cloudflare_ruleset.rate_limits) == 0 && length(cloudflare_zone_setting.tls) == 0
    error_message = "Without a zone, the module must plan nothing."
  }
}

run "secures_the_zone" {
  command = plan

  assert {
    condition     = cloudflare_zone_setting.tls["min_tls_version"].value == "1.2" && cloudflare_zone_setting.tls["ssl"].value == "strict"
    error_message = "TLS 1.2 minimum and strict origin checking are required."
  }

  assert {
    condition     = cloudflare_ruleset.rate_limits[0].rules[0].ratelimit.requests_per_period < cloudflare_ruleset.rate_limits[0].rules[1].ratelimit.requests_per_period
    error_message = "The token broker's limit must be stricter than the API's, and checked first."
  }

  assert {
    condition     = cloudflare_ruleset.cache[0].rules[0].action_parameters.cache == false
    error_message = "The API must never be cached at the edge."
  }
}
