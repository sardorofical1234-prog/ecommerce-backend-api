# E-commerce Backend API

[![CI](https://github.com/sardorofical1234-prog/ecommerce-backend-api/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/sardorofical1234-prog/ecommerce-backend-api/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.3-6DB33F)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1)
![Scope](https://img.shields.io/badge/Scope-Portfolio%20Project-blue)

A portfolio-grade REST API demonstrating backend engineering fundamentals: stateless authentication, transactional checkout, inventory consistency, idempotent operations, concurrency control, integration testing, and containerized delivery.

Built as a **modular monolith** with Java 21, Spring Boot 4.1.3, PostgreSQL 17 and Redis 7.

> **Scope:** this is an educational portfolio project. Payments are simulated; no card data or real money is processed. Local development and the full test suite use Docker Compose and Testcontainers. No paid hosting or external services are required.

## What this project demonstrates

- **Security:** Spring Security 7, JWT access tokens, BCrypt password hashing, role-based access, and customer data isolation.
- **Data consistency:** checkout creates an order, reserves stock, records inventory movements, and clears the cart inside one PostgreSQL transaction.
- **Concurrency:** pessimistic row locks and deterministic product-lock ordering prevent overselling during parallel checkout.
- **Idempotency:** checkout and payment retries reuse stable idempotency keys to avoid duplicate orders and payment attempts.
- **Auditability:** immutable order-item snapshots and an inventory movement ledger preserve historical facts.
- **Resilience:** Redis Lua fixed-window rate limiting, request correlation IDs, structured JSON logs, health probes, and secured Prometheus metrics.
- **Verification:** JUnit 5, Mockito, PostgreSQL/Redis Testcontainers, and a Docker Compose HTTP smoke test in GitHub Actions.

## Technology stack

| Area | Technologies |
|---|---|
| Language / framework | Java 21, Spring Boot 4.1.3, Spring MVC |
| Security | Spring Security 7, OAuth2 Resource Server, JWT (HS256), BCrypt |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL 17, Flyway |
| Cache / rate limiting | Redis 7, atomic Lua script, Caffeine single-instance fallback |
| API documentation | Springdoc OpenAPI, Swagger UI |
| Observability | Actuator, Micrometer, Prometheus, Grafana dashboard template, Logstash JSON |
| Build / delivery | Maven, Docker, Docker Compose, GitHub Actions |
| Testing | JUnit 5, Mockito, Testcontainers 2.0 |

## Architecture

The application is intentionally a **single deployable modular monolith**. Domain modules share one PostgreSQL database so checkout can rely on a real ACID transaction instead of a distributed saga.

- [Architecture overview and checkout sequence](docs/architecture.md)
- [Database ER diagram and relationship notes](docs/data-model.md)
- [Architecture decision records](docs/adr/)
- [Observability configuration](docs/observability.md)

## Core business flow

1. A customer registers and receives a short-lived JWT access token.
2. An administrator creates products; customers browse the public catalogue.
3. A customer adds products to a personal cart. Cart operations do not reserve stock.
4. Checkout validates the cart against current catalogue data, locks product rows, calculates totals on the server, creates an order snapshot, reserves inventory, writes ledger entries, and clears the cart atomically.
5. The customer approves or declines a **simulated** payment attempt.
6. Pending orders can be cancelled, or expire after 30 minutes; reserved stock is released exactly once.

### Important invariants

- A client cannot choose the customer ID, price, order total, or payment amount.
- One cart belongs to one customer; cart items are unique per cart/product pair.
- One checkout idempotency key per customer creates at most one order.
- One payment idempotency key per order returns the original attempt on retry.
- A product row is locked before its stock is reserved; product locks are acquired in stable UUID order.
- An order stores product name, SKU and price snapshots, so later catalogue edits do not rewrite order history.
- Inventory reservation/release, order state changes and ledger writes are transactional.
- The simulated payment provider accepts only APPROVE / DECLINE scenarios and never accepts card details.

## Run locally

### Prerequisites

- Java 21
- Docker Engine / Docker Desktop with Docker Compose v2
- Git

### Start the API with Docker Compose

1. Clone the repository and enter the project directory.
2. Copy **.env.example** to **.env**.
3. Generate a JWT secret using **openssl rand -base64 32** and replace the placeholder in **.env**.
4. Set a unique local database password in **.env**.
5. Enable the local bootstrap administrator and set its credentials:

   ~~~dotenv
   BOOTSTRAP_ADMIN_ENABLED=true
   BOOTSTRAP_ADMIN_EMAIL=admin@example.com
   BOOTSTRAP_ADMIN_PASSWORD=replace-with-a-long-unique-password
   BOOTSTRAP_ADMIN_FULL_NAME=Portfolio Admin
   ~~~

6. Start the stack:

   ~~~bash
   docker compose up --build -d
   ~~~

7. Open Swagger UI at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html).

The API listens on port **8080**. PostgreSQL is exposed locally on **5433**; Redis stays on the private Compose network. PostgreSQL data is stored in a named Docker volume.

Stop the stack without deleting the database volume:

~~~bash
docker compose down
~~~

### Run tests

~~~bash
mvn clean verify
~~~

Docker must be available because Testcontainers starts PostgreSQL 17 and Redis 7. The CI workflow runs Maven verification and then exercises the running Compose API over HTTP using the same smoke-test script.

### Run the HTTP smoke test manually

With the Compose stack running and bootstrap admin enabled:

~~~bash
./scripts/compose-smoke-test.sh
~~~

The script tests admin authentication/product creation, customer registration, cart mutation, checkout idempotency, simulated payment idempotency, order/payment history, and final stock.

## API overview

All application routes are versioned under **/api/v1**.

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| POST | /auth/register | Public | Register a customer |
| POST | /auth/login | Public | Obtain a JWT |
| GET | /users/me | Authenticated | Current principal and roles |
| GET | /products | Public | Search and paginate products |
| GET | /products/{id} | Public | Product details |
| POST | /admin/products | Admin | Create a product |
| PUT | /admin/products/{id} | Admin | Update a product |
| DELETE | /admin/products/{id} | Admin | Deactivate a product |
| GET / POST | /cart, /cart/items | Customer | Read cart and add an item |
| PUT / DELETE | /cart/items/{productId} | Customer | Update or remove an item |
| POST | /checkout | Customer | Create order and reserve stock |
| GET | /orders | Customer | List own orders |
| GET | /orders/{orderId} | Customer | Get own order |
| POST | /orders/{orderId}/cancel | Customer | Cancel a pending order |
| POST | /orders/{orderId}/payments | Customer | Run a simulated payment |
| GET | /orders/{orderId}/payments | Customer | Payment attempt history |

Health probes are available at **/actuator/health**, **/actuator/health/liveness**, and **/actuator/health/readiness**. General metrics require authentication; the Prometheus scrape endpoint requires its dedicated Basic Auth credentials.

## API examples

### Register a customer

~~~http
POST /api/v1/auth/register
Content-Type: application/json

{
  "email": "buyer@example.com",
  "password": "a-unique-password-12",
  "fullName": "Demo Buyer"
}
~~~

### Add an item to the cart

~~~http
POST /api/v1/cart/items
Authorization: Bearer <customer-access-token>
Content-Type: application/json

{
  "productId": "<existing-product-uuid>",
  "quantity": 2
}
~~~

### Checkout with an idempotency key

~~~http
POST /api/v1/checkout
Authorization: Bearer <customer-access-token>
Idempotency-Key: checkout-demo-0001
~~~

Reuse the same key when retrying the same checkout after a timeout. Use a new key for a distinct checkout.

### Approve a simulated payment

~~~http
POST /api/v1/orders/<order-id>/payments
Authorization: Bearer <customer-access-token>
Idempotency-Key: payment-demo-0001
Content-Type: application/json

{
  "scenario": "APPROVE"
}
~~~

The other supported scenario is **DECLINE**. Repeating a request with the same key returns the original attempt.

## Postman

An import-ready collection demonstrates the API's end-to-end flow, including admin login, customer registration, catalogue, cart, checkout, payment idempotency, decline/cancellation, and product maintenance.

- [Postman collection](postman/Ecommerce-Backend-API.postman_collection.json)
- [Local environment template](postman/Ecommerce-Local.postman_environment.json)
- [Import and run instructions](postman/README.md)

## Repository layout

~~~text
src/main/java/com/sardor/ecommerce/
├── auth/        # registration and login
├── cart/        # customer shopping carts
├── common/      # API errors and domain exceptions
├── config/      # OpenAPI, bootstrap admin and rate limiting
├── inventory/   # stock movement audit ledger
├── order/       # checkout, order history, cancellation and expiry
├── payment/     # provider abstraction and simulated payment
├── product/     # catalogue and admin product management
├── security/    # stateless JWT authentication and authorization
└── user/        # customer/admin identity
~~~

## Security and demo boundaries

- Never commit **.env**, real credentials, JWT signing keys, or metrics tokens.
- Admin bootstrap is disabled by default and should only be enabled for local/demo setup.
- Replace all example credentials before running in any shared environment.
- The payment implementation is a deterministic fake provider. It is not a payment gateway and must not be used to collect card data.
- A real payment integration is deliberately out of scope; it would need signed webhook verification, upstream idempotency, timeout reconciliation, and settlement controls.

## Project status

The core portfolio scope is implemented and automated in CI. Remaining items that require external accounts (for example, hosted monitoring or a real payment provider) are intentionally outside this free, local-first portfolio project.

See the [delivery roadmap](docs/roadmap.md) for implementation status.
