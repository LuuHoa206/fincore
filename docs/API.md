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
