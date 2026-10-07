# GRIND Platform

The native iOS + Android app for GRIND (train · recover · repeat) and its zero-knowledge backend.

| Folder | What |
|---|---|
| `grind-app/` | React + TypeScript + Capacitor 8 app: UI, on-device business logic, encrypted SQLite, native plugins |
| `grind-service/` | Java 21 / Spring Boot 4.1 backend (Maven multi-module) on Cloud Run, Pub/Sub, Neon Postgres, R2; settings and Terraform in `grind-service/deploy/` |

Design: `docs/grind-architecture.md` (system, services, security, cost) and `docs/grind-database.md` (schema,
partitioning, retention). Folder guides: `grind-app/README.md`, `grind-service/README.md`,
`grind-service/deploy/README.md`.

Keep this repo out of iCloud-synced folders (`~/Documents`, `~/Desktop`): it lives in `~/Developer/Grind-platform`.
