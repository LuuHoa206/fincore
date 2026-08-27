# Performance testing

FinCore keeps a repeatable, read-only baseline under
`performance/k6/read-journey.js`. It is intentionally limited to a normal
authenticated read journey:

1. Sign in once in `setup` with a dedicated test account.
2. Read the current profile, dashboard, and first transaction-history page.
3. Ramp from zero to five, then ten virtual users before ramping down.

The script never calls an endpoint that creates, reverses, transfers, or
settles money. Do not use a personal account or production credentials. Use a
dedicated account in staging or a disposable local environment.

## Prerequisites

- A running FinCore API.
- [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/) installed locally,
  or Docker Desktop with the `grafana/k6` image available.
- A pre-created test user with at least one wallet. The three read endpoints do
  not require sample transactions, but a realistic staging dataset gives a more
  useful baseline.

## Run locally on Windows PowerShell

Start the API, then run:

```powershell
$env:BASE_URL = 'http://localhost:8080/api/v1'
$env:TEST_EMAIL = 'performance-test@example.com'
$env:TEST_PASSWORD = 'replace-with-a-test-password'
$env:TEST_PERIOD = '2026-08' # optional
k6 run .\performance\k6\read-journey.js
```

`BASE_URL` must include `/api/v1` and must not include a trailing slash. The
script uses the current user time zone when `TEST_PERIOD` is omitted.

To retain machine-readable output locally without committing it:

```powershell
k6 run --summary-export .\performance\results\read-baseline.json .\performance\k6\read-journey.js
```

Files inside `performance/results/` are ignored by Git. Record the result
summary, application version, environment size, test date, and any relevant
database data volume in a ticket or release note instead of committing test
credentials or large raw artifacts.

## Docker alternative

When Docker Desktop is available, pass the same variables into k6:

```powershell
docker run --rm -i `
  -e BASE_URL='http://host.docker.internal:8080/api/v1' `
  -e TEST_EMAIL='performance-test@example.com' `
  -e TEST_PASSWORD='replace-with-a-test-password' `
  grafana/k6 run - < .\performance\k6\read-journey.js
```

On a Linux deployment host, replace `host.docker.internal` with the reachable
API host name or execute k6 from the same Docker network.

## Acceptance criteria and interpretation

The baseline fails when one percent or more requests fail, fewer than 99% of
checks pass, or the overall p95 request duration reaches 800 ms. These are
initial guardrails, not a production capacity claim. Repeat the run after a
warm-up, compare only like-for-like environments, and inspect the backend
correlation IDs, database metrics, and container CPU/memory before changing a
threshold.

The authentication request happens once during `setup`; do not raise virtual
users by repeatedly logging in because the application intentionally rate-limits
authentication endpoints.
