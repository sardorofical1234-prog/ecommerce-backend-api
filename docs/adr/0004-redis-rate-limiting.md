# ADR 0004: Use Redis for shared rate-limit counters

- **Status:** Accepted
- **Date:** 2026-11-25

## Context

A Caffeine counter is local to one JVM. Behind a load balancer, each API replica would maintain a separate counter, allowing the effective client limit to multiply with the replica count.

## Decision

Provide a RateLimitCounter abstraction with two implementations: Caffeine memory mode for a single process, and Redis mode for shared deployments. Redis uses one atomic Lua script to increment a fixed-window counter and initialize/retain its TTL. Redis mode is selected explicitly by `RATE_LIMIT_BACKEND=redis`. If the store is unavailable, the API fails closed with HTTP 503 rather than silently bypassing the configured protection.

## Consequences

- All replicas share one counter for each remote-IP/endpoint-group key.
- Redis becomes an operational dependency for rate-limited writes when Redis mode is enabled.
- Production Redis must use private networking, authentication, TLS and monitoring.
- Trusted proxy configuration is required so client IPs cannot be spoofed.
