# P2P Wallet Service — Design Write-Up

## Data Model

Two tables in a single Postgres database:

| Table | Key Columns | Constraints |
|---|---|---|
| `wallets` | `id` (UUID PK), `user_id` (UNIQUE), `balance` (BIGINT) | `CHECK (balance >= 0)` |
| `transfers` | `id` (UUID PK), `idempotency_key` (UNIQUE), `from_wallet_id`, `to_wallet_id`, `amount_paise` | `CHECK (amount_paise > 0)`, FK refs to wallets |

All money values are **integer paise** (`BIGINT`). Never floats, never rupees-as-decimal. The schema enforces this at the type level.

---

## The Simplest-Correct Mechanism for Conservation + No-Overdraft

### What I Used

A single Postgres transaction containing:

1. **`SELECT … FOR UPDATE ORDER BY id`** — locks both the sender and receiver wallet rows.
2. **`UPDATE wallets SET balance = balance - :amount WHERE id = :from AND balance >= :amount`** — conditional debit that atomically checks-and-decrements.
3. **`UPDATE wallets SET balance = balance + :amount WHERE id = :to`** — credit.

All three operations share a single `BEGIN / COMMIT`. Conservation is guaranteed because the debit and credit either both commit or neither does. No-overdraft is guaranteed by the `WHERE balance >= amount` guard — if the sender has insufficient funds, the UPDATE affects 0 rows, and we mark the transfer `DECLINED` and commit that status instead.

### Deadlock Prevention

When two concurrent transfers lock the same two wallets in opposite order (A→B and B→A simultaneously), a deadlock can occur if each transaction locks its sender first. I prevent this by **always acquiring locks in sorted UUID order** (`ORDER BY id` in the `SELECT FOR UPDATE`). Since all transactions acquire locks in the same global order, no cycle can form.

### Heavier Alternatives I Rejected

| Alternative | Why I rejected it |
|---|---|
| **Serializable isolation** | Correct but heavier. Postgres serializable mode detects conflicts at commit time and aborts one transaction, requiring application-level retry loops. More complex code, worse throughput under contention, and the same correctness I get from explicit row locks + conditional update. |
| **Advisory locks** | Equivalent correctness to row locks, but moves locking logic into the application layer (`pg_advisory_lock(wallet_id)`). More error-prone (must remember to release), harder to reason about, and no benefit over `FOR UPDATE` which is already row-level. |
| **Optimistic locking (version column)** | Works well under low contention. Under high contention on hot wallets, the retry rate explodes — every concurrent transfer to/from the same wallet would fail and retry. For a money workload where we want first-attempt success, pessimistic locking is simpler and more predictable. |
| **Application-level mutex / distributed lock** | Introduces a single point of failure (the lock service) and doesn't compose with Postgres transactions. The database already has the best lock manager for this job. |

### Why This Is the Simplest

- **No retry logic**: Pessimistic locks mean the transaction blocks (briefly) rather than failing and retrying.
- **No external dependencies**: Everything lives in Postgres — no Redis, no ZooKeeper, no application mutex.
- **Two SQL statements** do the real work (conditional debit + credit). The `FOR UPDATE` is one more. That's it.

---

## Where Idempotency Lives

The `idempotency_key` column has a **`UNIQUE` constraint** in the `transfers` table, enforced by Postgres.

The transfer record is **inserted in the same transaction** as the debit/credit:

```
BEGIN;
  INSERT INTO transfers (idempotency_key, …) VALUES (…);     -- ← uniqueness checked here
  SELECT … FOR UPDATE ORDER BY id;                             -- ← lock wallets
  UPDATE wallets SET balance = balance - amount WHERE …;       -- ← debit
  UPDATE wallets SET balance = balance + amount WHERE …;       -- ← credit
COMMIT;
```

If the insert hits a UNIQUE violation (duplicate key):
- **Same body** (from, to, amount match): return the original transfer result — this is an **idempotent replay**.
- **Different body**: return **HTTP 409 Conflict** — the key was reused with different parameters.

Because the idempotency key insert and the balance changes are in the **same atomic transaction**, there's no window where a key is recorded but the balance change isn't (or vice versa). This eliminates the ghost-transfer problem.

---

## Consistency vs Availability

**I chose consistency (CP).**

This is a money workload. The consequences of inconsistency — double-spends, phantom balances, lost funds — are catastrophic and unrecoverable. The consequences of unavailability — a failed request that the client can retry — are annoying but tolerable.

Concretely:
- **Single Postgres instance** (not a read-replica cluster). All reads and writes go to the same node.
- **`READ COMMITTED` isolation** with explicit `FOR UPDATE` row locks. Transactions serialize on contention rather than risking stale reads.
- **No caching layer** for balances. Every balance read hits Postgres.

**What I consciously gave up:**
- **Horizontal write scaling**: All writes funnel through one Postgres. This is fine for a wallet service at moderate scale; it becomes a bottleneck at tens of thousands of TPS. The mitigation path (if needed) is partitioning wallets across multiple Postgres shards, not relaxing consistency.
- **Availability during Postgres downtime**: If Postgres is down, the service returns 503. Correct behavior — better to refuse service than serve stale/inconsistent data about money.
- **Read latency**: No cache means every `GET /wallets/{id}` is a DB round-trip. For a financial service, this is the right trade-off — the balance is always fresh.

---

## Race-Free Get-or-Create

```sql
INSERT INTO wallets (user_id, balance) VALUES (:userId, 0)
ON CONFLICT (user_id) DO NOTHING;
```

Postgres's `UNIQUE` constraint on `user_id` serializes concurrent inserts. One wins, the rest silently do nothing. All callers then `SELECT` by `user_id` and get the same wallet. No application-level locking needed.

---

## AI Attribution

- **AI-directed**: Architecture decisions (sorted lock order, conditional debit, single-txn idempotency), technology choices (Spring Boot + Flyway + Micrometer), and overall system design were specified by the author.
- **AI-decided**: Specific implementation patterns (filter ordering, MDC correlation propagation, DTO record syntax, native query syntax), Dockerfile structure, burst test script logic, and docker-compose health check configuration were generated by AI with author review.

---

## Free-Tier Cost Note

| Service | Provider | Tier | Cost |
|---|---|---|---|
| Web Service | Render | Free (750 hours/month) | ₹0 |
| PostgreSQL | Render | Free (1 GB, 90 days) | ₹0 |
| **Total** | | | **₹0** |

The entire deployment runs on Render's free tier with no credit card required.
