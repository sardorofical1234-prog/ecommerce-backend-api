# ADR 0001: Use a modular monolith

- **Status:** Accepted
- **Date:** 2026-11-25

## Context

The API contains tightly related customer, catalogue, cart, order, inventory and payment-demo capabilities. Checkout must reserve inventory, create order snapshots, append inventory movements and clear a cart atomically. Splitting these operations across independently deployed services would require distributed transactions or compensating workflows before there is a demonstrated scaling need.

## Decision

Keep one deployable Spring Boot application and one PostgreSQL database, with package-level modules for auth/user, product, cart, order, inventory, payment and operations. Enforce boundaries through focused services, repositories and explicit ownership checks.

## Consequences

- Checkout and inventory changes can use one ACID transaction.
- Local development, CI and deployment remain simpler than a microservice fleet.
- Modules can be extracted later when independent scaling or team ownership justifies the operational cost.
- The application must avoid cross-module persistence shortcuts that make later extraction harder.
