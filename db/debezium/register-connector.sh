#!/bin/bash
# Register Debezium outbox connector after Kafka Connect is ready
set -euo pipefail

CONNECT_URL="${CONNECT_URL:-http://debezium:8083}"
MAX_ATTEMPTS=30

echo "Waiting for Kafka Connect at $CONNECT_URL ..."
for i in $(seq 1 $MAX_ATTEMPTS); do
  if curl -sf "$CONNECT_URL/connectors" >/dev/null 2>&1; then
    echo "Kafka Connect ready."
    break
  fi
  sleep 5
  if [ "$i" -eq "$MAX_ATTEMPTS" ]; then
    echo "ABORT: Kafka Connect not ready"
    exit 1
  fi
done

CONNECTOR_NAME="detp-outbox-connector"
if curl -sf "$CONNECT_URL/connectors/$CONNECTOR_NAME" >/dev/null 2>&1; then
  echo "Connector $CONNECTOR_NAME already exists — deleting for fresh register"
  curl -sf -X DELETE "$CONNECT_URL/connectors/$CONNECTOR_NAME"
  sleep 2
fi

echo "Registering outbox connector ..."
HTTP_CODE=$(curl -s -o /tmp/connector-response.json -w "%{http_code}" \
  -X POST "$CONNECT_URL/connectors" \
  -H "Content-Type: application/json" \
  -d @/config/outbox-connector.json)

if [ "$HTTP_CODE" = "201" ] || [ "$HTTP_CODE" = "200" ]; then
  echo "Connector registered successfully."
  cat /tmp/connector-response.json
  exit 0
else
  echo "ABORT: connector registration failed HTTP $HTTP_CODE"
  cat /tmp/connector-response.json
  exit 1
fi
