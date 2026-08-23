# P2P Wallet Service

A peer-to-peer wallet service built with Spring Boot that supports wallet management, fund transfers, and financial invariant enforcement. All monetary values are stored in **paise** (1/100th of a currency unit) to avoid floating-point issues.

## Features

- **Wallet Management** — Create, fetch, and deposit funds into wallets
- **P2P Transfers** — Move money between wallets with full atomicity
- **Idempotency** — Duplicate transfer requests (same `transaction_id`) return the original result safely
- **No Overdraft** — Conditional debit ensures balance never goes negative (`CHECK(balance >= 0)`)
- **Deadlock Prevention** — Wallets are locked in sorted order during transfers
- **Bearer Token Auth** — Simple token-based authentication via `Authorization` header
- **Prometheus Metrics** — Custom counters for transfers, declines, and wallet creation
- **Swagger UI** — Interactive API documentation via OpenAPI 3

## Tech Stack

| Component       | Technology                          |
|-----------------|-------------------------------------|
| Framework       | Spring Boot 3.2.5                   |
| Language        | Java 17                             |
| Database        | PostgreSQL 16                       |
| ORM             | Hibernate / Spring Data JPA         |
| Migrations      | Flyway                              |
| API Docs        | SpringDoc OpenAPI (Swagger UI)      |
| Metrics         | Micrometer + Prometheus             |
| Build           | Maven                               |
| Containerization| Docker + Docker Compose             |

## Project Structure

```
src/main/java/com/p2pwallet/
├── P2PWalletApplication.java       # Application entry point
├── config/
│   ├── MetricsConfig.java          # Custom Micrometer counters
│   └── OpenApiConfig.java          # Swagger/OpenAPI metadata
├── controller/
│   ├── WalletController.java       # Wallet REST endpoints
│   └── TransferController.java     # Transfer REST endpoints
├── dto/
│   ├── CreateWalletRequest.java    # Wallet creation request body
│   ├── CreateTransferRequest.java  # Transfer request body
│   ├── WalletResponse.java         # Wallet response body
│   └── TransferResponse.java       # Transfer response body
├── entity/
│   ├── Wallet.java                 # Wallet JPA entity
│   └── Transfer.java               # Transfer JPA entity
├── exception/
│   ├── GlobalExceptionHandler.java # Centralized error handling
│   ├── IdempotencyConflictException.java
│   └── InsufficientFundsException.java
├── filter/
│   ├── AuthFilter.java             # Bearer token authentication
│   └── CorrelationIdFilter.java    # Request correlation IDs
├── repository/
│   ├── WalletRepository.java       # Wallet data access
│   └── TransferRepository.java     # Transfer data access
└── service/
    ├── WalletService.java          # Wallet business logic
    └── TransferService.java        # Transfer business logic
```

## Getting Started

### Prerequisites

- Java 17+
- Docker & Docker Compose (for PostgreSQL)
- Maven (or use the included `mvnw` wrapper)

### Run with Docker Compose (Recommended)

```bash
# Start PostgreSQL + App
docker-compose up --build

# App will be available at http://localhost:8080
```

### Run Locally (with external PostgreSQL)

```bash
# 1. Start PostgreSQL only
docker-compose up db

# 2. Build and run the app
./mvnw spring-boot:run
```

### Environment Variables

| Variable            | Default                                          | Description              |
|---------------------|--------------------------------------------------|--------------------------|
| `PORT`              | `8080`                                           | Server port              |
| `DATABASE_URL`      | `jdbc:postgresql://localhost:5432/p2pwallet`      | JDBC connection URL      |
| `DATABASE_USERNAME` | `wallet`                                         | Database username        |
| `DATABASE_PASSWORD` | `wallet_secret`                                  | Database password        |
| `USER_TOKENS`       | `alice:tok_alice,bob:tok_bob,charlie:tok_charlie,dave:tok_dave` | Auth token mappings |

## Authentication

All API endpoints (except actuator and Swagger) require a Bearer token in the `Authorization` header:

```
Authorization: Bearer tok_alice
```

Pre-configured tokens:

| User      | Token          |
|-----------|----------------|
| alice     | `tok_alice`    |
| bob       | `tok_bob`      |
| charlie   | `tok_charlie`  |
| dave      | `tok_dave`     |

## API Reference

### Wallets

#### Create Wallet
```bash
POST /wallets
Content-Type: application/json
Authorization: Bearer tok_alice

{
  "user_id": "alice"
}
```

#### Get Wallet by ID
```bash
GET /wallets/{id}
Authorization: Bearer tok_alice
```

#### Get Wallet by User ID
```bash
GET /wallets/user/{userId}
Authorization: Bearer tok_alice
```

#### Deposit Funds
```bash
POST /wallets/{id}/deposit
Content-Type: application/json
Authorization: Bearer tok_alice

{
  "amount_paise": 100000
}
```

### Transfers

