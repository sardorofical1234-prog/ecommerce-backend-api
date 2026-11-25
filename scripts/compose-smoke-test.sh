#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
ADMIN_EMAIL="${SMOKE_ADMIN_EMAIL:-${BOOTSTRAP_ADMIN_EMAIL:-}}"
ADMIN_PASSWORD="${SMOKE_ADMIN_PASSWORD:-${BOOTSTRAP_ADMIN_PASSWORD:-}}"

if [[ -z "$ADMIN_EMAIL" || -z "$ADMIN_PASSWORD" ]]; then
  echo "Set BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD in .env (and enable bootstrap admin)." >&2
  exit 1
fi

command -v curl >/dev/null || { echo "curl is required." >&2; exit 1; }
command -v jq >/dev/null || { echo "jq is required." >&2; exit 1; }

API_BODY=""

api_request() {
  local method="$1"
  local path="$2"
  local expected_status="$3"
  shift 3

  local response_file
  local actual_status
  response_file="$(mktemp)"

  if ! actual_status="$(curl --silent --show-error --output "$response_file" \
      --write-out '%{http_code}' --request "$method" "$BASE_URL$path" "$@")"; then
    cat "$response_file" >&2 || true
    rm -f "$response_file"
    echo "HTTP request failed: $method $path" >&2
    exit 1
  fi

  API_BODY="$(cat "$response_file")"
  rm -f "$response_file"

  if [[ "$actual_status" != "$expected_status" ]]; then
    echo "Expected HTTP $expected_status, received $actual_status for $method $path" >&2
    echo "$API_BODY" >&2
    exit 1
  fi
}

echo "Waiting for API readiness at $BASE_URL ..."
ready=false
for attempt in $(seq 1 60); do
  if health="$(curl --silent --show-error --fail --max-time 3 "$BASE_URL/actuator/health/readiness" 2>/dev/null)" \
      && jq -e '.status == "UP"' >/dev/null <<<"$health"; then
    ready=true
    break
  fi
  sleep 2
done

if [[ "$ready" != true ]]; then
  echo "API did not become ready within 120 seconds." >&2
  exit 1
fi
echo "PASS: application and database readiness"

suffix="$(date +%s)-$RANDOM"
sku="SMOKE-$suffix"
customer_email="smoke-$suffix@example.com"
customer_password="smoke-customer-password-$suffix"
checkout_key="smoke-checkout-$suffix"
payment_key="smoke-payment-$suffix"

echo "Logging in with the bootstrapped admin ..."
login_json="$(jq -n --arg email "$ADMIN_EMAIL" --arg password "$ADMIN_PASSWORD" \
  '{email: $email, password: $password}')"
api_request POST "/api/v1/auth/login" 200 \
  -H "Content-Type: application/json" --data "$login_json"
admin_token="$(jq -er '.accessToken' <<<"$API_BODY")"

echo "Creating a catalogue product ..."
product_json="$(jq -n --arg sku "$sku" \
  '{sku: $sku, name: "Compose Smoke Product", description: "Temporary end-to-end smoke-test product", category: "Smoke Test", price: 9.99, stock: 5}')"
api_request POST "/api/v1/admin/products" 201 \
  -H "Authorization: Bearer $admin_token" \
  -H "Content-Type: application/json" --data "$product_json"
product_id="$(jq -er '.id' <<<"$API_BODY")"
echo "PASS: admin authentication and product creation"

echo "Registering a customer ..."
register_json="$(jq -n --arg email "$customer_email" --arg password "$customer_password" \
  '{email: $email, password: $password, fullName: "Compose Smoke Customer"}')"
api_request POST "/api/v1/auth/register" 201 \
  -H "Content-Type: application/json" --data "$register_json"
customer_token="$(jq -er '.accessToken' <<<"$API_BODY")"
echo "PASS: customer registration"

echo "Adding the product to the cart ..."
cart_json="$(jq -n --arg productId "$product_id" \
  '{productId: $productId, quantity: 2}')"
api_request POST "/api/v1/cart/items" 200 \
  -H "Authorization: Bearer $customer_token" \
  -H "Content-Type: application/json" --data "$cart_json"
jq -e '.totalItems == 2' >/dev/null <<<"$API_BODY"
echo "PASS: cart mutation"

echo "Checking out and verifying checkout idempotency ..."
api_request POST "/api/v1/checkout" 201 \
  -H "Authorization: Bearer $customer_token" \
  -H "Idempotency-Key: $checkout_key"
order_id="$(jq -er '.id' <<<"$API_BODY")"
jq -e '.status == "PENDING_PAYMENT"' >/dev/null <<<"$API_BODY"

api_request POST "/api/v1/checkout" 201 \
  -H "Authorization: Bearer $customer_token" \
  -H "Idempotency-Key: $checkout_key"
retry_order_id="$(jq -er '.id' <<<"$API_BODY")"
[[ "$retry_order_id" == "$order_id" ]]
echo "PASS: checkout, inventory reservation and checkout idempotency"

echo "Approving a simulated payment and verifying payment idempotency ..."
api_request POST "/api/v1/orders/$order_id/payments" 200 \
  -H "Authorization: Bearer $customer_token" \
  -H "Idempotency-Key: $payment_key" \
  -H "Content-Type: application/json" \
  --data '{"scenario":"APPROVE"}'
jq -e '.status == "SUCCEEDED"' >/dev/null <<<"$API_BODY"

api_request POST "/api/v1/orders/$order_id/payments" 200 \
  -H "Authorization: Bearer $customer_token" \
  -H "Idempotency-Key: $payment_key" \
  -H "Content-Type: application/json" \
  --data '{"scenario":"DECLINE"}'
jq -e '.status == "SUCCEEDED"' >/dev/null <<<"$API_BODY"
echo "PASS: simulated payment and payment idempotency"

echo "Verifying persisted order, payment history and inventory ..."
api_request GET "/api/v1/orders/$order_id" 200 \
  -H "Authorization: Bearer $customer_token"
jq -e '.status == "PAID"' >/dev/null <<<"$API_BODY"

api_request GET "/api/v1/orders/$order_id/payments" 200 \
  -H "Authorization: Bearer $customer_token"
jq -e 'length == 1 and .[0].status == "SUCCEEDED"' >/dev/null <<<"$API_BODY"

api_request GET "/api/v1/products/$product_id" 200
jq -e '.stock == 3' >/dev/null <<<"$API_BODY"

echo "PASS: order state, payment history and final stock"
echo "Docker Compose end-to-end smoke test passed."
