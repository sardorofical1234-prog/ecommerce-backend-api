# Production Deployment Guide

This document describes a provider-neutral deployment model. It is a checklist, not evidence that the application has already been deployed to a public cloud.

## 1. Recommended topology

Use a private application network with these components:

- TLS-terminating load balancer or reverse proxy.
- One or more stateless API replicas running the same immutable container image.
- Managed PostgreSQL 17 (private endpoint, automated backups and point-in-time recovery).
- Managed Redis 7 (private endpoint, authentication and TLS where supported).
- Centralized structured-log collection and an external monitoring/alerting workspace.

Only the HTTPS load balancer should be internet-facing. Do not publish PostgreSQL or Redis ports. Restrict direct access to the application port to the load balancer and approved operational systems.

## 2. Container and configuration

Build the Docker image in CI and deploy an immutable tag or image digest. Do not build an unpinned source checkout directly on a production host.

Required runtime configuration:

| Variable / property | Guidance |
|---|---|
| `DB_URL` | JDBC URL for managed PostgreSQL; use TLS verification (for example, PostgreSQL `sslmode=verify-full`) |
| `DB_USERNAME`, `DB_PASSWORD` | Dedicated least-privilege application database identity; source the password from a secret manager |
| `JWT_SECRET_BASE64` | Base64-encoded random signing key with at least 32 bytes of decoded entropy; rotate under a planned token-invalidation window |
| `RATE_LIMIT_BACKEND` | Set to `redis` for every multi-replica deployment |
| `REDIS_HOST`, `REDIS_PORT` | Private Redis endpoint |
| `REDIS_PASSWORD` | Secret-manager value when the Redis service requires authentication |
| `REDIS_SSL_ENABLED` | Enable TLS when required by the managed Redis endpoint |
| `BOOTSTRAP_ADMIN_ENABLED` | Normally `false` after the initial administrative account is provisioned |
| `SERVER_PORT` | Internal application port; do not expose it directly to the public internet |

Never commit production `.env` files, credentials, JWT keys, payment secrets or cloud service-account keys. Do not reuse local/demo passwords.

The in-memory rate limiter (`RATE_LIMIT_BACKEND=memory`) is for a single application process only. All replicas must use the same Redis deployment and compatible rate-limit settings.

## 3. Database rollout and recovery

1. Provision PostgreSQL 17 with private networking, encryption at rest, TLS in transit, automated backups and point-in-time recovery.
2. Create a dedicated database and least-privilege application role. The application requires DDL privileges during Flyway migration deployment; if the organization separates migration and runtime identities, run Flyway as a controlled release step and remove DDL rights from the steady-state runtime role.
3. Run the release against a staging database restored from a recent production backup.
4. Deploy schema changes using forward-compatible migrations. Hibernate is configured with `ddl-auto=validate`; Flyway owns schema changes.
5. Take or verify a recoverable backup before production migrations.
6. Verify order, inventory and payment tables after deployment. Perform a documented restore rehearsal regularly.

Do not edit a migration already applied to a shared database. Add a new forward migration. Avoid destructive schema changes in the same release that first stops using the old schema.

## 4. Redis and request limiting

The Redis limiter uses an atomic Lua operation to increment a shared fixed-window counter and set its TTL. It is designed to share counters across API replicas.

- Keep Redis on a private network; do not publish its port publicly.
- Enable authentication and TLS on managed Redis when available.
- Restrict Redis credentials to the application and rotate them through the secret manager.
- Monitor connection failures, command latency, memory pressure and evictions.
- The application intentionally fails closed with HTTP 503 when the configured Redis rate-limit store is unavailable. Alert on these responses; do not silently fall back to per-process counters in a multi-replica environment.

## 5. Reverse proxy, client IP and TLS

Terminate TLS at a maintained load balancer or reverse proxy. Configure it to:

- Redirect HTTP to HTTPS and use current TLS policy.
- Strip client-supplied `Forwarded` and `X-Forwarded-For` headers, then write trusted forwarding headers itself.
- Prevent direct public access to the application port.
- Set request-size, connection and upstream timeouts appropriate to the API.

Only enable Spring's forwarded-header handling (for example, `SERVER_FORWARD_HEADERS_STRATEGY=framework`) when the application is reachable exclusively through that trusted proxy. The rate limiter uses the resolved remote address; trusting arbitrary client-supplied forwarding headers would allow IP spoofing and rate-limit bypass.

## 6. Health checks, logs and metrics

Use the health probes for orchestration:

- `GET /actuator/health/liveness` — restart a process only when liveness fails.
- `GET /actuator/health/readiness` — route traffic only when the application and database are ready.
- `GET /actuator/health` — general health status.

The application emits Logstash-compatible JSON console logs. Each request has a validated or generated `X-Request-Id`; retain that identifier when shipping logs so a request can be followed across the reverse proxy and application.

The current Actuator `/actuator/metrics` endpoint is authenticated. Restrict access to the operations network and authorized operators. Connect the endpoint to the selected monitoring platform using a secured service identity; do not make metrics or diagnostic endpoints publicly accessible. Define alerts for elevated 5xx/503/429 rates, latency, database pool saturation, Redis failures, JVM memory/GC pressure, failed migrations and container restarts.

A dashboard and provider-specific alert rules still need to be configured in the target monitoring workspace.

## 7. Payment boundary

The checked-in payment provider is a deterministic simulator. It does not charge cards and must not be presented as a live payment integration.

Before enabling a real provider, implement and test:
- Provider-side idempotency using the same payment-attempt key.
- Signed webhook verification and replay protection.
- Explicit handling of ambiguous network timeouts and provider status lookup/reconciliation.
- Transactional outbox or equivalent reliable event publication.
- Settlement and refund reconciliation, audit trails and operational alerts.
- Provider-specific compliance, secrets and security review.

Never accept or store card numbers or CVVs in this API. Use the payment provider's hosted/tokenized flow.

## 8. Release verification and rollback

For every release:

1. Verify the CI workflow is green, including unit tests, PostgreSQL/Redis Testcontainers, Docker Compose build and HTTP smoke test.
2. Deploy to staging and run authenticated API smoke tests against the staging environment.
3. Confirm readiness, database migrations, Redis connectivity, request IDs and rate-limit behavior.
4. Deploy gradually and monitor error rate, latency, database connections and Redis availability.
5. Keep the previous immutable image available for application rollback.

An application rollback does not automatically reverse a Flyway migration. Database rollback must follow a separately reviewed restore or forward-fix plan. Test backups and the rollback procedure before the first production release.

## Deployment status

The repository contains Docker Compose and a CI-driven container smoke test. No public cloud deployment, domain, TLS certificate, managed database, managed Redis or external monitoring workspace is provisioned by this project.
