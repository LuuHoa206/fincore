# FinCore API contract

Base path: `/api/v1`

All request and response bodies use JSON unless an endpoint explicitly returns
a downloadable file. Protected endpoints require:

```http
Authorization: Bearer <access-token>
```

Access tokens expire after 15 minutes by default. Refresh tokens are opaque,
stored as SHA-256 hashes, rotated after use, and expire after 30 days by
default. The durations are configurable through environment variables.

## Authentication

Public authentication endpoints are protected by a per-instance rate limit.
`POST /auth/register`, `POST /auth/login` and `POST /auth/refresh` allow 10
attempts per source IP and endpoint each minute by default. A rejected request
returns `429 Too Many Requests`, the `AUTH_RATE_LIMITED` error code and a
`Retry-After` header.

### Register

`POST /auth/register`

```json
{
  "email": "hoa@example.com",
  "password": "StrongPass123",
  "displayName": "Luu Hoa",
  "preferredCurrency": "VND",
  "timeZone": "Asia/Ho_Chi_Minh"
}
```

Returns `201 Created` with an access token, refresh token, expiration time,
and user profile.

### Login

`POST /auth/login`

```json
{
  "email": "hoa@example.com",
  "password": "StrongPass123"
}
```

### Refresh an access token

`POST /auth/refresh`

```json
{
  "refreshToken": "<opaque-refresh-token>"
}
```

The submitted refresh token is revoked and replaced. A refresh token must not
be reused after a successful refresh.

### Logout

`POST /auth/logout`

Use the same request body as refresh. The operation is idempotent and returns
`204 No Content`, including when the token has already been revoked.

## Current user

`GET /users/me`

Returns the profile that belongs to the access-token subject.

## Wallets

Wallet IDs never determine access by themselves. Every query is scoped by both
the authenticated user ID and wallet ID.

### List wallets

`GET /wallets`

Only active wallets owned by the authenticated user are returned.

### Create a wallet

`POST /wallets`

```json
{
  "name": "Main bank account",
  "walletType": "BANK",
  "currency": "VND",
  "allowNegative": false
}
```

Supported wallet types: `CASH`, `BANK`, `E_WALLET`, `CREDIT`, `SAVINGS`.
New wallets always start at zero. Initial money must later be recorded as an
audited adjustment transaction instead of being inserted as an unexplained
balance.

### Get a wallet

`GET /wallets/{walletId}`

### Update wallet metadata

`PATCH /wallets/{walletId}`

```json
{
  "name": "Daily spending",
  "walletType": "E_WALLET",
  "allowNegative": false
}
```

Currency and balance are not editable through this endpoint.

### Archive a wallet

`DELETE /wallets/{walletId}`

Returns `204 No Content`. The wallet is archived, not physically deleted, so
future transaction history can continue to reference it.

## Categories

### Suggest a category from a transaction description

`GET /categories/suggestions?type=EXPENSE&description=An%20com%20trua`

Returns up to three visible categories whose names or baseline rule keywords
match the provided description. Each result includes a human-readable reason.
This is an explainable assistant only: it does not create a category, change a
form value, or write a transaction. The client must let the user choose a
suggestion explicitly.

Category responses include system defaults (`systemCategory: true`) and the
authenticated user's own active categories. System defaults are read-only;
users can create, update, and archive only their own categories.

### List available categories

`GET /categories?type=EXPENSE`

The optional `type` is `INCOME` or `EXPENSE`. Without it, both types are
returned.

### Create a category

`POST /categories`

```json
{
  "name": "Pet care",
  "categoryType": "EXPENSE",
  "icon": "paw-print",
  "color": "#7C3AED"
}
```

Names are unique per owner and category type, ignoring letter case.

### Update or archive an owned category

`PATCH /categories/{categoryId}` and `DELETE /categories/{categoryId}`

Archive returns `204 No Content`. Archived categories cannot be selected for
new transactions, while existing transaction history remains intact.

## Money jars

Money jars are envelopes for a purpose such as emergency savings or travel.
They allocate already-owned wallet balance without changing the wallet balance
itself. All allocation limits are checked server-side in a database transaction.

### List money jars

`GET /jars`

Only active jars owned by the authenticated user are returned.

### Create a money jar

`POST /jars`

```json
{
  "name": "Emergency fund",
  "currency": "VND",
  "spendingLimit": 5000000,
  "color": "#0F8F72",
  "icon": "shield-check",
  "allowNegative": false
}
```

The name is unique for each user. The jar starts with an allocated balance of
zero; the amount cannot be sent in the create request.

### Update or archive a money jar

`PATCH /jars/{jarId}` and `DELETE /jars/{jarId}`

