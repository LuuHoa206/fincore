# Operations Guide

## Health and monitoring endpoints

The backend exposes a deliberately small Actuator surface:

| Endpoint | Access | Purpose |
| --- | --- | --- |
| `GET /actuator/health` | Public | Liveness/readiness check for a load balancer or platform. |
| `GET /actuator/health/liveness` | Public | Confirms that the process is alive. |
| `GET /actuator/health/readiness` | Public | Confirms that the application is ready to serve traffic. |
| `GET /actuator/info` | Authenticated | Application information for operators. |
| `GET /actuator/metrics` | Authenticated | Lists available Micrometer metrics. |
| `GET /actuator/metrics/{name}` | Authenticated | Reads one metric, optionally filtered by tags. |

Health details are shown only to authenticated callers. Do not make `metrics`,
`env`, `configprops`, `heapdump`, or `loggers` public: they can expose runtime
details that are not intended for end users.

## Correlation IDs and request logs

Every request receives an `X-Correlation-Id` response header. A client can send
its own value when it contains 8 to 100 safe characters (`A-Z`, `a-z`, digits,
`.`, `_`, `-`); otherwise FinCore generates a UUID.

The identifier is present in the logging context for the lifetime of the
request and is removed afterward so it cannot leak into a later request handled
by the same thread. Request logs contain only:

- HTTP method;
- request path without query parameters;
- response status;
- duration in milliseconds.

They never contain authorization headers, refresh tokens, request bodies,
financial descriptions, or account values. Disable these logs temporarily with
`REQUEST_LOGGING_ENABLED=false`; keep correlation IDs enabled for support and
incident investigation.

## Authentication rate limiting

`POST /api/v1/auth/register`, `/login` and `/refresh` are limited by source IP
and endpoint. The default is 10 attempts in one minute. When the limit is
reached, the backend returns `429`, `AUTH_RATE_LIMITED` and `Retry-After`; it
does not record the email address, IP address, token or request body in logs.

The limiter is intentionally in-memory and therefore applies per application
instance. It is suitable as a basic guard for local and single-instance
deployments. Before deploying multiple backend instances, move this control to
an API gateway or a shared Redis-backed limiter. Configure it with
`AUTH_RATE_LIMIT_MAX_ATTEMPTS` and `AUTH_RATE_LIMIT_WINDOW` (for example
`PT1M`). The filter uses the direct peer address rather than a client supplied
forwarded header so an arbitrary caller cannot spoof its identity.

## First response checklist

1. Ask for the `X-Correlation-Id`, approximate request time, API path and user-visible error.
2. Search the backend log by the correlation ID. Check status and duration before inspecting deeper application errors.
3. Confirm `/actuator/health/readiness` is `UP`. If it is not, inspect the database connection and Flyway state.
4. Never request a JWT, refresh token, database password, or raw financial data through a support channel.
5. For an inconsistent balance, preserve the transaction ID and audit trail; use reversal, not direct database edits.

## Verification before deployment

```powershell
cd backend
.\mvnw.cmd test

cd ..\frontend
npm run lint
npm run build
```

Khi Docker Desktop có sẵn, kiểm tra thêm hợp đồng Compose trước khi bàn giao
một bản release. Lệnh này chỉ render cấu hình, không khởi động hay thay đổi dữ
liệu:

```powershell
docker compose --env-file .env.production -f docker-compose.production.yml config --quiet
```

When Docker Desktop is available, also execute the PostgreSQL/Flyway integration
suite documented in the root README. GitHub Actions cũng chạy hai test này trên
PostgreSQL Testcontainers ở mỗi pull request vào `dev` hoặc `main`; local chỉ
cần chạy khi thay đổi migration, mapping JPA hoặc trước một release quan trọng.

## Recurring auto-record recovery

The recurring scheduler processes each due rule independently. A failure for one
rule is logged as `recurring_rule_auto_record_failed` with its rule ID and does
not prevent later due rules from being recorded. The failed rule remains due and
is retried on the next scheduler cycle after its underlying issue is resolved.

When this log entry appears, investigate the referenced rule, wallet, category,
and recent audit records. Do not create a compensating transaction until the
execution and audit history confirm that no record was created for that rule.

## Read-only performance baseline

Before changing infrastructure sizing or promoting a release, run the
repeatable k6 read journey with a dedicated staging account. It authenticates
once and reads the current profile, dashboard, and transaction history without
creating or modifying financial data. The script, execution commands, and p95
guardrails are documented in [Performance testing](PERFORMANCE_TESTING.md).

## Production Docker handover

The repository contains `docker-compose.production.yml` for a small,
single-host production deployment:

- `postgres` stores data in the named `fincore-postgres-data` volume and is not
  published to the host network;
- `backend` runs the Spring Boot API with the `production` profile and is only
  reachable by the Nginx web service;
- `web` publishes `APP_PORT`, serves the React SPA, redirects client-side routes
  to `index.html`, and proxies `/api/*` to the private backend.

### Deploy or update

1. Fetch a reviewed commit on the deployment host.
2. Copy `.env.production.example` to `.env.production`. Keep the real file on
   the host only and use a unique PostgreSQL password and JWT secret of at least
   32 random bytes.
3. Set `CORS_ALLOWED_ORIGINS` to the exact public HTTPS URL, without a trailing
   slash. Keep `VITE_API_BASE_URL=/api/v1` unless the web application is hosted
   separately.
4. Start or update the stack:

```sh
docker compose --env-file .env.production -f docker-compose.production.yml up -d --build
docker compose --env-file .env.production -f docker-compose.production.yml ps
docker compose --env-file .env.production -f docker-compose.production.yml logs --tail=100 backend
```

5. Confirm `http://localhost:<APP_PORT>/healthz` and
   `http://localhost:<APP_PORT>/api/v1/actuator/health/readiness` return success
   before exposing the new version through the public proxy.

Terminate TLS at a managed load balancer or reverse proxy in front of the `web`
service. Do not expose port 5432 or backend port 8080 directly to the Internet.

### Database backup and rollback

Back up the database before applying a new version or Flyway migration:

```sh
docker compose --env-file .env.production -f docker-compose.production.yml exec -T postgres \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > "fincore-$(date +%F-%H%M).sql"
```

If an application update fails after deployment, return the code checkout to
the previous known-good commit and rerun `docker compose ... up -d --build`.
Never restore a database backup merely to roll back application code; restore
only after confirming the migration and data recovery plan, because financial
transactions must remain auditable.
