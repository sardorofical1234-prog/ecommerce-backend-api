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

- [ ] One active cart per customer
- [ ] Add, update and remove cart items
- [ ] Recalculate price and availability from the database
- [ ] Prevent cross-customer cart access
- [ ] Integration tests against PostgreSQL/Testcontainers

## Iteration 3 — Checkout, orders and inventory

- [ ] Checkout transaction and immutable order-item snapshots
- [ ] Stock reservation with row-level locking / guarded updates
- [ ] Idempotency key for checkout
- [ ] Order history and order detail endpoints
- [ ] Order lifecycle and cancellation rules
- [ ] Concurrency tests proving no overselling

## Iteration 4 — Payment simulation and hardening

- [ ] Payment provider interface and deterministic fake provider
- [ ] Payment attempt records; no card data storage
- [ ] Payment failure / retry handling
- [ ] API rate limiting and security tests
- [ ] OpenAPI examples, Newman/Postman collection and portfolio docs

## Iteration 5 — Operational quality

- [ ] Structured logs and correlation IDs
- [ ] Health/readiness endpoints
- [ ] Metrics and observability
- [ ] Architecture decision records and deployment guide
