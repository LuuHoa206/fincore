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

## Documentation

- [Product scope](docs/PRODUCT_SCOPE.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Data model](docs/DATA_MODEL.md)
- [Financial rules](docs/FINANCIAL_RULES.md)
- [API contract](docs/API.md)
- [Roadmap](docs/ROADMAP.md)
- [Git workflow](docs/GIT_WORKFLOW.md)

## Current status

The identity, wallet, transaction, category, money-jar, and monthly-budget milestones are complete. The
API supports account registration, login, rotating refresh tokens, owner-scoped
wallet management, system and user-owned categories, virtual money-jar
allocation with concurrency protection, monthly category budgets derived from
posted expenses, and immutable income/expense recording backed by balanced
ledger entries and idempotency keys. The next milestone is saving goals.

Backend modules follow a documented three-layer MVC convention: controllers
handle HTTP and DTO validation, services own business rules and transactions,
and repositories isolate data access. See [Architecture](docs/ARCHITECTURE.md).
