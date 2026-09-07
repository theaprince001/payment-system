# 💸 Payment Orchestration System (Simulated)

A production‑grade payment orchestration backend built with Java Spring Boot microservices, a Python machine‑learning fraud service, double‑entry ledger accounting, and asynchronous messaging.  
**No real money is processed—all external providers are mocked, but the core financial patterns are real.**

This system demonstrates how a modern fintech backend handles idempotency, distributed transactions, failure recovery, and AI‑based risk scoring under real constraints.

## Why I built this
I wanted to understand how real payment systems handle duplicate requests, crashes, and consistency without relying on expensive cloud services. The goal was to build something that runs locally with Docker Compose, yet behaves like a production orchestrator: strict state machines, immutable ledgers, asynchronous settlement, and a fraud layer that doesn’t take down the system when it fails.

## Architecture
The system consists of 8 microservices total – 7 Java Spring Boot services plus 1 Python FastAPI fraud‑scoring service. A shared common module contains DTOs and enums used across the Java services.

- **api‑gateway** – Spring Cloud Gateway – JWT validation, routing, rate limiting, CORS
- **auth‑service** – Registration, login, JWT issuance, OTP simulation (Redis)
- **user‑service** – User profiles, KYC mock, phone‑to‑user lookup
- **payment‑method‑service** – Tokenised storage of cards/UPI/bank accounts, verification
- **payment‑orchestrator** – Brain of the system – payment state machine, idempotency, fraud check, event publishing
- **ledger‑service** – Immutable double‑entry ledger, balance derived from entries, top‑up
- **notification‑service** – Asynchronous notifications (logs instead of SMS/email)
- **fraud‑python‑service** – FastAPI + scikit‑learn RandomForest – risk scoring endpoint

All services communicate via REST (gateway → services) and RabbitMQ (orchestrator ↔ ledger). PostgreSQL stores all transactional data. Redis handles idempotency keys, rate‑limit counters, and OTPs.

## Tech Stack
- Backend: Java 17, Spring Boot 3.2, Spring Cloud Gateway, Spring Data JPA, Spring AMQP, Resilience4j
- Database: PostgreSQL 15
- Cache: Redis 7
- Messaging: RabbitMQ 3
- AI/ML: Python 3.11, FastAPI, scikit‑learn
- Containerisation: Docker, Docker Compose
- Testing: JUnit 5, AssertJ

## Key Features

### Idempotent Payments
Every payment request includes a unique `idempotencyKey`. The orchestrator enforces no‑duplicate‑charges across three layers:
- Redis cache – fast duplicate detection with 24‑hour TTL.
- Database unique constraint – durable protection even if Redis fails.
- Optimistic locking (`@Version`) – prevents race conditions between concurrent requests.

### Double‑Entry Ledger
Every successful payment creates two immutable rows: a debit and a credit. Balances are never stored; they are derived via `SUM(amount)`. This gives full auditability and ensures the ledger always balances.

### Strict State Machine
Payments move through a strict sequence:

`CREATED → PENDING → SUCCESS / FAILED / REVERSED`

Plus a special `PENDING_REVIEW` state for suspicious transactions (see fraud detection). No state is skipped, and transitions are guarded by conditional SQL updates.

### AI‑Based Fraud Detection
A Python FastAPI service hosts a `scikit-learn RandomForest` model that scores each payment before any money movement occurs. The model takes four features: amount, user account age, payment method age, and transaction velocity. It returns a score and a decision:

- `score > 0.7` → `BLOCK`
- `0.3 < score ≤ 0.7` → `REVIEW`
- `score ≤ 0.3` → `ALLOW`

The Java orchestrator calls this service through a `Resilience4j` circuit breaker (50% failure threshold, 10‑call sliding window). If the ML service is down or times out, a local rule‑based fallback kicks in. **Important honesty note:** currently three of the four model features are hardcoded (`userAccountAgeDays=30`, `paymentMethodAgeSeconds=60`, `transactionVelocity=2`), so in practice the model behaves like an amount‑threshold check. Wiring these to real user/transaction history is a natural next step.

### PENDING_REVIEW & Race‑Safe Transitions
Payments flagged for review enter `PENDING_REVIEW`. A background scheduler runs every 60 seconds and auto‑rejects any payment stuck in that state for more than 24 hours. The update uses a conditional SQL:

```sql
UPDATE payment_intents SET status = 'FAILED' WHERE id = ? AND status = 'PENDING_REVIEW'
```

If zero rows are updated, another process already changed the state, so we skip. This prevents lost updates when a human reviewer and the auto‑reject job race.

### Rate Limiting
Redis‑backed counters limit each user to 5 payments per minute, preventing abuse.

## Debugging Journey
These are real bugs I hit and fixed during development, not hypothetical examples.

### Spring AOP self‑invocation disabled the circuit breaker
I annotated `callFraudService()` inside `PaymentOrchestratorService` with `@CircuitBreaker`, but the method was called from `assessRisk()` in the same class. I assumed Spring AOP would intercept the call. It didn’t, because Spring’s proxy only applies to calls through the bean reference, not internal `this` calls. The circuit breaker never counted failures. I fixed it by moving the method into a separate `FraudServiceClient` bean, so the call crosses the proxy boundary and the circuit breaker works as intended.

