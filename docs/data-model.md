# Database model

The ER diagram below is derived from the committed Flyway migrations in **src/main/resources/db/migration** (V1–V5). PostgreSQL is the source of truth; Hibernate runs with schema validation rather than automatic DDL mutation.

## Entity relationship diagram

~~~mermaid
erDiagram
    APP_USERS ||--o| SHOPPING_CARTS : owns
    APP_USERS ||--o{ ORDERS : places
    SHOPPING_CARTS ||--o{ CART_ITEMS : contains
    PRODUCTS ||--o{ CART_ITEMS : referenced_by
    ORDERS ||--o{ ORDER_ITEMS : snapshots
    PRODUCTS ||--o{ ORDER_ITEMS : referenced_by
    PRODUCTS ||--o{ INVENTORY_MOVEMENTS : tracks
    ORDERS ||--o{ INVENTORY_MOVEMENTS : causes
    ORDERS ||--o{ PAYMENT_ATTEMPTS : receives

    APP_USERS {
        uuid id PK
        varchar email UK
        varchar password_hash
        varchar full_name
        varchar role
        timestamptz created_at
    }

    PRODUCTS {
        uuid id PK
        varchar sku UK
        varchar name
        text description
        varchar category
        numeric price
        integer stock
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    SHOPPING_CARTS {
        uuid id PK
        uuid user_id FK
    }

    CART_ITEMS {
        uuid id PK
        uuid cart_id FK
        uuid product_id FK
        integer quantity
    }

    ORDERS {
        uuid id PK
        uuid customer_id FK
        varchar idempotency_key
        varchar status
        numeric total_amount
        timestamptz created_at
        timestamptz expires_at
        timestamptz paid_at
        timestamptz closed_at
    }

    ORDER_ITEMS {
        uuid id PK
        uuid order_id FK
        uuid product_id FK
        varchar product_sku
        varchar product_name
        numeric unit_price
        integer quantity
        numeric line_total
    }

    INVENTORY_MOVEMENTS {
        uuid id PK
        uuid product_id FK
        uuid order_id FK
        varchar type
        integer quantity
        integer stock_after
        timestamptz created_at
    }

    PAYMENT_ATTEMPTS {
        uuid id PK
        uuid order_id FK
        varchar idempotency_key
        varchar status
        numeric amount
        varchar provider_reference UK
        varchar failure_code
        varchar failure_message
        timestamptz created_at
        timestamptz completed_at
    }
~~~

## Table responsibilities

| Table | Responsibility | Important constraints |
|---|---|---|
| app_users | Customer and administrator identities | Unique email; role restricted to CUSTOMER / ADMIN |
| products | Current catalogue and stock balance | Unique SKU; price must be positive; stock cannot be negative |
| shopping_carts | One cart per customer | Unique user_id; user deletion cascades |
| cart_items | Current cart contents | Unique (cart_id, product_id); quantity 1–999 |
| orders | Checkout aggregate and lifecycle | Unique (customer_id, idempotency_key); pending, cancelled, expired or paid |
| order_items | Historical product and price snapshot | References order and product; quantity and monetary values positive |
| inventory_movements | Append-only stock reservation/release audit | Type RESERVED / RELEASED; positive quantity; stock_after non-negative |
| payment_attempts | Simulated payment attempts | Unique (order_id, idempotency_key); provider reference unique; at most one successful attempt per order |

## Consistency notes

- **Cart is not inventory.** Adding a cart item does not decrement or reserve product stock.
- **Checkout is atomic.** Order, order items, product stock updates, inventory movements and cart clearing commit or roll back together.
- **Order items are snapshots.** The product foreign key preserves identity, while SKU/name/unit price preserve the values seen at checkout.
- **Inventory movements are audit records.** A reservation records a negative stock operation in the product balance and a RESERVED movement; cancellation/expiry adds a RELEASED movement.
- **Idempotency is scoped.** Checkout keys are unique per customer; payment keys are unique per order.
- **Payment state is separate from order state.** A declined attempt is retained as an attempt while the order can remain PENDING_PAYMENT. A successful attempt marks the order PAID.

## Source migrations

- V1: users and products
- V2: shopping carts and cart items
- V3: orders and order items
- V4: inventory movement ledger
- V5: payment attempts and PAID order state
