#!/usr/bin/env bash
# Deploy check: calls GET /internal/status on grind-api, grind-worker and grind-partner and prints one line each.
# Exits non-zero if any service is not UP, so CI can use it after a deploy.
#
#   deploy/status.sh prod
#
# Needs: gcloud signed in as someone listed in the environment's `operators` (Terraform), terraform, curl, python3.
set -euo pipefail

env="${1:?usage: deploy/status.sh <dev|prod>}"
here="$(cd "$(dirname "$0")" && pwd)"
tf() { terraform -chdir="$here/terraform/envs/$env" output -raw "$1"; }

ops_account="$(tf ops_service_account_email)"
failed=0
printf '%-14s %-6s %-9s %-10s %-22s %s\n' SERVICE HTTP STATUS VERSION BUILT CHECKS
for service in api worker partner; do
  url="$(tf "${service}_url")"
  token="$(gcloud auth print-identity-token --impersonate-service-account="$ops_account" --audiences="$url" 2>/dev/null)"
  response="$(curl -sS --max-time 20 -w '\n%{http_code}' -H "Authorization: Bearer $token" "$url/internal/status" || true)"
  code="$(tail -n1 <<<"$response")"
  body="$(sed '$d' <<<"$response")"
  summary="$(python3 -c '
import json, sys
try:
    s = json.loads(sys.stdin.read())
    checks = " ".join(f"{k}={v}" for k, v in sorted(s.get("checks", {}).items()))
    print(s.get("status", "?"), s.get("version", "?"), s.get("builtAt") or "-", checks, sep="\t")
except Exception:
    print("?", "?", "-", "(no status body)", sep="\t")
' <<<"$body")"
  IFS=$'\t' read -r status version built checks <<<"$summary"
  printf '%-14s %-6s %-9s %-10s %-22s %s\n' "grind-$service" "$code" "$status" "$version" "$built" "$checks"
  [[ "$code" == "200" && "$status" == "UP" ]] || failed=1
done
exit "$failed"
