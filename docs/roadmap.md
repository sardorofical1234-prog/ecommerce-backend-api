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
- [x] PostgreSQL integration coverage of Flyway migrations

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
- [x] PostgreSQL integration test for two-customer last-unit race
- [ ] Additional rollback/failure-injection cases
- [x] Docker Compose HTTP smoke test covering admin/customer auth, catalogue, cart, checkout, payment, idempotency and persisted stock/order state

## Iteration 4 — Payment simulation and hardening

- [x] Payment provider interface and deterministic fake provider
- [x] Payment attempt records; no card data storage
- [x] Payment failure / retry handling and order PAID transition
- [x] Payment idempotency and order-level locking
- [x] Customer payment history endpoint
- [x] PostgreSQL end-to-end payment integration test
- [x] Basic role-based API security integration tests
- [ ] Provider-side idempotency, webhook validation and reconciliation (required before a real provider)

## Iteration 5 — Operational quality

- [x] In-memory IP rate limiting for auth, checkout and payment routes
- [x] Health, liveness and readiness endpoints
- [x] Actuator metrics endpoint
- [ ] Redis-backed rate limiting for multi-instance deployments
- [ ] Structured logs and correlation IDs
- [ ] Dashboard and external observability integration
- [ ] Architecture decision records and deployment guide
