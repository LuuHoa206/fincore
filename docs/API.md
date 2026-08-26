# FinCore API contract

Base path: `/api/v1`

All request and response bodies use JSON. Protected endpoints require:

```http
Authorization: Bearer <access-token>
```

Access tokens expire after 15 minutes by default. Refresh tokens are opaque,
stored as SHA-256 hashes, rotated after use, and expire after 30 days by
default. The durations are configurable through environment variables.

## Authentication

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

## Transactions

### List transactions

`GET /transactions`

Returns the latest 100 transactions owned by the authenticated user, including
wallet and category display details.

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

### Reverse a transaction

`POST /transactions/{transactionId}/reverse`

The original transaction is marked reversed and a balanced counter-transaction
is added. The original record is never deleted.

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
