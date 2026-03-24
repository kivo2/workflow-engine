#!/bin/bash

set -e

BASE_URL="http://localhost:8080"
COUNT=20
DELAY_MS=200

while [[ $# -gt 0 ]]; do
  case $1 in
    --count) COUNT="$2"; shift 2 ;;
    --delay) DELAY_MS="$2"; shift 2 ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

echo "═══════════════════════════════════════"
echo "  Workflow Engine Load Test"
echo "  Requests: $COUNT | Delay: ${DELAY_MS}ms"
echo "═══════════════════════════════════════"
echo ""

SUCCESS=0
DECLINED=0
TRANSIENT=0
ERRORS=0

for i in $(seq 1 $COUNT); do
  RAND=$((RANDOM % 100))

  if [ $RAND -lt 60 ]; then
    TOKEN="tok_abc"
    TYPE="happy"
  elif [ $RAND -lt 80 ]; then
    TOKEN="tok_declined"
    TYPE="declined"
  else
    TOKEN="tok_error"
    TYPE="transient"
  fi

  PRODUCT="prod_$((RAND % 2 + 1))"
  QTY=$((RAND % 3 + 1))

  RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/checkout" \
    -H "Content-Type: application/json" \
    -d "{
      \"customerId\": \"cust_load_$i\",
      \"items\": [{\"productId\": \"$PRODUCT\", \"quantity\": $QTY}],
      \"paymentMethod\": {\"type\": \"card\", \"token\": \"$TOKEN\"}
    }" 2>/dev/null)

  HTTP_CODE=$(echo "$RESPONSE" | tail -1)
  BODY=$(echo "$RESPONSE" | head -1)
  STATUS=$(echo "$BODY" | grep -o '"status":"[^"]*"' | cut -d'"' -f4 2>/dev/null || echo "ERROR")
  SAGA_ID=$(echo "$BODY" | grep -o '"sagaId":"[^"]*"' | cut -d'"' -f4 2>/dev/null || echo "")

  case $TYPE in
    happy)    SUCCESS=$((SUCCESS + 1)) ;;
    declined) DECLINED=$((DECLINED + 1)) ;;
    transient) TRANSIENT=$((TRANSIENT + 1)) ;;
  esac

  echo "[$i/$COUNT] $TYPE → HTTP $HTTP_CODE | status=$STATUS | sagaId=${SAGA_ID:0:8}..."

  sleep $(echo "scale=3; $DELAY_MS/1000" | bc 2>/dev/null || echo "0.2")
done

echo ""
echo "═══════════════════════════════════════"
echo "  Load Test Complete"
echo "  Happy path sent:      $SUCCESS"
echo "  Declined sent:        $DECLINED"
echo "  Transient err sent:   $TRANSIENT"
echo ""
echo "  Query H2 to see results:"
echo "  SELECT STATUS, COUNT(*) FROM SAGA GROUP BY STATUS;"
echo ""
echo "  Watch for retry scheduler in orchestrator logs."
echo "  Transient failures should move to COMPLETED or PERMANENTLY_FAILED."
echo "═══════════════════════════════════════"
