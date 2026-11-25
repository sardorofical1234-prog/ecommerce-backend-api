# Observability: Prometheus and Grafana

The application exposes health probes, Actuator metrics and a Prometheus-compatible metrics endpoint. The repository includes a Prometheus scrape template and an importable Grafana dashboard. It does not provision an external Prometheus/Grafana account or send alerts to an external destination.

## 1. Configure the scraper secret

Generate a high-entropy secret:

```bash
openssl rand -hex 32
```

Store it as `METRICS_SCRAPE_TOKEN` in the application secret manager. For a local Compose environment, put it in the untracked `.env` file. The API accepts the scraper identity `prometheus` with this secret as the HTTP Basic password. The endpoint is denied when the secret is empty.

The secret is independent of customer/admin JWT signing keys. Do not commit the token or place it in Grafana dashboard JSON.

## 2. Configure Prometheus

Use [monitoring/prometheus.yml](../monitoring/prometheus.yml) as a starting point. Mount the same token value into the Prometheus container as a file at:

`/etc/prometheus/secrets/ecommerce-api-token`

The scrape target `app:8080` assumes Prometheus shares a private network with the API Compose service. For a managed Prometheus service, replace the target with the private application endpoint reachable by that service and store the token using the provider's secret facility.

The scrape endpoint is:

`GET /actuator/prometheus`

Prometheus authenticates using HTTP Basic username `prometheus` and the token file. All other non-health Actuator endpoints remain authenticated by the application's regular security rules. Do not expose the metrics endpoint to the public internet.

## 3. Import the Grafana dashboard

1. Add the Prometheus data source to Grafana.
2. Import [monitoring/grafana-dashboard.json](../monitoring/grafana-dashboard.json).
3. Select the Prometheus data source when Grafana prompts for `DS_PROMETHEUS`.

The dashboard contains panels for HTTP request rate, 5xx rate, p95 request latency, JVM memory, active Hikari connections and process CPU.

The application enables histogram buckets for HTTP server request timers so the latency percentile panel can be calculated. Confirm that the selected Micrometer/Prometheus version emits the expected metric names after the first scrape.

## 4. Suggested alert rules

Create and tune alerts in the target monitoring platform for:

- API target unavailable or readiness failing.
- Sustained HTTP 5xx rate and elevated p95/p99 latency.
- HTTP 429 or 503 spikes, especially when Redis rate-limit storage is unavailable.
- High active database connections or connection-pool saturation.
- Redis command/connection failures and memory pressure.
- JVM heap pressure, sustained CPU use and repeated container restarts.
- Failed Flyway migrations during a release.

Use the `X-Request-Id` in structured JSON logs to correlate individual requests with proxy and application logs.

## Observability status

The secured Prometheus endpoint and Grafana dashboard template are versioned in this repository. Provisioning a real monitoring workspace, private network path, secrets and alert delivery integrations remains an environment-specific deployment task.
