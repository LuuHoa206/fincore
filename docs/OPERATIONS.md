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

When Docker Desktop is available, also execute the PostgreSQL/Flyway integration
suite documented in the root README.
