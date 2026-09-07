# Performance baseline

## Baseline 2026-09-07

This baseline validates a local production-shaped stack through Nginx. It is a
repeatable engineering reference, not a claim about public cloud capacity.

### Environment

| Item | Value |
| --- | --- |
| Application commit | `64bf537d129445d85a13a1867778618c5befb106` on `dev` |
| Stack | Nginx web gateway, Spring Boot backend, PostgreSQL in Docker Compose |
| Database | PostgreSQL 18.6 (`postgres:18-alpine`) |
| Load generator | k6 2.2.0 in Docker |
| Docker Engine | 29.7.2, 7.36 GiB available, no explicit Compose resource limits |
| Host | AMD Ryzen 7 6800H, 15.19 GiB RAM, Windows with Docker Desktop/WSL2 |
| Network path | k6 container to `host.docker.internal:18080`, then Nginx to backend |
| Test account data | One wallet and one transaction before the measured read run |

The read journey was warmed once with the same stages before the measured run.
Both measured runs passed all configured thresholds.

### Authenticated read journey

The test ramped from zero to 5 and then 10 virtual users over 75 seconds. Each
iteration requested the current user, dashboard, and first transaction page in
parallel.

| Metric | Result |
| --- | ---: |
| HTTP requests | 1,276 |
| Completed iterations | 425 |
| Median (p50) | 13.09 ms |
| p95 | 22.24 ms |
| p99 | 32.08 ms |
| Maximum | 123.72 ms |
| Request failure rate | 0.00% |
| Checks passed | 1,277 / 1,277 |

Acceptance thresholds: p95 below 800 ms, p99 below 1,500 ms, request failures
below 1%, and checks above 99%.

### Concurrent idempotency retry

The test ramped to 12 virtual users for 30 seconds. All users repeatedly sent
the same income payload and `Idempotency-Key` to exercise the financial write
lock and replay path.

| Metric | Result |
| --- | ---: |
| HTTP requests | 592, including setup login |
| Concurrent write iterations | 591 |
| Median (p50) | 10.36 ms |
| p95 | 14.57 ms |
| p99 | 23.72 ms |
| Maximum | 121.10 ms |
| Request failure rate | 0.00% |
| Checks passed | 1,184 / 1,184 |

Acceptance thresholds: p95 below 1,200 ms, p99 below 2,000 ms, request failures
below 1%, and checks above 99%.

### Correctness proof after the write run

API state before and after the run showed exactly one additional transaction
and a wallet balance increase of exactly VND 1. A direct PostgreSQL query then
confirmed:

| Invariant | Result |
| --- | ---: |
| Distinct matching financial transactions | 1 |
| Ledger entries for that transaction | 2 |
| Sum of signed ledger entries | 0.0000 |
| Wallet balance after the run | 1,000,001.0000 VND |

This demonstrates that 591 successful HTTP retries did not duplicate the
financial posting. The PostgreSQL concurrency integration tests remain the
primary automated regression proof because they recreate a clean database and
assert transaction, ledger, and balance invariants in CI.
