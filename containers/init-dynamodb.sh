#!/bin/bash

set -e

DYNAMO_ENDPOINT="http://localhost:8000"
AUTH_HEADER="Authorization: AWS4-HMAC-SHA256 Credential=dummy/20260404/us-west-2/dynamodb/aws4_request, SignedHeaders=content-type;host;x-amz-target, Signature=dummy"
DATE_HEADER="X-Amz-Date: 20260404T000000Z"

echo "========================================="
echo "  Initializing DynamoDB tables..."
echo "========================================="

# ── Helper ──────────────────────────────────
dynamo() {
  local target=$1
  local body=$2
  curl -s -X POST "$DYNAMO_ENDPOINT" \
    -H "Content-Type: application/x-amz-json-1.0" \
    -H "X-Amz-Target: DynamoDB_20120810.$target" \
    -H "$AUTH_HEADER" \
    -H "$DATE_HEADER" \
    -d "$body"
}

wait_for_index() {
  local table=$1
  local index=$2
  echo "  Waiting for index '$index' on table '$table' to become ACTIVE..."
  for i in $(seq 1 20); do
    status=$(dynamo DescribeTable "{\"TableName\":\"$table\"}" \
      | grep -o "\"IndexName\":\"$index\".*\"IndexStatus\":\"[^\"]*\"" \
      | grep -o "\"IndexStatus\":\"[^\"]*\"" \
      | grep -o "ACTIVE\|CREATING\|UPDATING")
    if [ "$status" = "ACTIVE" ]; then
      echo "  ✔ Index '$index' is ACTIVE"
      return 0
    fi
    sleep 1
  done
  echo "  ✘ Timeout waiting for index '$index'"
  return 1
}

# ─────────────────────────────────────────────
# TABLE: Ticket
# PK: eventId (HASH) | SK: ticketId (RANGE)
# GSI: status-index  (status HASH)
# GSI: ticketId-index (ticketId HASH)
# ─────────────────────────────────────────────
echo ""
echo ">>> Creating table: Ticket"
dynamo CreateTable '{
  "TableName": "Ticket",
  "BillingMode": "PAY_PER_REQUEST",
  "AttributeDefinitions": [
    {"AttributeName": "eventId",  "AttributeType": "S"},
    {"AttributeName": "ticketId", "AttributeType": "S"},
    {"AttributeName": "status",   "AttributeType": "S"}
  ],
  "KeySchema": [
    {"AttributeName": "eventId",  "KeyType": "HASH"},
    {"AttributeName": "ticketId", "KeyType": "RANGE"}
  ],
  "GlobalSecondaryIndexes": [
    {
      "IndexName": "status-index",
      "KeySchema": [{"AttributeName": "status", "KeyType": "HASH"}],
      "Projection": {"ProjectionType": "ALL"}
    },
    {
      "IndexName": "ticketId-index",
      "KeySchema": [{"AttributeName": "ticketId", "KeyType": "HASH"}],
      "Projection": {"ProjectionType": "ALL"}
    }
  ]
}'
echo ""

# ─────────────────────────────────────────────
# TABLE: OrderHistory
# PK: orderId (HASH) | SK: createdAt (RANGE)
# ─────────────────────────────────────────────
echo ">>> Creating table: OrderHistory"
dynamo CreateTable '{
  "TableName": "OrderHistory",
  "BillingMode": "PAY_PER_REQUEST",
  "AttributeDefinitions": [
    {"AttributeName": "orderId",   "AttributeType": "S"},
    {"AttributeName": "createdAt", "AttributeType": "S"}
  ],
  "KeySchema": [
    {"AttributeName": "orderId",   "KeyType": "HASH"},
    {"AttributeName": "createdAt", "KeyType": "RANGE"}
  ]
}'
echo ""

# ─────────────────────────────────────────────
# TABLE: Orders
# ─────────────────────────────────────────────
echo ">>> Creating table: Orders"
dynamo CreateTable '{
  "TableName": "Orders",
  "BillingMode": "PAY_PER_REQUEST",
  "AttributeDefinitions": [
    {"AttributeName": "orderId", "AttributeType": "S"}
  ],
  "KeySchema": [
    {"AttributeName": "orderId", "KeyType": "HASH"}
  ]
}'
echo ""

# ─────────────────────────────────────────────
# TABLE: Event
# ─────────────────────────────────────────────
echo ">>> Creating table: Event"
dynamo CreateTable '{
  "TableName": "Event",
  "BillingMode": "PAY_PER_REQUEST",
  "AttributeDefinitions": [
    {"AttributeName": "eventId", "AttributeType": "S"}
  ],
  "KeySchema": [
    {"AttributeName": "eventId", "KeyType": "HASH"}
  ]
}'
echo ""

# ─────────────────────────────────────────────
# TABLE: Idempotency
# ─────────────────────────────────────────────
echo ">>> Creating table: Idempotency"
dynamo CreateTable '{
  "TableName": "Idempotency",
  "BillingMode": "PAY_PER_REQUEST",
  "AttributeDefinitions": [
    {"AttributeName": "idempotencyKey", "AttributeType": "S"}
  ],
  "KeySchema": [
    {"AttributeName": "idempotencyKey", "KeyType": "HASH"}
  ]
}'
echo ""

echo "========================================="
echo "  All tables created successfully ✔"
echo "========================================="