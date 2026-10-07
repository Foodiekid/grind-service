# GRIND service (backend)

Java 21 + Spring Boot 4.1 on Cloud Run, Pub/Sub (push), Neon Postgres 17 with row-level security, Cloudflare R2,
Supabase Auth (token check only). The server is **zero-knowledge**: it stores ciphertext, wrapped keys and sizes, and
never computes on health data.

```bash
export JAVA_HOME=$HOME/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
./mvnw verify                           # build every module and run the tests
./mvnw -pl grind-api -am package        # build one service and the modules it needs
```

## Modules

Dependencies point one way only: `grind-contract` and `grind-common` (the shared library every service builds on),
then `grind-core`, then the services. `grind-partner` uses only the contract and `grind-common`.

| Module | Holds | Depends on |
|---|---|---|
| `grind-contract` | Every contract in one place: the public API (`src/main/openapi/v1`, generated into `contract.api.v1`) and the Pub/Sub events (`contract.event`: payloads, `EventType`, `EventEnvelope`; topic names are settings). Change a contract here first. | none |
| `grind-common` | **The shared library for every service**, no business logic: error codes and one problem format, global exception handler, correlation ids, request and audit logging, log masking, `log4j2-spring.xml`, `UserId` / `RecordId`, and the opt-in public-API bundle (`@EnableGrindPublicApi`: Cloudflare check, client version, size limit, deprecation headers, Supabase JWT security, CORS, `CurrentUser`). Its test jar is the shared test kit (`testkit.ArchitectureRules`). | none |
| `grind-core` | Business logic: `account`, `keyring`, `sync`, `upload`, each `service` / `repository` / `domain`; Flyway migrations; R2 storage; event publishing. | contract, common |
| `grind-api` | Cloud Run service `grind-api`: `<module>/web/v1` controllers and mappers, security, API-only filters. | core, contract |
| `grind-worker` | Cloud Run service `grind-worker`: Pub/Sub push endpoint, dispatcher, one handler per event and module. | core |
| `grind-partner` | Cloud Run service `grind-partner`: partner connections (OAuth token broker for Oura, WHOOP, ...). Stateless, no database; holds the partners' client secrets. Partners are settings: one block + one secret per partner. | contract, common |
| `deploy/` | Per-environment settings (`config/<env>`) and Terraform (`terraform/`). See `deploy/README.md`. | none |

## Settings
Defaults shared by every environment are in each service's `src/main/resources/config/application.yaml`, local runs
add `application-local.yaml`. Dev and prod values live in `deploy/config/<env>/<service>.yaml`, outside the jar:
change one there and redeploy, no rebuild. Secrets are never in any file. Settings are validated at start-up.

## Features (modular monolith)

The code is grouped by **feature** first, then by layer, so each feature can grow, and later move into its own
service, without untangling the rest. A feature has the same name in every module:

| Feature | grind-core | grind-api | grind-worker | Owns tables |
|---|---|---|---|---|
| `account` | `account/{service,repository}` | `account/web/v1` | `account/` handlers | `accounts` (parent of every table) |
| `keyring` | `keyring/{service,repository,domain}` | `keyring/web/v1` | `keyring/` handlers | `keyrings` |
| `sync` | `sync/{service,repository,domain}` | `sync/web/v1` | `sync/` handlers | `records`, `storage_usage` |
| `upload` | `upload/{service,domain}` | `upload/web/v1` | none | none (R2 only) |


`common` (in each module and in grind-common) is shared plumbing, never business logic. Partner connections are a
separate service, `grind-partner` (feature `connection`), because they change with third parties' APIs, fail when
they fail, and hold their secrets.

Boundaries, checked by `ArchitectureTest` in core, api and worker (rules in grind-core's
`support/ArchitectureRules`):
- A feature's tables are reached only through its own repositories. No foreign keys or joins across features.
- Other features use it only through its **service** and **domain** types (today: keyring, sync and upload call
  `AccountService.ensureActive`).
- Controllers and handlers use only their own feature, through services; never repositories, outbound clients,
  storage or transactions.
- `core.common` never depends on a feature; features never depend on each other in a cycle.
- grind-core never uses the generated API types; each feature's `web.v1` mapper converts.
- Side effects in another feature go through events where they don't have to be immediate (account deletion is one
  event, one handler per feature). That keeps a later split to "move the folder, turn the event into Pub/Sub".

