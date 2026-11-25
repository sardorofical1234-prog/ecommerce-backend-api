# E-commerce Backend API

A portfolio-grade e-commerce REST API built as a **modular monolith** with Java 21, Spring Boot 4.1.3 and PostgreSQL 17.

> Current scope: customer authentication, product catalogue, shopping cart, checkout, order history, inventory reservation and simulated payment. The payment provider is intentionally fake: this project does not process real payments or store card data.

## Technology

- Java 21
- Spring Boot 4.1.3 / Spring MVC / Actuator
- Spring Security 7 / JWT (HS256)
- Spring Data JPA / Hibernate
- PostgreSQL 17 / Flyway
- Redis 7 / Spring Data Redis (atomic distributed rate limiting)
- Micrometer Prometheus registry for secured metrics scraping
- Bean Validation
- Springdoc OpenAPI / Swagger UI
- Maven, Docker, Docker Compose, GitHub Actions
- JUnit 5, Mockito and Testcontainers 2.0

## Implemented features

### Authentication and users

- Customer registration and login
- BCrypt password hashing (work factor 12)
- 15-minute JWT access tokens
- Role-based authorization (`CUSTOMER`, `ADMIN`)
- `GET /api/v1/users/me` for the authenticated principal
- Registration cannot assign the `ADMIN` role

### Product catalogue

- Public product listing with text search and pagination
- Public product details
- Admin-only create, update and deactivate operations
- SKU uniqueness and validation
- Positive price and non-negative stock constraints
- Soft deactivation instead of destructive deletion

### Shopping cart

- One persistent cart per customer
- Add a product; repeated additions increase its quantity
- Replace an item's quantity or remove it
- Server-side price calculation from the current product catalogue
- Stock and active-product checks when adding or updating
- JWT subject identifies the customer; client-supplied user IDs are never accepted
- Customer-row and cart-row pessimistic locks serialize cart creation and updates
- Cart responses flag lines that are no longer available; adding to a cart does not reserve stock

### Checkout, orders and inventory

- Checkout is atomic: order creation, inventory reservation and cart clearing share one database transaction
- Required `Idempotency-Key` prevents duplicate orders and duplicate stock deductions on client retries
- Product rows are pessimistically locked in stable UUID order before stock is reserved
- Server calculates totals and stores immutable product name, SKU, unit-price and quantity snapshots on order items
- Inventory reservation and release are recorded in an append-only movement ledger
- Customers can view their own order history and details
- Customer cancellation restores stock exactly once
- Pending orders expire after 30 minutes; a scheduled job releases their reserved stock

### Simulated payment

- Payment provider interface with a deterministic scenario-based fake provider
- Explicit demo scenarios: `APPROVE` and `DECLINE`; no card details or real credentials are accepted
- Payment attempts are persisted with amount, outcome, provider reference and failure details
- Provider adapters receive the same idempotency key so a real gateway can deduplicate retries after ambiguous timeouts
- Payment requests require an `Idempotency-Key`; repeated keys return the original attempt
- A successful attempt transitions the order from `PENDING_PAYMENT` to `PAID`
- Failed attempts leave the order pending so a new idempotency key can retry
- Order row locking prevents a second successful charge on an already-paid order
- Customer can view payment-attempt history for their own order

### Reliability and operations

- PostgreSQL integration tests use Testcontainers and the real Flyway migration chain
- Checkout failure-injection coverage verifies order, stock, inventory ledger and cart are rolled back together
- Provider timeout handling returns HTTP 502 and permits a retry with the same idempotency key
- HTTP requests emit an `X-Request-Id`; Logstash JSON logs include the request correlation ID via MDC
- Parallel checkout test verifies two customers cannot buy the last inventory unit
- Security integration tests verify unauthenticated and wrong-role access is rejected
- Atomic Redis-backed fixed-window IP rate limiting on registration/login, checkout and payment creation; Caffeine memory mode remains available for single-instance runs
- Actuator health, liveness, readiness and metrics endpoints
- Prometheus scrape endpoint protected by dedicated HTTP Basic credentials; Grafana dashboard template included

## Run with Docker Compose

Prerequisites: Docker Engine / Docker Desktop with Compose v2.

