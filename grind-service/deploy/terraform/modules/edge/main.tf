# Cloudflare in front of the public API: the zone's TLS settings, request filtering, rate limits and caching rules.
#
# Everything is created only once a zone exists (OD-4, the domain): with an empty cloudflare_zone_id this module
# plans nothing, so the rest of the environment can be planned before the domain is bought.
#
# The services enforce the same limits themselves (256 KiB bodies, 16 KiB on grind-partner; the edge secret; JWTs),
# so this is defence in depth, not the only line. Still to decide and add here: the DNS record for api_hostname and
# how /grind/api/v1/connections/** reaches grind-partner (an origin rule or a load balancer).
# Never apply without the owner's go-ahead.

locals {
  enabled = var.cloudflare_zone_id != ""

  api_paths         = "starts_with(http.request.uri.path, \"/grind/api/\")"
  connections_paths = "starts_with(http.request.uri.path, \"/grind/api/v1/connections/\")"

  # TLS 1.2 is the floor because the app supports Android 7-9, which can't do TLS 1.3; TLS 1.3 is used whenever the
  # phone supports it. Strict mode: Cloudflare checks the origin's certificate too.
  zone_settings = {
    ssl              = "strict"
    min_tls_version  = "1.2"
    tls_1_3          = "on"
    always_use_https = "on"
  }
}

resource "cloudflare_zone_setting" "tls" {
  for_each = local.enabled ? local.zone_settings : {}

  zone_id    = var.cloudflare_zone_id
  setting_id = each.key
  value      = each.value
}

# HSTS: browsers and clients that honour it never fall back to plain HTTP. No preload until the domain is final.
resource "cloudflare_zone_setting" "hsts" {
  count = local.enabled ? 1 : 0

  zone_id    = var.cloudflare_zone_id
  setting_id = "security_header"
  value = {
    strict_transport_security = {
      enabled            = true
      max_age            = 31536000
      include_subdomains = true
      nosniff            = true
      preload            = false
    }
  }
}

# Only the public API is served at this hostname; anything else is refused at the edge.
resource "cloudflare_ruleset" "firewall" {
  count = local.enabled ? 1 : 0

  zone_id = var.cloudflare_zone_id
  name    = "grind-${var.env}-firewall"
  kind    = "zone"
  phase   = "http_request_firewall_custom"

  rules = [{
    description = "Only the public API is served here"
    expression  = "not ${local.api_paths}"
    action      = "block"
    enabled     = true
  }]
}

# Per-client limits, counted per IP and Cloudflare data centre. The token broker has a stricter limit and comes first.
# Plan note: Cloudflare's Free plan allows fewer rate-limiting rules and only a 10-second window; on that plan keep
# one rule and lower the numbers to the same per-second rate.
resource "cloudflare_ruleset" "rate_limits" {
  count = local.enabled ? 1 : 0

  zone_id = var.cloudflare_zone_id
  name    = "grind-${var.env}-rate-limits"
  kind    = "zone"
  phase   = "http_ratelimit"

  rules = [
    {
      description = "Partner token broker"
      expression  = local.connections_paths
      action      = "block"
      enabled     = true
      ratelimit = {
        characteristics     = ["ip.src", "cf.colo.id"]
        period              = 60
        requests_per_period = var.connections_requests_per_minute
        mitigation_timeout  = var.rate_limit_block_seconds
      }
    },
    {
      description = "Public API"
      expression  = local.api_paths
      action      = "block"
      enabled     = true
      ratelimit = {
        characteristics     = ["ip.src", "cf.colo.id"]
        period              = 60
        requests_per_period = var.api_requests_per_minute
        mitigation_timeout  = var.rate_limit_block_seconds
      }
    },
  ]
}

# Signed-in, per-user responses must never sit in a shared cache, whatever headers an origin sends.
resource "cloudflare_ruleset" "cache" {
  count = local.enabled ? 1 : 0

  zone_id = var.cloudflare_zone_id
  name    = "grind-${var.env}-cache"
  kind    = "zone"
  phase   = "http_request_cache_settings"

  rules = [{
    description = "Never cache the API"
    expression  = local.api_paths
    action      = "set_cache_settings"
    enabled     = true
    action_parameters = {
      cache = false
    }
  }]
}
