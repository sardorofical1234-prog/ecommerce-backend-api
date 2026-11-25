# Architecture

## Style

The application is a **modular monolith**, not a collection of prematurely separated microservices. One deployable application owns one PostgreSQL database. Domain packages create clear boundaries while allowing ACID transactions where checkout and inventory require them.

## Modules

| Module | Responsibility | Status |
|---|---|---|
| `auth` / `user` | Registration, login, customer identity and roles | Implemented |
| `product` | Product catalogue, SKU, price, stock and active state | Implemented |
| `cart` | One customer cart, cart items and estimated totals | Implemented |
| `order` | Checkout, immutable order-item snapshots, order state and expiration | Implemented |
| `inventory` | Stock reservation, release and movement audit trail | Implemented |
| `payment` | Provider abstraction, payment attempts and simulated provider | Implemented |
| `operations` | Health probes, metrics, request correlation and Redis-backed request rate limiting | Implemented |

## Cart behavior and invariants

1. The JWT subject is the customer identity. Cart endpoints do not accept a customer ID from the request body or URL.
2. A unique `shopping_carts.user_id` constraint guarantees at most one cart per customer.
3. The customer row is pessimistically locked before cart lookup/creation, preventing concurrent first requests from creating duplicate carts. The cart row is then locked for mutations.
4. A cart item is unique by `(cart_id, product_id)`; repeated additions increment the existing quantity.
5. Cart prices are **not** persisted as authoritative prices. The API calculates line totals from the current product price. The cart total is an estimate, not a checkout quote.
6. Product active state and stock are checked when an item is added or its quantity changes. Cart contents do not reserve stock; checkout must revalidate and reserve inventory atomically.
7. If a product is deactivated or stock falls below the requested quantity after it entered a cart, the cart response marks that line as unavailable. Customers can still remove it.

## Checkout and order invariants

1. Checkout is idempotent per customer: a unique `(customer_id, idempotency_key)` constraint ensures retries cannot create a second order.
2. The server reads prices and quantities from the persisted cart and locked product rows. Client-supplied totals are never accepted.
3. Each order item stores immutable product ID, SKU, product name, unit price, quantity and line total snapshots. Catalogue edits do not rewrite order history.
4. Product rows are locked in ascending UUID order using pessimistic write locks. Availability checks, stock decrements, order creation, inventory ledger writes and cart clearing occur in a single database transaction.
5. If any product is inactive or lacks stock, the entire transaction fails; the order is not created and the cart is not cleared.
6. New orders enter `PENDING_PAYMENT` with a 30-minute payment window. Customer cancellation or automatic expiration releases the reserved inventory exactly once.
7. The expiration worker locks eligible pending orders before releasing stock. Inventory release and order state transition happen in the same transaction.
8. Inventory movements are append-only records with movement type, quantity and resulting stock. The current product stock remains the operational balance.

## Payment invariants

1. The payment API accepts only a demo scenario (`APPROVE` / `DECLINE`), not card numbers, CVVs, bank credentials or client-calculated totals.
2. Each attempt stores the order, server-calculated amount, provider reference, result and optional failure details.
3. A unique `(order_id, idempotency_key)` constraint and order-level pessimistic lock ensure a retry returns the original attempt and prevents a second successful attempt for an already-paid order.
4. A declined attempt leaves the order in `PENDING_PAYMENT`; a new idempotency key can be used for a later attempt while the payment window remains open.
5. A successful attempt changes the order to `PAID`. Inventory remains deducted because the sale is now complete.
6. Payment attempts are visible only to the customer who owns the order.
7. The provider interface receives the payment idempotency key; each real gateway adapter must forward it upstream so ambiguous retries cannot create duplicate charges.
8. A provider timeout is surfaced as HTTP 502 and does not persist a false declined attempt. Clients must retry with the same idempotency key; a real gateway adapter must reconcile ambiguous provider outcomes before accepting a new attempt.

## Reliability and operations

- Testcontainers integration tests launch a clean PostgreSQL 17 instance and run all Flyway migrations before application startup.
- The API test suite covers a full customer checkout/payment flow and verifies the resulting stock, order, payment attempt and inventory audit rows in PostgreSQL.
- A PostgreSQL trigger-based failure-injection test forces the inventory ledger write to fail and verifies the order insert, stock decrement and cart clear all roll back.
- Payment unit tests cover provider timeout followed by a retry with the exact same idempotency key.
- Each HTTP request receives a validated or generated `X-Request-Id`; the value is added to MDC and emitted in Logstash JSON logs.
- Redis integration tests use two independent counter instances against the same Redis container and verify they observe one shared atomic window.
- A concurrency test runs two independent customer checkouts against one remaining unit. Exactly one transaction may reserve it.
- The default Compose deployment uses a Redis fixed-window counter with one atomic Lua script (`INCR` + `PEXPIRE`) keyed by remote IP and endpoint group.
- A Caffeine-backed in-memory counter remains available for a single-instance process.
- Authentication, checkout and payment creation are rate limited; read-only catalogue/order routes are not.
- Redis counters are shared by multiple application instances; each increment and TTL initialization is atomic.
- Redis is private to the Compose network. In production, use managed Redis with authentication/TLS. Redis failure is fail-closed and returns HTTP 503.
- Prometheus exposition is authenticated with the dedicated `prometheus` Basic Auth identity, compared in constant time; the token is separate from user JWTs.
- Actuator exposes health, liveness, readiness, metrics and Prometheus exposition endpoints. Health probes are public; general metrics remain authenticated; Prometheus exposition requires a dedicated static Basic Auth secret.
- Console output uses Spring Boot's Logstash structured JSON format, which includes MDC correlation fields.

## Security

- Passwords are hashed with BCrypt (work factor 12).
- Access tokens are signed with HS256 and expire after 15 minutes.
- JWT signing material is provided only through the environment and must decode to at least 32 bytes.
- Public registration always creates a `CUSTOMER`; admin creation is an explicit bootstrap-only environment option.
- Product writes are restricted to `ADMIN`; product reads are public.
- Cart, checkout, order and payment endpoints are restricted to the `CUSTOMER` role and scope data to the JWT subject.
- Product removal is a soft deactivation, preserving historical references for future orders.

## Persistence

Flyway owns the schema. Hibernate runs with `ddl-auto=validate`; it must not silently mutate production-like schemas. All timestamps are stored as `TIMESTAMPTZ` and represented as UTC instants.