Split a feature out only when it needs to scale or deploy on its own, or another team owns it.

## Health and deploy checks
- `/livez` and `/readyz` on every service's main port for Cloud Run probes and uptime checks (readiness includes the
  database where there is one).
- `GET /internal/status` on every service: name, version, build time, profile, uptime and each health check, 503 when
  anything is down. Only the `grind-ops` account may read it; after a deploy run `deploy/status.sh <env>`.
- Settings every service shares live once, in `grind/defaults/*.yaml` inside grind-common and grind-core, loaded
  with the lowest precedence. A service's `application.yaml` holds only what is its own.

## Scheduled jobs
Cloud Scheduler calls `POST /internal/jobs/<name>` on grind-worker with its own Google identity (the worker accepts
it only there). Each job lives in its feature's worker package, calls grind-core services one user at a time, is safe
to repeat, and stops at `grind.worker.jobs.time-budget`; the next run carries on.

| Job | When (UTC) | Does |
|---|---|---|
| `account-deletion-sweep` | hourly | Publishes again every deletion still incomplete after 24 h (e.g. dead-lettered) |
| `orphan-blob-sweep` | daily 03:30 | Deletes blobs older than 14 days that no record points to (never-committed uploads) |
| `storage-usage-reconcile` | Sunday 04:00 | Recomputes each user's stored bytes from their records and fixes drift |

Cross-user lookups (which users have stuck deletions or stored data) go only through SECURITY DEFINER functions that
return user ids (migrations V3, V4); everything else still runs per user with row-level security. Never add an
age-based R2 lifecycle rule: it would delete committed blobs too.

## Add a partner
Partners that follow standard OAuth (token endpoint, client secret in the form body) need no code:
1. Register GRIND's app with the partner (redirect URI `com.grindandtrain.app:/oauth/<partner>`).
2. Add `grind.partners.providers.<partner>` to `deploy/config/<env>/grind-partner.yaml` and a secret
   `<partner>-client-secret` to `deploy/terraform/modules/services` (partner_secrets).
3. Add the secret's value with `gcloud secrets versions add`, then `terraform apply`.
Only partners the phone can fetch from directly are accepted; one that pushes data to a server would break the
zero-knowledge rule and needs a design review first.

## Add a feature (playbook)
1. **Contract first:** add the endpoints and schemas to `grind-contract/src/main/openapi/v1/grind-api-v1.yaml`;
   new events go in `grind-contract/.../contract/event/<feature>/` plus an `EventType` constant.
2. **Migration:** `grind-core/src/main/resources/db/migration/V<n>__<feature>_<what>.sql`. Every table: `user_id`,
   row-level security enabled and forced, an owner policy on `app.user_id`, grants to `${app_role}`. Ciphertext,
   sizes and ids only, never plaintext health data.
3. **grind-core** `<feature>/`: `domain` (records), `repository` (JdbcClient, used inside
   `UserTransactionTemplate`), `service` (`@Service`, the feature's public API; throws `GrindException` with a new
   `BusinessErrorCode`).
4. **grind-api** `<feature>/web/v1/`: one or more controllers implementing the generated interfaces, plus one mapper
   between contract types and domain types. The signed-in user comes from `CurrentUser.id()`.
5. **grind-worker** `<feature>/`: an `EventHandler` per event the feature reacts to (idempotent; use `HandlerOrder`
   when order matters).
6. **Tests:** service logic on the embedded database (`GrindTestDatabase`, row-level security on), a WebMvc test for
   new error cases, and `./mvnw verify` (includes the architecture rules).

## Rules
- Every request: JWT checked, `app.user_id` set per transaction for row-level security, sizes and formats validated
  (content is ciphertext).
- Errors: throw `GrindException` with an `ErrorCode`; never build an error message from request data.
- Logging: never log bodies, tokens, health values or real user ids. Ids go in log fields (`LogFields`, `MdcScope`);
  users appear only as `UserId.pseudonym()`.
- Requests from the app are strict JSON (unknown or duplicated fields are a 400); outbound clients and the worker
  stay lenient, because Google and partners add fields. Every response carries `no-store`, `nosniff`, no referrer and
  `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, including early filter rejections.
- A new API version gets its own contract file and `web.v2` packages, side by side with v1.
