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

The separate `performance/k6/financial-write-idempotency.js` script is a
controlled write benchmark. All virtual users send the same `Idempotency-Key`,
so a run records exactly one VND 1 income while exercising simultaneous retry
handling. Run it only against a disposable local or staging wallet and income
category. It measures a safety-critical retry path, not normal write capacity.

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

The raw summary includes `setup_data`, which can contain the short-lived access
token used by the test account. Treat it as sensitive local output even though
it expires and is excluded from Git. The reviewed baseline is kept in
[Performance baseline](PERFORMANCE_BASELINE.md) without credentials or tokens.

## Run the financial idempotency benchmark

Create a dedicated test wallet and an income category, then set their IDs. The
script writes a single VND 1 test income per run because every request shares
one newly generated idempotency key:

```powershell
$env:BASE_URL = 'http://localhost:8080/api/v1'
$env:TEST_EMAIL = 'performance-test@example.com'
$env:TEST_PASSWORD = 'replace-with-a-test-password'
$env:TEST_WALLET_ID = 'replace-with-test-wallet-uuid'
$env:TEST_INCOME_CATEGORY_ID = 'replace-with-test-income-category-uuid'
k6 run --summary-export .\performance\results\idempotency-baseline.json .\performance\k6\financial-write-idempotency.js
```

Before recording a result, confirm in transaction history that the run created
one new transaction only. The PostgreSQL integration suite is the stronger
correctness proof because it asserts the resulting ledger and balance directly.

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

The read baseline fails when one percent or more requests fail, fewer than 99%
of checks pass, the overall p95 request duration reaches 800 ms, or p99 reaches
1.5 seconds. The financial idempotency benchmark uses a p95 of 1.2 seconds and
p99 of 2 seconds because it exercises database locks and a write commit. These
are initial guardrails, not a production capacity claim. Repeat the run after a
warm-up, compare only like-for-like environments, and inspect the backend
correlation IDs, database metrics, and container CPU/memory before changing a
threshold.

Record p50, p95, p99, request failure rate, application commit, machine or
container resources, PostgreSQL version, dataset size, and test date alongside
each baseline. A result without this environment information is not comparable
to a later run.

The authentication request happens once during `setup`; do not raise virtual
users by repeatedly logging in because the application intentionally rate-limits
authentication endpoints.