### Fallback handler swallowed real bugs
The `@CircuitBreaker(fallbackMethod = "fraudFallback")` caught every exception, including `NullPointerException` from my own code, and routed them to the fallback. That would have silently treated internal bugs as service degradation, potentially allowing risky payments. I added a custom `FraudServiceInternalException` and listed it in `ignoreExceptions` so only known dependency failures trigger the fallback. Now real bugs propagate and return a `SYSTEM_ERROR` decision instead of a fraud block.

### Transaction‑visibility bug in concurrency test
I initially annotated the test class with `@Transactional` for automatic rollback. But Spring’s test transaction binds to the main thread only; worker threads spawned via `ExecutorService` don’t inherit it. Each worker ran under `READ COMMITTED` isolation and couldn’t see the uncommitted row inserted by the main thread—every update returned 0 rows. I fixed it by removing `@Transactional` from the test class, letting the setup insert commit immediately, and using `@AfterEach` for cleanup. This taught me that multi‑threaded tests require careful thought about transaction propagation.

### Database constraint missing `PENDING_REVIEW`
Hibernate’s `ddl-auto=update` did not modify the existing `CHECK` constraint on the `status` column. I had to manually drop and re‑add it to include the new status. This highlighted the need for proper schema migrations in production.

### Missing columns for risk fields
Adding `riskScore`, `riskSource`, etc. to the entity did not automatically add columns to the existing table. I used `ALTER TABLE` to add them. Again, a migration tool like Flyway would avoid this in production.

## Testing

### Concurrency Test
`ReviewConditionalUpdateConcurrencyTest` runs against the real PostgreSQL instance (from Docker Compose). It uses 20 threads – 10 trying to approve a payment, 10 trying to auto‑reject it – racing to update the same `PENDING_REVIEW` row. The result is exactly `successCount == 1` and `skipCount == 19`, proving that the conditional SQL update allows only one transition. This test is run manually, not yet wired into CI.

### Manual Integration Flow
The system is tested end‑to‑end locally by registering users, topping up, making payments, and checking ledger entries. No Testcontainers are used; the local Docker Compose environment serves as the integration test bed.

## Setup (Local)

### Prerequisites
- Java 17 or later
- Maven
- Docker Desktop
- Python 3.11 (for the fraud service only)

### Backend
```bash
# from project root
cd common && mvn clean install -DskipTests
cd ../auth-service && mvn clean package -DskipTests
cd ../user-service && mvn clean package -DskipTests
cd ../payment-method-service && mvn clean package -DskipTests
cd ../payment-orchestrator && mvn clean package -DskipTests
cd ../ledger-service && mvn clean package -DskipTests
cd ../notification-service && mvn clean package -DskipTests
cd ../api-gateway && mvn clean package -DskipTests
cd ..
```

### Python fraud service
```bash
cd fraud-python-service
python train_model.py   # generates fraud_model.joblib
cd ..
```

This script creates a synthetic dataset, trains the RandomForest model, and saves `fraud_model.joblib` in the current directory. The Docker image copies this file during build. If you skip this step, the Python service container will crash‑loop because the model file is missing.

### Now start everything
```bash
docker-compose up -d --build
```

This single command builds and starts all 8 services, including the `fraud-python-service`. No separate build step is needed for Python.

The backend API is available at `http://localhost:8080`.

## API Endpoints (Main)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/register` | Register a new user |
| POST | `/api/auth/login` | Login, sets JWT cookie |
| GET | `/api/users/me` | Get current user profile |
| GET | `/api/users/by-phone/{phone}` | Lookup user by phone for payee |
| POST | `/api/payment-methods` | Add a payment method |
| POST | `/api/payment-methods/{id}/verify` | Verify payment method (mock) |
| POST | `/api/payments` | Create a payment (idempotent) |
| GET | `/api/payments/{id}` | Get payment status |
| POST | `/api/ledger/topup` | Add cash to wallet |
| GET | `/api/ledger/balance` | Get current balance |
| GET | `/api/ledger/transactions` | Get transaction history |

All endpoints except auth require a valid JWT cookie (or `Authorization: Bearer`).

## Deployment
Currently the system runs locally via Docker Compose. I have not deployed it to a public cloud yet. The architecture is designed to be portable—each service can be containerized independently, and the Python fraud service is already separated as its own container.

A future deployment could use Render (free tier) or [Fly.io](https://fly.io/) with minimal changes, but right now the project is intended for local demonstration and portfolio review.

## What I’d improve next
- Compute real transaction velocity and payment‑method age from database data instead of hardcoding them
- Replace the synthetic fraud training data with a real dataset (and re‑evaluate the model honestly)
- Set up a CI/CD pipeline with GitHub Actions
- Add more integration tests for failure scenarios (insufficient balance, provider timeout)
- Implement a real external provider simulator with configurable latencies
- Add Flyway for proper database migrations
- Automate the fraud model training step inside the Docker build so a clean clone doesn’t require the manual `python train_model.py`

## Disclaimer
This project simulates a payment system for educational and portfolio purposes.  
**No real money, card numbers, or bank accounts are involved.** All external providers are mocked.

Built with ❤️ and a lot of late‑night debugging.