A jar must be released to zero before it can be archived. Currency and
allocated balance are immutable through the edit endpoint.

### Allocate money to a jar

`POST /jars/{jarId}/allocate`

```json
{
  "amount": 500000
}
```

The API locks the user's active wallets and jars before it calculates the
remaining allocatable balance for that currency. It returns `409` if the
request would allocate more than the real wallet balance that remains
unassigned.

### Release money from a jar

`POST /jars/{jarId}/release`

```json
{
  "amount": 200000
}
```

The amount becomes available for another jar. The release is blocked if it
would make a non-negative jar balance fall below zero.

## Allocation rules

An allocation rule distributes a future income into money jars of the same
currency. A user can keep multiple drafts, but only one rule per currency may
be enabled. The percentages may be less than 100; the remainder stays available
in the wallet.

### List rules

`GET /allocation-rules`

### Create a rule

`POST /allocation-rules`

```json
{
  "name": "Monthly salary split",
  "currency": "VND",
  "enabled": true,
  "items": [
    { "jarId": "<emergency-jar-id>", "percentage": 20 },
    { "jarId": "<travel-jar-id>", "percentage": 10 }
  ]
}
```

All jars must belong to the current user and use the specified currency. The
combined percentage cannot exceed 100. Use `PATCH /allocation-rules/{ruleId}`
to change the name, enabled state, or items, and `DELETE /allocation-rules/{ruleId}`
to remove a rule.

### Preview an income allocation

`GET /allocation-rules/preview?walletId=<wallet-id>&amount=15000000`

The response shows the enabled rule for that wallet currency, per-jar amounts,
the total allocated amount, and the amount left unassigned. It does not change
data.

### Apply a rule when recording income

Include `applyAllocationRule: true` in `POST /transactions` for an `INCOME`.
The API rejects the request with `409` when there is no enabled rule for the
wallet currency. The financial transaction, balanced ledger entries, jar
balances, and `jar_movements` either all commit or all roll back together.

## Monthly budgets

A budget belongs to one expense category and one calendar month. Its actual
spending is derived from posted expense transactions, never accepted from the
client as an editable amount.

### List a month

`GET /budgets?period=2026-08`

The optional `period` uses `YYYY-MM`; if omitted, the API uses the authenticated
user's configured time zone to select the current month.

Each response includes `limitAmount`, `spentAmount`, `remainingAmount`,
`usagePercentage`, and status `ON_TRACK`, `WARNING`, or `EXCEEDED`.

### Create a budget

`POST /budgets`

```json
{
  "categoryId": "09d20e44-7963-48c4-a78f-6e970d87d2af",
  "periodStart": "2026-08-01",
  "limitAmount": 3000000,
  "currency": "VND",
  "warningThreshold": 80
}
```

`periodStart` must be the first day of a month. Only an available `EXPENSE`
category can be used. An active budget is unique per user, category, and month.

### Update or archive a budget

`PATCH /budgets/{budgetId}` and `DELETE /budgets/{budgetId}`

Only `limitAmount` and `warningThreshold` are editable. Archive returns `204 No
Content`; it preserves historical transactions and allows a new plan to be
created for the same category and month later.

## Saving goals

A saving goal is attached to one money jar. `currentAmount` is the jar's
allocated balance, so the client cannot independently edit a second balance.

### List saving goals

`GET /saving-goals`

The response includes progress, remaining amount, and an optional monthly
contribution suggestion when a target date is provided.

### Create a saving goal

`POST /saving-goals`

```json
{
  "jarId": "a49d66c4-8765-4ac2-9ec3-9c4a21bbd73b",
  "name": "Emergency fund",
  "targetAmount": 30000000,
  "targetDate": "2026-12-31"
}
```

An active jar can have at most one open goal. If the jar allocation reaches the
target, the API reports `COMPLETED` automatically.

### Update or change goal status

`PATCH /saving-goals/{goalId}` updates the name, target amount, or target date.

`POST /saving-goals/{goalId}/status` accepts `ACTIVE`, `PAUSED`, or `CANCELLED`.
`COMPLETED` is calculated from the linked jar and cannot be manually selected.

## Transactions

### List transactions

`GET /transactions?page=0&size=10&transactionType=EXPENSE&query=coffee`

All query parameters are optional. `page` is zero-based and `size` must be from
1 to 50. `transactionType` can be any supported transaction type; `query`
searches the description, notes, and category name. `from` and `to` accept ISO
8601 timestamps, with `to` treated as exclusive.

The response is a page object containing `content`, `page`, `size`,
`totalElements`, and `totalPages`. Queries are owner-scoped and run at the
database layer before the response is created.

### Export transaction history as CSV