1. Copy `.env.example` to `.env`.
2. Set a strong local `DB_PASSWORD` and generate `METRICS_SCRAPE_TOKEN` with `openssl rand -hex 32` when a Prometheus scraper will be used.
3. Generate a 32-byte JWT secret and paste its Base64 output into `JWT_SECRET_BASE64`:

   ```bash
   openssl rand -base64 32
   ```

4. To manage the catalogue, enable the bootstrap admin and set a unique email and password (at least 12 characters):

   ```dotenv
   BOOTSTRAP_ADMIN_ENABLED=true
   BOOTSTRAP_ADMIN_EMAIL=admin@example.com
   BOOTSTRAP_ADMIN_PASSWORD=replace-with-a-long-unique-password
   BOOTSTRAP_ADMIN_FULL_NAME=Store Administrator
   ```

5. Start the services:

   ```bash
   docker compose up --build -d
   ```

6. Open Swagger UI: <http://localhost:8080/swagger-ui.html>

The API listens on port `8080`; PostgreSQL is exposed to the host on port `5433`. Redis is private to the Compose network. The database uses a named Docker volume. **Do not run `docker compose down -v` if you need to preserve local database data.**

To stop without deleting data:

```bash
docker compose down
```

## Run tests

With Java 21, Maven and a working Docker Engine available:

```bash
mvn clean verify
```

Testcontainers starts isolated PostgreSQL 17 and Redis 7 containers for the full application integration tests. Docker is required for the complete test suite. Unit tests cover authentication, product catalogue, cart operations, checkout idempotency, inventory reservation, order expiration, payment outcomes and rate limiting.

## Docker Compose smoke test

The same end-to-end HTTP smoke test can be run against a locally started Compose stack. It logs in with the bootstrapped admin, creates a product and customer, adds the product to the cart, checks out, approves a simulated payment, and verifies persisted order/payment state and remaining stock. It also checks checkout and payment idempotency.

1. Copy `.env.example` to `.env` and set a local database password and JWT secret.
2. Enable the bootstrap admin and set a unique email and password (at least 12 characters).
3. Start the stack with `docker compose up --build -d`.
4. Run `./scripts/compose-smoke-test.sh` (requires `curl` and `jq`).
5. Stop the stack with `docker compose down`; this preserves the database volume.

The GitHub Actions pipeline runs this same script against a fresh Docker Compose environment after the Maven/Testcontainers verification.

## API overview

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Public, rate limited | Register a customer |
| `POST` | `/api/v1/auth/login` | Public, rate limited | Get a JWT access token |
| `GET` | `/api/v1/users/me` | Authenticated | Current user and roles |
| `GET` | `/api/v1/products` | Public | Search / paginate active products |
| `GET` | `/api/v1/products/{id}` | Public | Get active product details |
| `POST` | `/api/v1/admin/products` | Admin | Create a product |
| `PUT` | `/api/v1/admin/products/{id}` | Admin | Update a product |
| `DELETE` | `/api/v1/admin/products/{id}` | Admin | Deactivate a product |
| `GET` | `/api/v1/cart` | Customer | Get current cart |
| `POST` | `/api/v1/cart/items` | Customer | Add a product / increase quantity |
| `PUT` | `/api/v1/cart/items/{productId}` | Customer | Set item quantity |
| `DELETE` | `/api/v1/cart/items/{productId}` | Customer | Remove item |
| `POST` | `/api/v1/checkout` | Customer, rate limited | Create order and reserve stock |
| `GET` | `/api/v1/orders` | Customer | List own recent orders |
| `GET` | `/api/v1/orders/{orderId}` | Customer | Get own order details |
| `POST` | `/api/v1/orders/{orderId}/cancel` | Customer | Cancel pending order and release stock |
| `POST` | `/api/v1/orders/{orderId}/payments` | Customer, rate limited | Run a simulated payment attempt |
| `GET` | `/api/v1/orders/{orderId}/payments` | Customer | List own payment attempts |
| `GET` | `/actuator/health` | Public | Application health |
| `GET` | `/actuator/health/liveness` | Public | Liveness probe |
| `GET` | `/actuator/health/readiness` | Public | Readiness probe |
| `GET` | `/actuator/metrics` | Authenticated | Application metrics |
| `GET` | `/actuator/prometheus` | Basic auth (`prometheus` + `METRICS_SCRAPE_TOKEN`) | Prometheus scrape format |
| `GET` | `/actuator/prometheus` | Prometheus scraper credentials | Prometheus text exposition |

