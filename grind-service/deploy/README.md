# Deploy

Everything needed to run the services in Google Cloud, outside the code. **Never `terraform apply` without the
owner's go-ahead**: it creates resources in the owner's cloud accounts and may cost money.

```
config/<env>/grind-api.yaml      settings per environment (no secrets; ${NAME} placeholders for Secret Manager values)
config/<env>/grind-worker.yaml
config/<env>/grind-partner.yaml  its public-api block must match grind-api.yaml (a build test checks it)
terraform/modules/cloud_run/     one Cloud Run service: service account, settings mounted from Secret Manager,
                                 secret env vars, instance limits, health probes
terraform/modules/services/      the three services for an environment, their secrets, and the plan-time checks
                                 (connection budget, no CHANGE-ME left); tests in tests/ (terraform test)
terraform/modules/spend_alert/   a $1 monthly budget on the project: email at the first cent of real spend
terraform/modules/{pubsub,r2,neon,edge}/   still inputs only
terraform/envs/{dev,prod}/       one root module per environment, state in a GCS bucket
```

## Cost: stay inside the free tiers (owner, 2026-10-07)
Target: $0 a month for about 2,000 users. Google has no hard cap, so `modules/spend_alert` emails the billing
account's admins at the first cent, at $1, and when $1 is forecast. Free tiers are per **billing account**, so dev
and prod share them. Checked 2026-10-07:

| Item | Free each month | GRIND | Note |
|---|---|---|---|
| Cloud Run requests | 2M | ~0.9M API + ~0.9M worker (15 syncs/user/day; Pub/Sub push doubles them) | the tightest limit; watch it |
| Cloud Run CPU / memory | 180k vCPU-s / 360k GiB-s | request time + every cold start | keep `api_min_instances = 0`: one warm instance is ~$10/month |
| Secret Manager versions | 6 | 11 per environment (8 secrets + 3 settings files) | ~$0.30/month for prod alone, ~$1 with dev too |
| Secret Manager reads | 10,000 | ~8 per cold start | pennies once cold starts pass ~1,200 a month |
| Cloud Scheduler jobs | 3 | 3 per environment | dev + prod = 3 paid jobs, $0.30/month |
| Artifact Registry | 0.5 GB | 3 images | add a clean-up policy (keep the last few) with the registry |

Keep dev torn down (`terraform destroy`) when it isn't being used, or the shared tiers go to it.

## Change a setting (no rebuild)
1. Edit `config/<env>/<service>.yaml`.
2. `cd terraform/envs/<env> && terraform plan && terraform apply`.
   The file becomes a new Secret Manager version and Cloud Run starts a new revision of the **same image**. Rolling
   back the revision also rolls back the settings.

## Secrets
Created empty by Terraform (`db-app-password`, `db-owner-password`, `r2-access-key-id`, `r2-secret-access-key`,
`edge-secret`, `supabase-service-role-key`, `oura-client-secret`, `whoop-client-secret`); values are added by hand so they never reach Terraform state or git:
`printf '%s' "$VALUE" | gcloud secrets versions add <secret-id> --data-file=-`, then redeploy. Every secret needs a version before the first deploy, even one whose provider block you
removed from the settings (any placeholder will do), because Cloud Run reads them all at start-up.

## Edge (Cloudflare)
`modules/edge` manages the zone once `cloudflare_zone_id` is set (OD-4): TLS 1.2 minimum with TLS 1.3 on (the app
supports Android 7-9), strict origin TLS, HSTS, only `/grind/api/**` served, rate limits per client (100/min for the
API, 10/min for the token broker), and the API never cached. Cloudflare's Free plan allows fewer rate-limit rules and
only a 10-second window; adjust there. Tests: `cd terraform/modules/edge && terraform init && terraform test`.

## Routing
grind-api and grind-partner both serve the public API: Cloudflare must send `/grind/api/v1/connections/**` to
grind-partner and everything else under `/grind/api/**` to grind-api (an origin rule or a load balancer URL map,
decided when the `edge` module is built).

## Check a deploy
```bash
deploy/status.sh prod      # one line per service: HTTP code, status, version, build time, health checks
```
Exits non-zero unless every service is UP. You must be listed in the environment's `operators` (tfvars); the script
impersonates the `grind-ops` account, so nobody holds a key. Each service's `grind.ops.audience` in
`config/<env>/<service>.yaml` must be its own run.app URL (from `terraform output`).

## Scheduled jobs
`modules/services` creates the `grind-scheduler` service account (may invoke the worker only) and one Cloud Scheduler
job per worker job. After the first apply, put the worker's URL and that account in
`config/<env>/grind-worker.yaml` under `grind.worker.scheduler`, then apply again.

## Database roles
The schema (`grind`) is created by grind-api's migrations, which run as `grind_owner`. Three roles must exist in Neon
before the first deploy: `grind_owner` (migrations), `grind_app` (the services; row-level security applies) and
`grind_support` (read-only queries by support; row-level security applies, so set `app.user_id` first). The `neon`
module is still inputs only, so create them by hand until it is written. Design: `docs/grind-database.md`.

**Passwords and connections in dev and prod** (local `grind_owner` / `grind_app` / `grind_support` passwords exist
only on a laptop and are never used anywhere else):
- Every password is random, at least 32 bytes, different per role **and** per environment (dev never reuses a prod
  password): `openssl rand -base64 32 | gcloud secrets versions add <secret-id> --data-file=-`. It goes straight from
  the generator into Secret Manager; nobody types it, pastes it in chat or keeps it in a file.
- The services get only `db-app-password`; only grind-api (migrations) gets `db-owner-password`.
- No shared `grind_support` password in the cloud. Each person who needs read access gets their own login role that
  is a member of it (`create role alice login password '...' in role grind_support`), password in their password
  manager, removed when they leave.
- Connections check the server's certificate and host name, not just encryption (`sslmode=verify-full`, Java's
  trusted certificates), and use SCRAM channel binding (`channelBinding=require`), so a man in the middle can't
  impersonate Neon or relay the login. A build test fails if a deploy file weakens either, or puts a password in
  the file instead of a `${...}` placeholder.
- Rotate: add a new secret version, then `alter role ... password` in Neon, then redeploy; disable the old version
  once the new revision is serving.

## Database connections
Each instance opens up to `maximum-pool-size` connections (in the service's yaml). `terraform plan` fails when
`max instances x pool size`, summed over both services, exceeds `database_connection_limit` (tfvars). Raise the
limit only after raising the database's own limit (Neon compute size or pooler).

## Checks
```bash
export PATH=$HOME/.local/bin:$PATH
terraform fmt -recursive -check terraform
(cd terraform/envs/prod && terraform init -backend=false && terraform validate)
(cd terraform/modules/services && terraform init && terraform test)
(cd terraform/modules/spend_alert && terraform init && terraform test)
```
Providers: `hashicorp/google ~> 8.5`, `cloudflare/cloudflare ~> 5.27`, `kislerdm/neon ~> 0.18`. Commit
`.terraform.lock.hcl`; never commit `*.tfvars` or state.
