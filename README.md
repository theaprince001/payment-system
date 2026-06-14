# 💸 Payment Orchestration System (Simulated)

A production‑grade, zero‑cost payment orchestration system built with Spring Boot microservices, double‑entry ledger accounting, and asynchronous messaging.  
**No real money is processed – everything is mocked.** This is a backend system designed to showcase architecture, reliability, and attention to financial‑grade detail.

## Why I built this
I wanted to understand how real payment systems handle idempotency, failures, and consistency without using expensive cloud services. The goal was to build something that could run **forever free** on a single VM, yet still behave like a real orchestrator between users, payment methods, and a ledger.

## Architecture
Eight Spring Boot services, all talking to each other through RabbitMQ and REST:

- **api‑gateway** – Spring Cloud Gateway, JWT validation, rate limiting
- **auth‑service** – Registration / login, JWT tokens, OTP simulation (Redis)
- **user‑service** – User profiles, KYC mock
- **payment‑method‑service** – Tokenised storage of cards/UPI/bank accounts
- **payment‑orchestrator** – Brain of the system: payment state machine, idempotency, failure recovery
- **ledger‑service** – Immutable double‑entry ledger, balance derived from entries
- **notification‑service** – Asynchronous notifications (logs instead of SMS/email)
- **common** – Shared DTOs and enums

Everything is containerised with Docker Compose. PostgreSQL for the ledger & transactional data, Redis for idempotency/rate limiting, RabbitMQ for async messaging.

## Tech Stack
- Java 17, Spring Boot 3.2, Spring Cloud Gateway, Spring Data JPA
- PostgreSQL, Redis, RabbitMQ
- Docker, Docker Compose
- JWT (jjwt), Lombok, Prometheus, Sentry (optional), Springdoc OpenAPI
- Testcontainers (integration tests)

## What makes this different?
- **Zero‑cost production** – Deployed on Render's free tier (no credit card). PostgreSQL & Redis are free managed instances; RabbitMQ runs inside the app container. Everything lives in a single Docker image.
- **Double‑entry ledger** – Every payment creates two immutable rows: one debit, one credit. Balances are always derived, never stored.
- **Idempotency** – Redis + database checks guarantee no double charges. Same idempotency key always returns identical result.
- **State machine** – Payments move strictly through `CREATED → PENDING → SUCCESS / FAILED / REVERSED`. No skipping states.
- **Self‑healing** – A background scheduler looks for payments stuck in `PENDING` and reverses them automatically.
- **Proper error handling** – Duplicate registrations return 409 Conflict, validation errors are structured, and global exception handlers keep the API predictable.

## The bugs I fought (and fixed)
- **Ledger deserialization** – The ledger service kept throwing `MessageConversionException` because it lacked a Jackson converter. I added a `RabbitMQConfig` with `Jackson2JsonMessageConverter` and a custom `SimpleRabbitListenerContainerFactory`. Took me way too long to realise the container was using an old JAR without the converter.
- **First payment stuck** – The initial credit for the payer wasn't seeded, so the debit failed due to insufficient balance. I manually inserted a starting balance, then tested the full automatic flow.
- **Gateway JWT forwarding** – I accidentally passed the user ID as the Bearer token (instead of the actual JWT). The gateway's filter caught it and returned 401. Fixed by correctly passing the token from login.
- **Ports not exposed** – When testing locally, I couldn't hit individual services because only the gateway was published. I realised all API calls must go through `:8080` (the gateway) and that internal services talk to each other inside the Docker network.

## Setup (local)
1. Clone the repo
2. Install Java 17, Maven, Docker Desktop
3. Build everything: `mvn clean package -DskipTests` (from root)
4. Start: `docker-compose up -d --build`
5. The API is at `http://localhost:8080`
6. Swagger: `http://localhost:8081/swagger-ui.html` (auth), etc.

## Testing the flow
- Register two users
- Login to get a JWT
- Add and verify a payment method (CARD/UPI/BANK)
- Create a payment with a unique `idempotencyKey`
- Check status via `GET /api/payments/{id}`
- Verify the ledger entries in PostgreSQL
- RabbitMQ management UI at `http://localhost:15672`

## Integration tests
There's a Testcontainers‑based integration test for the orchestrator. It spins up real PostgreSQL, Redis, and RabbitMQ containers, then performs a full payment and asserts success.  
Run it with: `cd payment-orchestrator && mvn test`

## Deployment (live demo)
The system is deployed on **Render** (free tier).  
- **PostgreSQL** and **Redis** are Render‑managed free instances.  
- **RabbitMQ** runs inside the app container.  
- The entire backend is a single Web Service built from the Dockerfile.

**No credit card was ever used.** The deployment stays up thanks to a free UptimeRobot monitor pinging the health endpoint every 5 minutes.

## API endpoints (main ones)
| Method | Path | Description |
|--------|------|-------------|
| POST | /api/auth/register | Register a new user |
| POST | /api/auth/login | Login, returns JWT |
| POST | /api/payment-methods | Add a payment method |
| POST | /api/payment-methods/{id}/verify | Verify a payment method (mock) |
| POST | /api/payments | Create a payment (idempotent) |
| GET | /api/payments/{id} | Get payment status |

All calls except auth require `Authorization: Bearer <token>`.

## What I'd improve next
- Add a proper GET endpoint for transaction history
- Implement a real external provider simulator with configurable latencies
- Build a simple React dashboard to see payments in real time
- Write more integration tests for failure scenarios (insufficient balance, provider timeout)
- Set up a CI/CD pipeline with GitHub Actions

## Disclaimer
This project simulates a payment system for educational and portfolio purposes. **No real money, card numbers, or bank accounts are involved.** All external providers are mocked.

---
Built with ❤️ and a lot of late‑night debugging.
