# Postman setup

This collection exercises the local API. It does not call any paid service and uses the deterministic fake payment provider only.

## 1. Start the local API

From the repository root:

1. Copy .env.example to .env.
2. Set DB_PASSWORD and replace JWT_SECRET_BASE64 with output from openssl rand -base64 32.
3. Enable the bootstrap admin for this local demo:

   ~~~dotenv
   BOOTSTRAP_ADMIN_ENABLED=true
   BOOTSTRAP_ADMIN_EMAIL=portfolio-admin@example.com
   BOOTSTRAP_ADMIN_PASSWORD=PortfolioAdminPass-2026
   BOOTSTRAP_ADMIN_FULL_NAME=Portfolio Admin
   ~~~

4. Start Docker Compose:

   ~~~bash
   docker compose up --build -d
   ~~~

5. Confirm readiness at http://localhost:8080/actuator/health/readiness.

Use a unique local admin password. These example credentials are for a disposable local database only; do not reuse them elsewhere.

## 2. Import

Import these two files into Postman:

- Ecommerce-Backend-API.postman_collection.json
- Ecommerce-Local.postman_environment.json

Select the **Ecommerce API - Local** environment. Set adminEmail and adminPassword to the same values configured in .env. Set customerPassword to a local demo password of at least 12 characters. The baseUrl defaults to http://localhost:8080.

## 3. Run the collection

Run requests in the listed order, or run the full collection:

1. Admin login generates a fresh run suffix and clears previous run tokens/IDs.
2. Create a demo product.
3. Register a fresh customer and save the JWT.
4. Read customer and product details.
5. Add to cart and inspect cart state.
6. Checkout and replay the same checkout key to demonstrate idempotency.
7. Approve a simulated payment and replay the payment key with a different scenario; the original attempt should be returned.
8. Read payment history and order history/details.
9. Create a second order, simulate a decline, then cancel it to demonstrate inventory release.
10. Update and deactivate the demo product.

The collection saves access tokens and generated entity IDs automatically. It uses one new customer email, product SKU and idempotency-key set per collection run.

## Notes

- Registration permits CUSTOMER only. The admin user must be created through local bootstrap.
- Checkout and payment requests require the Idempotency-Key header.
- The API rate-limits auth, checkout and payment creation. Do not repeatedly hammer the collection; wait for the Retry-After period if you receive HTTP 429.
- Payment APPROVE / DECLINE are deterministic demo outcomes, not real payment processing.
- Stop the stack with docker compose down. This preserves the local PostgreSQL volume.
