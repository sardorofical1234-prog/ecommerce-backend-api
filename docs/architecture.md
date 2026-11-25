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
| `payment` | Payment-provider abstraction and safe simulated provider | Planned |

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

## Important invariants for payment

1. A future payment module must not trust totals or payment status supplied by the client.
2. A payment retry requires a provider idempotency key; payment attempt records must be linked to an order.
3. Payment success must lock the order and verify it is still pending and unexpired before transitioning to `PAID`.
4. Payment processing is simulated in this portfolio project. No real card numbers or payment credentials are stored.

## Security

- Passwords are hashed with BCrypt (work factor 12).
- Access tokens are signed with HS256 and expire after 15 minutes.
- JWT signing material is provided only through the environment and must decode to at least 32 bytes.
- Public registration always creates a `CUSTOMER`; admin creation is an explicit bootstrap-only environment option.
- Product writes are restricted to `ADMIN`; product reads are public.
- Cart, checkout and order endpoints are restricted to the `CUSTOMER` role and scope data to the JWT subject.
- Product removal is a soft deactivation, preserving historical references for future orders.

## Persistence

Flyway owns the schema. Hibernate runs with `ddl-auto=validate`; it must not silently mutate production-like schemas. All timestamps are stored as `TIMESTAMPTZ` and represented as UTC instants.
