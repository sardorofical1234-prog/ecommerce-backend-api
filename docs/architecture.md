# Architecture

## Style

The first release is a **modular monolith**, not a collection of prematurely separated microservices. One deployable application owns one PostgreSQL database. Domain packages create clear boundaries while allowing ACID transactions where checkout and inventory require them.

## Planned modules

| Module | Responsibility | Initial status |
|---|---|---|
| `auth` / `user` | Registration, login, customer identity and roles | Implemented |
| `product` | Product catalogue, SKU, price, stock and active state | Implemented (catalogue CRUD) |
| `cart` | Customer cart and cart items | Planned |
| `order` | Checkout, immutable order-item price snapshots, order state machine | Planned |
| `inventory` | Stock reservation, release and audit trail | Planned |
| `payment` | Payment-provider abstraction and safe simulated provider | Planned |

## Important invariants for checkout

1. A product price is copied into an order item at checkout; later catalogue edits must not rewrite historical orders.
2. The order total is calculated server-side from persisted prices and quantities. Client-supplied totals are never trusted.
3. Stock reservation and order creation must be atomic. Product rows should be locked in a stable order (`SELECT ... FOR UPDATE`) or updated with a guarded statement to prevent overselling.
4. A checkout request needs an idempotency key so a retried HTTP request cannot create a second order or charge.
5. Payment is simulated in this portfolio project. No real card numbers or payment credentials are stored.
6. Order status changes follow an explicit state machine; clients cannot arbitrarily set `PAID`, `SHIPPED`, or `CANCELLED`.

## Security

- Passwords are hashed with BCrypt (work factor 12).
- Access tokens are signed with HS256 and expire after 15 minutes.
- JWT signing material is provided only through the environment and must decode to at least 32 bytes.
- Public registration always creates a `CUSTOMER`; admin creation is an explicit bootstrap-only environment option.
- Product writes are restricted to `ADMIN`; product reads are public.
- Product removal is a soft deactivation, preserving historical references for future orders.

## Persistence

Flyway owns the schema. Hibernate runs with `ddl-auto=validate`; it must not silently mutate production-like schemas. All timestamps are stored as `TIMESTAMPTZ` and represented as UTC instants.
