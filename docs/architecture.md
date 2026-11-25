# Architecture

## Style

The application is a **modular monolith**, not a collection of prematurely separated microservices. One deployable application owns one PostgreSQL database. Domain packages create clear boundaries while allowing ACID transactions where checkout and inventory require them.

## Modules

| Module | Responsibility | Status |
|---|---|---|
| `auth` / `user` | Registration, login, customer identity and roles | Implemented |
| `product` | Product catalogue, SKU, price, stock and active state | Implemented |
| `cart` | One customer cart, cart items and estimated totals | Implemented |
| `order` | Checkout, immutable order-item price snapshots, order state machine | Planned |
| `inventory` | Stock reservation, release and audit trail | Planned |
| `payment` | Payment-provider abstraction and safe simulated provider | Planned |

## Cart behavior and invariants

1. The JWT subject is the customer identity. Cart endpoints do not accept a customer ID from the request body or URL.
2. A unique `shopping_carts.user_id` constraint guarantees at most one cart per customer.
3. The customer row is pessimistically locked before cart lookup/creation, preventing concurrent first requests from creating duplicate carts. The cart row is then locked for mutations.
4. A cart item is unique by `(cart_id, product_id)`; repeated additions increment the existing quantity.
5. Cart prices are **not** persisted as authoritative prices. The API calculates line totals from the current product price. The cart total is an estimate, not a checkout quote.
6. Product active state and stock are checked when an item is added or its quantity changes. Cart contents do not reserve stock; checkout must revalidate and reserve inventory atomically.
7. If a product is deactivated or stock falls below the requested quantity after it entered a cart, the cart response marks that line as unavailable. Customers can still remove it.

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
- Cart operations are restricted to the `CUSTOMER` role.
- Product removal is a soft deactivation, preserving historical references for future orders.

## Persistence

Flyway owns the schema. Hibernate runs with `ddl-auto=validate`; it must not silently mutate production-like schemas. All timestamps are stored as `TIMESTAMPTZ` and represented as UTC instants.
