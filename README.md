# E-commerce Backend API

A portfolio-grade e-commerce REST API built as a **modular monolith** with Java 21, Spring Boot 4.1.3 and PostgreSQL 17.

> Current scope: customer authentication, product catalogue, shopping cart, checkout, order history, inventory reservation and simulated payment. The payment provider is intentionally fake: this project does not process real payments or store card data.

## Technology

- Java 21
- Spring Boot 4.1.3 / Spring MVC / Actuator
- Spring Security 7 / JWT (HS256)
- Spring Data JPA / Hibernate
- PostgreSQL 17 / Flyway
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
- Payment requests require an `Idempotency-Key`; repeated keys return the original attempt
- A successful attempt transitions the order from `PENDING_PAYMENT` to `PAID`
- Failed attempts leave the order pending so a new idempotency key can retry
- Order row locking prevents a second successful charge on an already-paid order
- Customer can view payment-attempt history for their own order

### Reliability and operations

- PostgreSQL integration tests use Testcontainers and the real Flyway migration chain
- Parallel checkout test verifies two customers cannot buy the last inventory unit
- Security integration tests verify unauthenticated and wrong-role access is rejected
- Fixed-window IP rate limiting on registration/login, checkout and payment creation
- Actuator health, liveness, readiness and metrics endpoints

## Run with Docker Compose

Prerequisites: Docker Engine / Docker Desktop with Compose v2.

1. Copy `.env.example` to `.env`.
2. Set a strong local `DB_PASSWORD`.
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

The API listens on port `8080`; PostgreSQL is exposed to the host on port `5433`. The database uses a named Docker volume. **Do not run `docker compose down -v` if you need to preserve local database data.**

To stop without deleting data:

```bash
docker compose down
```

## Run tests

With Java 21, Maven and a working Docker Engine available:

```bash
mvn clean verify
```

Testcontainers starts an isolated PostgreSQL 17 container for the full application integration tests. Docker is required for the complete test suite. Unit tests cover authentication, product catalogue, cart operations, checkout idempotency, inventory reservation, order expiration, payment outcomes and rate limiting.

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

The in-memory fixed-window limiter applies per remote IP address:
- Authentication endpoints: 20 POST requests per minute
- Checkout: 10 POST requests per minute
- Payment creation: 10 POST requests per minute

A rejected request returns HTTP `429` and a `Retry-After` header. Configure `RATE_LIMIT_ENABLED=false` to disable it or override `app.rate-limit.*` settings. This limiter is local to one application instance. For multi-instance production deployments, replace it with a shared Redis-backed limiter and configure the trusted reverse proxy so client IP addresses cannot be spoofed.

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
- Payment provider calls are currently local and deterministic. A real gateway integration would need provider-side idempotency, webhook signature verification, outbox/transaction coordination and reconciliation.

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

## Architecture and roadmap

- [Architecture and invariants](docs/architecture.md)
- [Implementation roadmap](docs/roadmap.md)
