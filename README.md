# Wallet Service

A mission-critical microservice for managing user wallets, supporting deposits, withdrawals, transfers, and full transaction history with historical balance queries.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Running the Service](#running-the-service)
3. [API Reference](#api-reference)
4. [Design Decisions](#design-decisions)
5. [Assumptions](#assumptions)
6. [Trade-offs & Compromises](#trade-offs--compromises)
7. [Time Estimate](#time-estimate)

---

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose (optional, for PostgreSQL)

### Run with embedded H2 (zero config)

```bash
mvn spring-boot:run
```

The service starts on **http://localhost:8080**.

Interactive API docs: **http://localhost:8080/swagger-ui.html**

H2 console (dev only): **http://localhost:8080/h2-console** (JDBC URL: `jdbc:h2:mem:walletdb`)

### Run with PostgreSQL via Docker Compose

```bash
docker-compose up --build
```

### Run tests

```bash
# All tests (unit + integration)
mvn test

# Unit tests only
mvn test -Dtest=WalletServiceTest

# Integration tests only
mvn test -Dtest=WalletIntegrationTest
```

---

## Running the Service

### Environment variables (production profile)

| Variable      | Default                              | Description              |
|---------------|--------------------------------------|--------------------------|
| `DB_URL`      | `jdbc:postgresql://localhost:5432/walletdb` | JDBC URL            |
| `DB_USERNAME` | `wallet`                             | DB user                  |
| `DB_PASSWORD` | `wallet`                             | DB password              |

```bash
# With PostgreSQL
SPRING_PROFILES_ACTIVE=prod DB_URL=jdbc:postgresql://... mvn spring-boot:run
```

### Health check

```
GET /actuator/health
```

---

## API Reference

Base path: `/api/v1/wallets`

### Create Wallet

```
POST /api/v1/wallets
Content-Type: application/json

{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "currency": "BRL"
}
```

Response `201 Created`:
```json
{
  "id": "...",
  "userId": "...",
  "balance": 0,
  "currency": "BRL",
  "createdAt": "2024-06-01T12:00:00Z",
  "updatedAt": "2024-06-01T12:00:00Z"
}
```

### Get Wallet

```
GET /api/v1/wallets/{walletId}
GET /api/v1/wallets/by-user/{userId}
```

### Get Current Balance

```
GET /api/v1/wallets/{walletId}/balance
```

### Get Historical Balance

```
GET /api/v1/wallets/{walletId}/balance/history?at=2024-06-01T12:00:00Z
```

Returns the wallet balance as it was at the given ISO-8601 instant.

### Deposit

```
POST /api/v1/wallets/{walletId}/deposit
Content-Type: application/json

{
  "amount": "250.00",
  "description": "Monthly salary"
}
```

### Withdraw

```
POST /api/v1/wallets/{walletId}/withdraw
Content-Type: application/json

{
  "amount": "50.00",
  "description": "ATM withdrawal"
}
```

Returns `422 Unprocessable Entity` if balance is insufficient.

### Transfer

```
POST /api/v1/wallets/{walletId}/transfer
Content-Type: application/json

{
  "targetWalletId": "...",
  "amount": "100.00",
  "description": "Rent payment"
}
```

Returns `422 Unprocessable Entity` if balance is insufficient.  
Returns `400 Bad Request` for self-transfer or cross-currency transfer.

### Transaction History

```
GET /api/v1/wallets/{walletId}/transactions?page=0&size=20
```

Returns a paginated list ordered by `createdAt DESC`.

---

## Design Decisions

### 1. Append-only ledger (`transactions` table)

All financial operations are recorded as **immutable** rows in the `transactions` table. Records are never updated or deleted. This:

- Provides complete auditability (every cent is traceable).
- Enables historical balance reconstruction at any point in time.
- Makes the data a natural event log that can feed analytics or be replayed.

### 2. `balanceAfter` snapshot per transaction

Each `Transaction` row stores `balanceAfter` — the wallet balance immediately after that operation. This makes historical balance queries **O(1)**: find the latest transaction at or before a given timestamp, and read its `balanceAfter` directly. Without this, you'd need to replay all transactions from time zero.

### 3. Pessimistic locking for concurrent operations

`deposit`, `withdraw`, and `transfer` acquire a **pessimistic write lock** (`SELECT ... FOR UPDATE`) on the `Wallet` row before modifying it. Combined with `REPEATABLE_READ` isolation, this prevents:

- Lost updates (two concurrent withdrawals reading the same balance before either commits).
- Phantom reads within the same transaction.

### 4. Deadlock prevention in transfers

When transferring between two wallets, both rows must be locked. Locking them in **ascending UUID order** (consistent across all threads) eliminates the classic ABBA deadlock pattern.

### 5. Optimistic locking (`@Version`) on the Wallet entity

The `version` column gives a second line of defence via JPA optimistic locking, which will catch any edge cases the pessimistic lock doesn't cover (e.g. off-JPA updates).

### 6. Single currency per wallet

A wallet has a fixed `currency` field. Cross-currency transfers are rejected. This avoids the complexity of FX rate management while keeping the model correct.

### 7. BigDecimal with 4 decimal places

All monetary values use `NUMERIC(19,4)` in the DB and `BigDecimal` in Java. Floating-point types (`float`, `double`) are never used for money to avoid rounding errors.

### 8. Spring Boot + Spring Data JPA

Chosen for:
- Production-grade out-of-the-box: connection pooling (HikariCP), transaction management, health checks.
- JPA + Hibernate handles dialect differences between H2 (tests) and PostgreSQL (production).
- SpringDoc/OpenAPI generates Swagger UI automatically.

### 9. H2 for development/tests, PostgreSQL for production

`application.yml` uses H2 in in-memory mode so the service can start and be tested with zero external dependencies. The `prod` profile switches to PostgreSQL.

### 10. Flyway migrations (production schema)

`V1__initial_schema.sql` contains the authoritative DDL for the production database (PostgreSQL). In the dev/test profile, Hibernate's `ddl-auto: create-drop` is used for simplicity.

---

## Assumptions

| # | Assumption | Rationale |
|---|-----------|-----------|
| 1 | **One wallet per user.** The `userId` column has a unique constraint. | Simplest model that satisfies requirements; multi-wallet-per-user would need a wallet "type" or "name" discriminator that wasn't specified. |
| 2 | **Currency is fixed at wallet creation.** Cross-currency transfers are rejected. | FX rate management is out of scope for a 6-8 hour assessment. |
| 3 | **Balance cannot go negative.** A `CHECK` constraint and service-level guard prevent negative balances. | Standard expectation for a consumer wallet; credit products would need a separate model. |
| 4 | **Authentication/authorization is out of scope.** The API is unprotected. | No auth requirement was stated. In production, every endpoint would be behind a JWT/OAuth2 gateway. |
| 5 | **User management is external.** The service accepts any UUID as `userId` without validating its existence in a user service. | The wallet service is a bounded context focused solely on money; user lifecycle is another service's responsibility. |
| 6 | **Timestamps are stored in UTC.** All `Instant` fields use UTC; the JDBC driver is configured with `time_zone: UTC`. | Avoids daylight-saving ambiguity and simplifies historical queries. |
| 7 | **`description` is optional.** | Natural for programmatic transfers; the field is available for human-readable context when needed. |
| 8 | **Idempotency keys are not implemented.** | Would require a separate idempotency table and is a significant feature. Documented as a known gap below. |

---

## Trade-offs & Compromises

### 1. No idempotency keys
A production wallet API would accept a client-supplied `Idempotency-Key` header and deduplicate retries. This would require a separate table and ~2 hours of work. **Mitigation:** the pessimistic lock prevents duplicate concurrent submissions of the same request; only network-level retries can cause duplicates.

### 2. No distributed tracing / structured logging
A production service would emit structured JSON logs (Logstash/Logback) and send traces to an OpenTelemetry collector. The current setup uses plain SLF4J with INFO-level logging of key operations.

### 3. No rate limiting
The API has no request throttling. In production this would be handled at the API gateway layer.

### 4. No soft-delete / wallet status
Wallets cannot be suspended or closed. Adding a `status` enum (`ACTIVE`, `SUSPENDED`, `CLOSED`) with service-level guards would be the next step.

### 5. H2 for tests vs PostgreSQL in production
H2's `MODE=PostgreSQL` covers most SQL compatibility issues, but subtle differences (e.g. `gen_random_uuid()`) can cause Flyway migration tests to fail. In a real project, Testcontainers with a real PostgreSQL image would be used for integration tests.

### 6. No event publishing
A production financial service would publish domain events (e.g. `WalletFunded`, `TransferCompleted`) to a message broker for downstream consumers. This was omitted due to time constraints.

---

## Time Estimate

| Activity | Time |
|----------|------|
| Requirements analysis & design | ~45 min |
| Domain model + repositories | ~45 min |
| Service layer (business logic + concurrency) | ~90 min |
| Controller + DTOs + validation | ~45 min |
| Exception handling | ~20 min |
| Tests (unit + integration) | ~90 min |
| Configuration (profiles, Docker, Flyway) | ~30 min |
| Documentation (README) | ~45 min |
| **Total** | **~7 hours** |