`GET /transactions/export?transactionType=EXPENSE&query=coffee&from=2026-08-01T00:00:00Z&to=2026-09-01T00:00:00Z`

Returns a `text/csv` attachment containing every transaction matching the same
owner-scoped filters as the list endpoint, not only the page currently shown in
the client. The file is UTF-8 with a BOM so Vietnamese text opens correctly in
spreadsheet applications. Timestamps use the authenticated user's configured
time zone. To protect the API from an unexpectedly large download, exports are
limited to 10,000 rows; a wider request returns `409` with
`TRANSACTION_EXPORT_LIMIT_EXCEEDED` and must be narrowed first.

### Record income or expense

`POST /transactions`

```http
Idempotency-Key: 4bf4c8cc-8c2d-4604-9262-8e7f64a967e1
```

```json
{
  "walletId": "a49d66c4-8765-4ac2-9ec3-9c4a21bbd73b",
  "categoryId": "09d20e44-7963-48c4-a78f-6e970d87d2af",
  "transactionType": "EXPENSE",
  "amount": 65000,
  "description": "Lunch",
  "notes": "Team meeting",
  "occurredAt": "2026-08-26T05:30:00Z"
}
```

Only `INCOME` and `EXPENSE` are currently accepted. The category must be
visible to the user and have the matching type. Reusing the same
`Idempotency-Key` returns the original recorded transaction instead of posting
another balance change.

### Transfer between wallets

`POST /transactions/transfers`

```http
Idempotency-Key: b15a2272-3d91-4d5b-851d-6737c0d72a2c
```

```json
{
  "sourceWalletId": "a49d66c4-8765-4ac2-9ec3-9c4a21bbd73b",
  "destinationWalletId": "2ee046e8-faa7-438d-b01a-3dcefc7712bf",
  "amount": 500000,
  "description": "Move cash to bank",
  "notes": "Weekend deposit",
  "occurredAt": "2026-08-27T03:30:00Z"
}
```

The two wallets must be different, active, owned by the authenticated user,
and use the same currency. The service locks both rows in a stable database
order, checks the source balance, then records one `TRANSFER` and two opposite
wallet ledger entries in one database transaction. It is therefore neither
income nor expense and does not change total assets. Reversal restores both
wallet balances through a new counter-transaction; the original transfer stays
in the audit trail.

### Reverse a transaction

`POST /transactions/{transactionId}/reverse`

The original transaction is marked reversed and a balanced counter-transaction
is added. The original record is never deleted.

## Recurring transaction rules

### List rules

`GET /recurring-rules`

Rules are ordered by their next scheduled time and include the selected wallet,
category, recurrence frequency, and whether automatic posting is enabled.

### Review upcoming recurring rules

`GET /recurring-rules/upcoming?limit=4`

Returns 1 to 10 enabled rules for the authenticated user, ordered by their next
scheduled time. It is a read-only dashboard helper: it can include a past-due
rule so the user can see that an occurrence still needs to be recorded. The
endpoint never posts a transaction or advances a rule schedule.

### Create or update a rule

`POST /recurring-rules` and `PATCH /recurring-rules/{ruleId}`

```json
{
  "name": "Monthly rent",
  "walletId": "a49d66c4-8765-4ac2-9ec3-9c4a21bbd73b",
  "categoryId": "09d20e44-7963-48c4-a78f-6e970d87d2af",
  "transactionType": "EXPENSE",
  "amount": 5000000,
  "description": "Rent payment",
  "frequency": "MONTHLY",
  "nextRunAt": "2026-09-01T01:00:00Z",
  "autoRecord": false,
  "enabled": true,
  "applyAllocationRule": false
}
```

Only `INCOME` and `EXPENSE` rules are accepted. The wallet, category, and rule
must belong to the authenticated user; the category type must match the rule
type. Automatic posting is opt-in, so a new rule is a reminder by default.

### Record a due occurrence or disable a rule

`POST /recurring-rules/{ruleId}/record` records exactly one occurrence when the
rule is due. `DELETE /recurring-rules/{ruleId}` disables the rule while keeping
its history and configuration for auditability.

The scheduler checks opted-in due rules every five minutes by default. Each
occurrence uses a deterministic idempotency key derived from the rule and its
scheduled timestamp. Concurrent scheduler runs or a retry therefore cannot post
the same occurrence twice. After recording, the next run advances to the first
future period; missed periods are not bulk-posted after downtime.

## Split bills and reimbursements

A split bill records one real expense first, then tracks only the money that
other people need to reimburse. It never creates a second artificial balance.
Each reimbursement is posted as a real `INCOME` transaction into the selected
wallet, in the same database transaction that updates the receivable status.

### List or view split bills