### Example: register

```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "email": "buyer@example.com",
  "password": "a-unique-password-12",
  "fullName": "Demo Buyer"
}
```

### Example: add to cart

```http
POST /api/v1/cart/items
Authorization: Bearer <customer-access-token>
Content-Type: application/json

{
  "productId": "<existing-product-uuid>",
  "quantity": 2
}
```

### Example: checkout

```http
POST /api/v1/checkout
Authorization: Bearer <customer-access-token>
Idempotency-Key: checkout-2026-001
```

Send a unique key for each distinct checkout attempt. Reuse the same key when retrying the same request after a timeout. The response contains the order ID, status, expiration time, item snapshots and server-calculated total. Stock is reserved immediately; the payment window is 30 minutes.

### Example: approve a simulated payment

```http
POST /api/v1/orders/<order-id>/payments
Authorization: Bearer <customer-access-token>
Idempotency-Key: payment-attempt-001
Content-Type: application/json

{
  "scenario": "APPROVE"
}
```

For a simulated decline, use `"scenario": "DECLINE"` and a new idempotency key. Reusing an old key returns the original result even if a different scenario is sent. A successful result marks the order `PAID`; a declined result leaves it `PENDING_PAYMENT` for another attempt.

## Rate limiting

The fixed-window limiter applies per remote IP address:
- Authentication endpoints: 20 POST requests per minute
- Checkout: 10 POST requests per minute
- Payment creation: 10 POST requests per minute

A rejected request returns HTTP `429` and a `Retry-After` header. Docker Compose uses Redis 7 and an atomic Lua `INCR`/`PEXPIRE` script, so separate API instances share the same fixed-window counter. Configure `RATE_LIMIT_BACKEND=redis` for distributed deployments or `RATE_LIMIT_BACKEND=memory` for a single-instance process. `RATE_LIMIT_ENABLED=false` disables the limiter. Keep Redis on a private network, configure authentication/TLS in managed production Redis, and configure the trusted reverse proxy so client IP addresses cannot be spoofed. Redis unavailability fails closed with HTTP `503` rather than bypassing the limit.

## Database migrations

Flyway applies versioned SQL migrations from `src/main/resources/db/migration`. Hibernate is configured with `ddl-auto=validate`; schema changes must be committed as new migrations. Do not edit an already-applied migration in a shared database.

## Security and consistency notes

- Replace all local passwords and JWT signing material before exposing the service beyond localhost.
- Never commit `.env` or real secrets.
- Admin bootstrap is disabled by default and never resets an existing account password.
- Stock reservation, order creation, inventory movements and cart clearing execute in one transaction.
- Checkout locks product rows in a deterministic order to reduce deadlock risk and prevent overselling.
- Order-item prices are historical snapshots; later product edits do not change an existing order.
- Payment attempts are scoped to the order owner and protected by an order row lock.
- The simulated provider only supports test scenarios. It is not a real payment gateway and must not be used to collect card data.
- Payment provider calls are currently local and deterministic. The provider interface receives the payment idempotency key; a real adapter must forward it to the gateway. A real gateway integration still needs webhook signature verification, outbox/transaction coordination, timeout-state reconciliation and settlement checks.

## Project structure

```text
src/main/java/com/sardor/ecommerce/
├── auth/       # registration and login
├── cart/       # customer shopping carts
├── common/     # API errors and domain exceptions
├── config/     # OpenAPI, admin bootstrap and rate limiting
├── inventory/  # stock movement audit ledger
├── order/      # checkout, order history, cancellation, expiration
├── payment/    # provider abstraction and simulated payment attempts
├── product/    # public catalogue and admin product management
├── security/   # stateless JWT authentication and authorization
└── user/       # customer/admin identity
```

## Architecture, operations and roadmap

- [Observability and Grafana setup](docs/observability.md)
- [Architecture and invariants](docs/architecture.md)
- [Production deployment guide](docs/deployment.md)
- [Architecture decision records](docs/adr/)
- [Implementation roadmap](docs/roadmap.md)