#### Create Transfer
```bash
POST /transfers
Content-Type: application/json
Authorization: Bearer tok_alice

{
  "from": "<wallet-uuid>",
  "to": "<wallet-uuid>",
  "amount_paise": 5000,
  "transaction_id": "txn_001"
}
```

#### Get Transfer by ID
```bash
GET /transfers/{id}
Authorization: Bearer tok_alice
```

#### Get Transfer by Transaction ID
```bash
GET /transfers/transaction/{transactionId}
Authorization: Bearer tok_alice
```

## Example Workflow

```bash
TOKEN="Bearer tok_alice"

# 1. Create wallets
curl -s -X POST http://localhost:8080/wallets \
  -H "Authorization: $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"user_id": "alice"}'

curl -s -X POST http://localhost:8080/wallets \
  -H "Authorization: $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"user_id": "bob"}'

# 2. Deposit into Alice's wallet (use the wallet ID from step 1)
curl -s -X POST http://localhost:8080/wallets/<alice-wallet-id>/deposit \
  -H "Authorization: $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount_paise": 100000}'

# 3. Transfer from Alice to Bob
curl -s -X POST http://localhost:8080/transfers \
  -H "Authorization: $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "from": "<alice-wallet-id>",
    "to": "<bob-wallet-id>",
    "amount_paise": 25000,
    "transaction_id": "txn_001"
  }'
```

## Swagger UI

Interactive API documentation is available at:

| URL                                        | Description        |
|--------------------------------------------|--------------------|
| http://localhost:8080/swagger-ui.html       | Swagger UI         |
| http://localhost:8080/v3/api-docs           | OpenAPI JSON spec  |

## Monitoring

### Actuator Endpoints

| URL                                        | Description        |
|--------------------------------------------|--------------------|
| http://localhost:8080/actuator/health       | Health check       |
| http://localhost:8080/actuator/prometheus   | Prometheus metrics |
| http://localhost:8080/actuator/info         | App info           |

### Custom Metrics

| Metric                                           | Description                          |
|--------------------------------------------------|--------------------------------------|
| `p2p_transfers_total{status="completed"}`         | Total completed transfers            |
| `p2p_transfers_total{status="declined"}`          | Total declined transfers             |
| `p2p_transfers_declined_insufficient_funds_total` | Declines due to insufficient funds   |
| `p2p_transfers_idempotent_replays_total`          | Idempotent replay hits               |
| `p2p_wallets_created_total`                       | Total new wallets created            |

```bash
# View all custom metrics
curl -s http://localhost:8080/actuator/prometheus | grep "p2p_"
```

## Database Schema

### wallets
| Column       | Type         | Constraints                     |
|--------------|--------------|----------------------------------|
| `id`         | UUID         | Primary key, auto-generated     |
| `user_id`    | VARCHAR(255) | NOT NULL, UNIQUE                |
| `balance`    | BIGINT       | NOT NULL, DEFAULT 0, CHECK >= 0 |
| `created_at` | TIMESTAMPTZ  | NOT NULL                        |
| `updated_at` | TIMESTAMPTZ  | NOT NULL                        |

### transfers
| Column           | Type         | Constraints                       |
|------------------|--------------|-----------------------------------|
| `id`             | UUID         | Primary key, auto-generated       |
| `idempotency_key`| VARCHAR(255) | NOT NULL, UNIQUE                  |
| `from_wallet_id` | UUID         | NOT NULL, FK → wallets(id)        |
| `to_wallet_id`   | UUID         | NOT NULL, FK → wallets(id)        |
| `amount_paise`   | BIGINT       | NOT NULL, CHECK > 0               |
| `status`         | VARCHAR(20)  | NOT NULL, DEFAULT 'COMPLETED'     |
| `decline_reason` | VARCHAR(255) | Nullable                          |
| `created_at`     | TIMESTAMPTZ  | NOT NULL                          |

## Design Decisions

- **Paise as BIGINT** — Avoids floating-point rounding errors. All amounts are in the smallest currency unit.
- **Conditional Debit** — `UPDATE ... WHERE balance >= amount` ensures no overdraft at the database level, backed by a `CHECK(balance >= 0)` constraint as a safety net.
- **Sorted Lock Order** — Wallets are locked via `SELECT ... FOR UPDATE ORDER BY id` to prevent deadlocks when concurrent transfers involve the same wallet pair.
- **Idempotency via UNIQUE constraint** — The `transaction_id` is unique. Duplicate inserts are caught by `DataIntegrityViolationException`, and the original result is returned from a new transaction (to avoid Hibernate session poisoning).
- **Flyway Migrations** — Schema is managed by Flyway (`ddl-auto: validate`), ensuring the app never modifies the schema at runtime.

## Docker

### Build Image
```bash
docker build -t p2p-wallet .
```

### Run with Docker Compose
```bash
docker-compose up --build
```

The Docker setup uses a multi-stage build (JDK for compilation, JRE for runtime) and runs as a non-root user with a health check on the actuator endpoint.
