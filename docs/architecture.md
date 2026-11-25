# Architecture

## Style and boundaries

The application is a **modular monolith**: one Spring Boot deployment owns one PostgreSQL database. Domain packages establish maintainable boundaries while allowing checkout and inventory operations to share a single ACID transaction. This project does not split tightly coupled transactional code into microservices.

## Component view

~~~mermaid
flowchart LR
    C[API client / Postman] --> S[Spring Security 7<br/>JWT validation and role checks]
    S --> RL[Rate limiter<br/>Redis Lua / Caffeine]
    RL --> API[Spring MVC controllers]

    subgraph APP[Spring Boot modular monolith]
      API --> AUTH[auth / user]
      API --> CAT[product]
      API --> CART[cart]
      API --> ORD[order / checkout]
      ORD --> INV[inventory ledger]
      API --> PAY[payment]
      OBS[Actuator / Micrometer<br/>request IDs and JSON logs]
    end

    AUTH --> DB[(PostgreSQL 17)]
    CAT --> DB
    CART --> DB
    ORD --> DB
    INV --> DB
    PAY --> DB
    RL <--> REDIS[(Redis 7)]
    OBS --> PROM[Prometheus]
    PROM --> GRAF[Grafana dashboard]
    PAY -. demo only .-> FAKE[Deterministic fake provider]
~~~

## Modules

| Module | Responsibility |
|---|---|
| auth / user | Registration, login, customer identity and roles |
| product | Public product catalogue, SKU, price, stock and active state |
| cart | One cart per customer, cart items and estimated totals |
| order | Checkout, immutable order-item snapshots, order state and expiration |
| inventory | Stock reservation, release and movement audit trail |
| payment | Provider abstraction, payment attempts and simulated provider |
| operations | Health probes, metrics, request correlation and rate limiting |

## Checkout sequence

~~~mermaid
sequenceDiagram
    autonumber
    actor Customer
    participant Security as Security filter chain
    participant API as CheckoutController
    participant Service as CheckoutService
    participant DB as PostgreSQL

    Customer->>Security: POST /api/v1/checkout + JWT + Idempotency-Key
    Security->>Security: Validate token and CUSTOMER role
    Security->>API: Authenticated principal
    API->>Service: checkout(customerId, idempotencyKey)
    Service->>DB: Lock customer cart and product rows
    Service->>DB: Validate current stock and calculate total
    Service->>DB: Insert order and immutable order items
    Service->>DB: Decrement stock and append RESERVED movements
    Service->>DB: Clear cart
    DB-->>Service: Commit transaction
    Service-->>Customer: PENDING_PAYMENT order response
~~~

## Cart behavior and invariants

1. The JWT subject is the customer identity. Cart endpoints never accept a customer ID from the request.
2. A unique constraint on shopping_carts.user_id guarantees at most one cart per customer.
3. The customer row is pessimistically locked before cart lookup/creation; the cart row is locked for mutations.
4. A cart item is unique by (cart_id, product_id); repeated additions increment its quantity.
5. Cart prices are not persisted as authoritative prices. The API calculates estimated line totals from current product prices.
6. Cart contents do not reserve stock. Checkout revalidates product state and stock.
7. Unavailable/deactivated lines are flagged in the cart response and can still be removed.

## Checkout and order invariants

1. Checkout is idempotent per customer: unique (customer_id, idempotency_key) prevents duplicate orders.
2. The server reads persisted cart quantities and locked product prices. Client totals are never accepted.
3. Each order item stores product ID, SKU, name, unit price, quantity and line-total snapshots.
4. Product rows are locked in ascending UUID order. Availability checks, stock decrements, order creation, inventory ledger writes and cart clearing share one database transaction.
5. If any product is inactive or lacks stock, the transaction fails; no partial order is committed and the cart is not cleared.
6. New orders enter PENDING_PAYMENT with a 30-minute payment window. Cancellation or expiration releases reserved inventory exactly once.
7. The expiration worker locks eligible orders before releasing stock. Release and state transition happen in one transaction.
8. Inventory movements are append-only records with movement type, quantity and resulting stock.

## Payment invariants

1. The API accepts only APPROVE / DECLINE demo scenarios, not card numbers, CVVs or bank credentials.
2. Each attempt stores the order, server-calculated amount, provider reference, result and optional failure details.
3. A unique (order_id, idempotency_key) constraint and order-level pessimistic lock protect retries and paid orders.
4. A declined attempt leaves the order PENDING_PAYMENT; a new idempotency key may be used while the payment window remains open.
5. A successful attempt changes the order to PAID; stock remains deducted.
6. Payment history is scoped to the customer who owns the order.
7. The provider interface receives the payment idempotency key. A real adapter would need to forward it upstream.
8. Provider timeout is returned as HTTP 502 and does not persist a false decline. A real adapter would need to reconcile an ambiguous provider outcome before accepting another attempt.

## Reliability and operations

- Testcontainers starts PostgreSQL 17 and Redis 7; Flyway applies the real migration chain.
- A trigger-based failure-injection integration test verifies that order creation, stock changes, inventory ledger writes and cart clearing roll back together.
- A parallel checkout integration test verifies that only one customer can reserve the final stock unit.
- Redis integration tests use independent counter instances and verify the same atomic rate-limit window.
- Redis fixed-window rate limiting uses one Lua script (INCR + PEXPIRE) so increment and TTL initialization are atomic.
- Caffeine memory mode is available for a single-instance process. Redis mode shares counters across API instances.
- Redis unavailability fails closed with HTTP 503 rather than silently bypassing the limiter.
- Request IDs are validated or generated, stored in MDC and emitted in Logstash JSON logs.
- Actuator exposes health, liveness, readiness, metrics and Prometheus exposition. The Prometheus endpoint uses dedicated Basic Auth credentials.

## Security

- Passwords are hashed with BCrypt (work factor 12).
- Access tokens use HS256 and expire after 15 minutes.
- JWT signing material is supplied through environment variables and must decode to at least 32 bytes.
- Public registration always creates CUSTOMER; admin bootstrap is an explicit environment-only option.
- Product writes are ADMIN-only; customer cart/order/payment data is scoped to the JWT subject.
- Product deletion is a soft deactivation, preserving historical references.

## Persistence

Flyway owns the schema. Hibernate uses ddl-auto=validate and must not mutate schemas automatically. All timestamps use TIMESTAMPTZ and are represented as UTC instants.

See [data-model.md](data-model.md) for the schema-level ER diagram.
