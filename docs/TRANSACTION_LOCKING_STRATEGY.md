# Financial Transaction and Locking Strategy

## Purpose

FinCore treats a balance-changing request as one atomic financial operation.
The system must never create a partial ledger, let a wallet fall below zero
when negative balances are disabled, or record the same client request twice.

## Write boundary

Income, expense, internal transfer, reversal, recurring recording, split-bill
settlement, and reconciliation adjustment are executed by service methods
annotated with `@Transactional`. A successful commit includes the financial
transaction, wallet updates, balanced ledger entries, related money-jar
movements when applicable, and the audit event. Any exception rolls back the
whole operation.

```text
request
  -> normalize idempotency key
  -> find previous result
  -> lock financial owner
  -> find previous result again
  -> lock affected wallet, jars, rule, or bill
  -> validate business rules
  -> update balances and write balanced ledger entries
  -> write audit event
  -> commit once
```

The second idempotency lookup is mandatory. A request can observe no previous
record before waiting on the financial write lock, then find a completed record
after the earlier request commits.

## Lock order

| Resource | Protection | Reason |
| --- | --- | --- |
| User account | `PESSIMISTIC_WRITE` at the start of a financial write | Serializes writes for one owner and closes the idempotency race window. |
| Wallet | `PESSIMISTIC_WRITE` | Prevents lost updates and validates available balance from the current database row. |
| Two-wallet transfer | Both wallets are selected in ascending database ID order | Two opposite transfers acquire locks in the same order, reducing deadlock risk. |
| Active money jars | Selected in stable order with the active wallets | Allocation cannot exceed the money currently available in wallets. |
| Allocation rule | `PESSIMISTIC_WRITE` before percentages are read | A rule cannot change while an income is being allocated. |
| Split bill | `PESSIMISTIC_WRITE` before a participant payment | The remaining amount and payment status change together. |
| Recurring rule | `PESSIMISTIC_WRITE` while a due occurrence is recorded | Scheduler retries or concurrent workers cannot post the same period twice. |

Entities that can be changed outside these coordinated writes also have a JPA
`@Version` field. Optimistic locking is an additional stale-update guard; it
does not replace the explicit pessimistic locks used when balances are changed.

## Idempotency guarantee

All financial write APIs accept an `Idempotency-Key`. Its scope is one user and
one logical request. FinCore combines three protections:

1. A fast lookup returns a prior recorded transaction for ordinary retries.
2. The per-user financial write lock serializes two first-time requests using
   the same key. The waiting request performs the lookup again after the lock.
3. PostgreSQL has a partial unique index on
   `(user_id, idempotency_key)` for non-null keys as the final database guard.

The concurrency integration suite sends twelve simultaneous income requests
with one key and asserts that exactly one financial transaction and exactly two
balanced ledger entries are committed. It also sends competing expenses with
different keys and asserts that the wallet cannot be overdrawn.

## Data invariants

- A posted income or expense has one wallet entry and one external entry whose
  signed values sum to zero.
- An internal transfer has two opposite wallet entries and does not change total
  assets or income/expense reporting.
- A posted transaction is never edited or deleted. A correction is a separate
  reversal linked to the original transaction.
- A money jar changes allocation only; it does not create or destroy wallet
  balance.
- Reporting uses posted ledger facts and keeps currencies separate until an
  explicit exchange-rate feature exists.

## Operational response

When a user reports a duplicate or inconsistent balance, retain the displayed
transaction ID, request time, API path, and `X-Correlation-Id`. Inspect the
audit and ledger entries first. Correct an approved mistake through reversal or
reconciliation adjustment; never edit financial rows directly in PostgreSQL.
