# ADR 0002: Reserve inventory atomically during checkout

- **Status:** Accepted
- **Date:** 2026-11-25

## Context

Two customers may attempt to buy the final units of the same product at the same time. A cart is only an estimate and does not reserve stock. The order must preserve the product name, SKU and price seen at checkout, while inventory movements provide an audit trail.

## Decision

Checkout runs in one database transaction. It locks the customer/cart, then locks product rows in ascending UUID order, revalidates active state and stock, calculates totals on the server, creates immutable order-item snapshots, decrements stock, appends RESERVED ledger rows and clears the cart. A unique customer/idempotency-key constraint makes retries return the original order. Pending orders can be cancelled or expire after 30 minutes; stock is restored once with RELEASED ledger rows.

## Consequences

- Database transactions and row-level locks prevent overselling and partial checkout state.
- Stable lock ordering reduces (but does not eliminate all possible) deadlock risk.
- Product hot spots can increase transaction wait time under heavy demand.
- PostgreSQL concurrency and failure-injection tests are required for changes to this flow.
- A future distributed checkout architecture would need explicit reservation ownership, expiration and compensation semantics.