`GET /split-bills` and `GET /split-bills/{billId}`

Responses include the original expense transaction, the payer's own share,
each participant's balance, payment history, and calculated status:
`OPEN`, `PARTIALLY_SETTLED`, or `SETTLED`.

### Create a split bill

`POST /split-bills`

```http
Idempotency-Key: split-bill-2026-08-27-01
```

```json
{
  "walletId": "a49d66c4-8765-4ac2-9ec3-9c4a21bbd73b",
  "expenseCategoryId": "09d20e44-7963-48c4-a78f-6e970d87d2af",
  "name": "Dinner with the project team",
  "totalAmount": 900000,
  "payerShareAmount": 300000,
  "description": "Paid dinner for the team",
  "occurredAt": "2026-08-27T12:00:00Z",
  "participants": [
    { "name": "An", "owedAmount": 300000 },
    { "name": "Binh", "contact": "binh@example.com", "owedAmount": 300000 }
  ]
}
```

The payer share plus all participant shares must equal `totalAmount`. The API
posts exactly one `EXPENSE` transaction and uses its ID as the immutable link
to the split bill. `Idempotency-Key` is mandatory for this endpoint, so a
network retry returns the original split bill instead of charging the wallet
twice.

### Record a reimbursement

`POST /split-bills/{billId}/participants/{participantId}/payments`

```http
Idempotency-Key: split-payment-2026-08-27-01
```

```json
{
  "walletId": "a49d66c4-8765-4ac2-9ec3-9c4a21bbd73b",
  "incomeCategoryId": "35cde5bf-5e55-4ce2-9c14-e6d7ad6b9cf0",
  "amount": 100000,
  "notes": "Bank transfer received",
  "occurredAt": "2026-08-27T13:00:00Z"
}
```

The service locks the split bill before checking the outstanding balance. A
payment cannot exceed the participant's remaining amount. It creates one real
`INCOME` transaction, persists the payment link, and recalculates participant
and bill status atomically. Direct reversal is blocked for an active split-bill
expense or linked reimbursement, preventing the ledger and receivable state
from diverging.

## Dashboard report

### Get a financial dashboard

`GET /reports/dashboard?period=2026-08`

The optional `period` uses `YYYY-MM`; when omitted, the user's configured time
zone determines the current month. The response contains per-currency wallet
balances, jar allocations, monthly income and expense totals, active wallet/jar
counts, budget alerts, open saving goals, and the five most recent transactions.
Totals from different currencies are returned separately rather than converted
with an unverified exchange rate.

### Get transparent monthly insights

`GET /reports/dashboard/insights?period=2026-08`

The optional `period` follows the same `YYYY-MM` rule as the dashboard. The
response ranks concise observations derived from the already-recorded cash flow,
budget status, and saving-goal progress. It is read-only and explainable: it
never changes a transaction, budget, wallet, or goal, and it does not claim to
predict future financial outcomes.

Each insight contains a stable `key`, `severity` (`INFO`, `SUCCESS`, `WARNING`,
or `DANGER`), title, message, and the related currency/amount when applicable.

### Review unusually large expenses

`GET /reports/dashboard/unusual-expenses?period=2026-08`

The optional `period` follows the dashboard `YYYY-MM` rule. The endpoint is
read-only and only considers the authenticated user's `POSTED` expense
transactions. For each expense in the requested month, it compares the amount
with the average of expenses in the same category and currency during the three
preceding months. A finding requires at least three historical transactions and
an amount at least 2.5 times that average.

The response contains the current amount, historical average, historical count,
multiple, severity (`MEDIUM` or `HIGH`), and an explanatory reason. Findings
are review prompts only: the endpoint never changes financial data and does not
claim that a transaction is fraudulent.

### Preview recurring cash flow

`GET /reports/dashboard/cash-flow-forecast?days=30`

Returns a read-only projection from the authenticated user's enabled recurring
rules. `days` is optional (defaults to `30`) and must be from `7` to `90`.
The result uses the user's configured time zone, keeps each currency separate,
and contains projected income, expense, and net totals plus up to twelve
nearest scheduled occurrences.

This endpoint does not create transactions, reserve funds, or advance a
recurring rule's `nextRunAt`. It is a deterministic schedule preview, not an
exchange-rate conversion or a prediction of discretionary spending.

## Error format

Validation and business errors share one response shape:

```json
{
  "timestamp": "2026-08-25T08:30:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/v1/wallets",
  "fieldErrors": {
    "currency": "size must be between 3 and 3"
  }
}
```

Expected status codes include `400`, `401`, `404`, and `409`. A wallet that
does not exist and a wallet owned by another user both return `404`, avoiding
resource-existence leaks.
