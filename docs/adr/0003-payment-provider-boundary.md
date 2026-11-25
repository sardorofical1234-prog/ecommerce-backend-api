# ADR 0003: Isolate payment behind an idempotent provider boundary

- **Status:** Accepted
- **Date:** 2026-11-25

## Context

A payment request can time out after an upstream provider has processed a charge but before the API receives the response. Retrying with a new identity can create duplicate charges. The repository currently uses a simulator and must not collect payment card details.

## Decision

Keep a PaymentProvider interface that receives order ID, server-calculated amount, payment idempotency key and a demo scenario. The simulator returns deterministic approve/decline outcomes. A provider exception maps to HTTP 502 and does not create a false declined attempt; the client retries with the same idempotency key. A real provider adapter must forward that key upstream and reconcile ambiguous outcomes.

## Consequences

- Provider-specific code stays outside the order/payment domain service.
- The simulator is safe for portfolio and automated test flows, not real commerce.
- Real payment launch is blocked until signed webhook handling, replay protection, outbox/reconciliation, refund and settlement processes are implemented and reviewed.
