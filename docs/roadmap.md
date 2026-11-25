# Delivery roadmap

## Iteration 1 — Foundation and catalogue

- [x] Java 21 / Spring Boot application skeleton
- [x] PostgreSQL and Flyway baseline migration
- [x] Customer registration and login with JWT access tokens
- [x] Public product search, pagination and product details
- [x] Admin product create/update/deactivate endpoints
- [x] Docker Compose, non-root container and CI workflow
- [x] Initial unit tests

## Iteration 2 — Shopping cart

- [x] One cart per customer
- [x] Add, update and remove cart items
- [x] Recalculate estimated total from current product prices
- [x] Check active state and available stock when adding/updating items
- [x] Prevent cross-customer cart access by deriving owner from JWT
- [x] Serialize cart creation and mutations using database row locks
- [x] Unit tests for cart operations
- [ ] Integration tests against PostgreSQL/Testcontainers

## Iteration 3 — Checkout, orders and inventory

- [x] Atomic checkout transaction
- [x] Immutable order-item snapshots (SKU, name and price)
- [x] Stock reservation with sorted row-level locks
- [x] Idempotency key for checkout
- [x] Customer order history and order detail endpoints
- [x] Pending-order cancellation with inventory release
- [x] Inventory movement audit ledger
- [x] Automatic 30-minute order expiration and stock release
- [x] Unit tests for checkout, idempotency, stock and expiration
- [ ] PostgreSQL integration tests for migrations and rollback
- [ ] Concurrency tests proving no overselling and no duplicate checkout

## Iteration 4 — Payment simulation and hardening

- [x] Payment provider interface and deterministic fake provider
- [x] Payment attempt records; no card data storage
- [x] Payment failure / retry handling and order PAID transition
- [x] Payment idempotency and order-level locking
- [x] Customer payment history endpoint
- [ ] Security and authorization integration tests
- [ ] OpenAPI examples, Newman/Postman collection and portfolio docs
- [ ] Provider-side idempotency, webhook validation and reconciliation (required before a real provider)

## Iteration 5 — Operational quality

- [ ] Structured logs and correlation IDs
- [ ] Health/readiness endpoints
- [ ] Metrics and observability
- [ ] Architecture decision records and deployment guide
