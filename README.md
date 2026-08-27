# FinCore

FinCore is a smart personal-finance platform for tracking income and expenses,
allocating money into virtual jars, managing budgets, and building savings
goals. The project is designed as a portfolio-grade Java backend system with a
strong focus on financial correctness, traceability, testing, and deployment.

## Product principles

- Wallets represent real money; jars represent how that money is allocated.
- Internal transfers are not income or expenses.
- Posted financial transactions are immutable and corrected by reversal.
- Every balance-changing operation is atomic and auditable.
- Money uses `BigDecimal` and an explicit ISO currency.
- AI may recommend, but never changes financial data without confirmation.

## Technology

- Backend: Java 21, Spring Boot 4.1, Spring Security, JPA, Flyway, PostgreSQL
- Frontend: React 19, TypeScript, Vite, TanStack Query, React Hook Form, Zod
- Quality: JUnit, Testcontainers, Flyway migrations, GitHub Actions
- Operations roadmap: Docker, Redis, messaging, Actuator, OpenTelemetry, AWS

## Repository structure

```text
fincore/
|-- backend/              Spring Boot API
|-- frontend/             React web application
|-- docs/                 Product and engineering decisions
|-- docker-compose.yml    Local PostgreSQL environment
|-- docker-compose.production.yml
|                         Full production stack: PostgreSQL, API, Nginx web gateway
`-- .env.example          Environment variable template
```

## Local setup

Requirements:

- JDK 21
- Node.js 22 or newer
- PostgreSQL 16 or newer, or Docker Desktop

1. Copy `.env.example` to `.env` and replace local-only passwords.
2. Start PostgreSQL using the installed local service or `docker compose up -d`.
3. Create database/user values matching `.env` when using local PostgreSQL.
4. Start the backend:

```powershell
cd backend
$env:JAVA_HOME = 'C:\Users\Admin\.jdks\ms-21.0.11'
.\mvnw.cmd spring-boot:run
```

5. Start the frontend:

```powershell
cd frontend
npm install
npm run dev
```

The frontend runs at `http://localhost:5173`; the backend runs at
`http://localhost:8080`.

## Production deployment with Docker

The production Compose file starts PostgreSQL, the private Spring Boot API, and
the React application behind Nginx. Only the web gateway publishes a host port;
the database and API remain inside the Docker network. Nginx serves the SPA,
falls back to `index.html` for client routes, and proxies `/api/v1` to the API.

On the deployment host:

```sh
cp .env.production.example .env.production
# Edit .env.production and replace every placeholder, especially passwords and JWT_SECRET.
docker compose --env-file .env.production -f docker-compose.production.yml up -d --build
docker compose --env-file .env.production -f docker-compose.production.yml ps
curl -fsS http://localhost:8080/healthz
curl -fsS http://localhost:8080/api/v1/actuator/health/readiness
```

The two `curl` commands assume the default `APP_PORT=8080`; replace the port if
you choose a different host port.

For a public deployment, put a TLS-capable reverse proxy or managed load
balancer in front of the web gateway, point it to `APP_PORT`, and set
`CORS_ALLOWED_ORIGINS` to the exact public `https://` origin. Do not publish the
PostgreSQL or backend service ports, commit `.env.production`, or reuse the
local JWT secret in production. `VITE_API_BASE_URL` is a build-time setting and
defaults to the same-origin `/api/v1` proxy path.

## Verification

Run the fast backend suite and frontend checks before opening a pull request:

```powershell
cd backend
.\mvnw.cmd test

cd ..\frontend
npm run lint
npm run build
```

With Docker Desktop available, run the PostgreSQL/Flyway integration suite as
well. It boots a disposable PostgreSQL container and verifies the current
schema, including split-bill tables:

```powershell
cd backend
$env:RUN_INTEGRATION_TESTS = 'true'
.\mvnw.cmd test
Remove-Item Env:RUN_INTEGRATION_TESTS
```

## Documentation

- [Product scope](docs/PRODUCT_SCOPE.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Data model](docs/DATA_MODEL.md)
- [Financial rules](docs/FINANCIAL_RULES.md)
- [API contract](docs/API.md)
- [Operations guide](docs/OPERATIONS.md)
- [Performance testing](docs/PERFORMANCE_TESTING.md)
- [Roadmap](docs/ROADMAP.md)
- [Git workflow](docs/GIT_WORKFLOW.md)

## Current status

The identity, wallet, transaction, category, money-jar, monthly-budget, saving-goal, and reporting milestones are complete. The
API supports account registration, login, rotating refresh tokens, owner-scoped
wallet management, system and user-owned categories, virtual money-jar
allocation with concurrency protection, monthly category budgets derived from
posted expenses, saving goals derived from jar allocations, and immutable
income/expense recording backed by balanced ledger entries and idempotency keys.
The dashboard is server-composed from these modules and transaction history is
filtered and paginated by the API instead of a client-side sample.

Backend modules follow a documented three-layer MVC convention: controllers
handle HTTP and DTO validation, services own business rules and transactions,
and repositories isolate data access. See [Architecture](docs/ARCHITECTURE.md).

The observability foundation now provides health/readiness probes, authenticated
metrics and correlation IDs on every HTTP response. See the
[Operations guide](docs/OPERATIONS.md) for safe monitoring and incident response.

The first AI-assist baseline recommends up to three visible transaction
categories from the description and explains the matching signals. It is a
deterministic, reviewable rule layer; users always select the category and the
assistant never writes financial data on its own.

The dashboard also provides transparent monthly observations from posted cash
flow, budget status, and saving-goal progress. These observations are read-only
and explainable; they help users review recorded data rather than predict or
alter financial outcomes.

The dashboard can also flag expenses worth reviewing with a transparent,
read-only rule. A posted expense is shown only when at least three earlier
posted expenses exist in the same category and currency during the prior three
months, and the current amount is at least 2.5 times that historical average.
This is a review prompt, not a fraud decision, prediction, or automatic change.

Enabled recurring rules also power a read-only 7-to-90-day cash-flow preview.
It presents projected income, expense, and net totals per currency alongside
the nearest planned occurrences, without creating transactions or modifying a
schedule.

Transaction history can be exported as a Vietnamese Excel-friendly CSV using
the active server-side filters. Export remains owner-scoped and read-only, uses
the user's time zone for timestamps, and rejects requests above 10,000 rows
instead of silently producing a partial file.
