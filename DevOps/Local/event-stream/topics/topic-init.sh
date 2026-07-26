#!/bin/bash
# =============================================================================
# Topic Initialization Script
# Creates all Kafka topics defined in topic-registry.yml on first startup.
# Intended to run inside the Kafka container or from a host with kafka CLI.
# =============================================================================
set -e

BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVER:-localhost:9092}"
KAFKA_BIN="${KAFKA_BIN_DIR:-/opt/kafka/bin}"

echo "=== FXOps Kafka Topic Initialization ==="
echo "Bootstrap server: ${BOOTSTRAP_SERVER}"
echo ""

# Wait for Kafka to be ready
echo "Waiting for Kafka to be available..."
until ${KAFKA_BIN}/kafka-topics.sh --bootstrap-server "${BOOTSTRAP_SERVER}" --list > /dev/null 2>&1; do
  echo "  Kafka not ready yet, retrying in 5s..."
  sleep 5
done
echo "Kafka is available."
echo ""

# Function to create a topic if it does not already exist
create_topic() {
  local TOPIC_NAME=$1
  local PARTITIONS=$2
  local REPLICATION=$3
  local RETENTION_MS=$4
  local CLEANUP_POLICY=$5

  EXISTING=$(${KAFKA_BIN}/kafka-topics.sh --bootstrap-server "${BOOTSTRAP_SERVER}" --list | grep -w "${TOPIC_NAME}" || true)
  if [ -n "${EXISTING}" ]; then
    echo "  [SKIP] Topic '${TOPIC_NAME}' already exists."
  else
    echo "  [CREATE] Topic '${TOPIC_NAME}' (partitions=${PARTITIONS}, replication=${REPLICATION}, retention=${RETENTION_MS}ms, cleanup=${CLEANUP_POLICY})"
    ${KAFKA_BIN}/kafka-topics.sh --bootstrap-server "${BOOTSTRAP_SERVER}" \
      --create \
      --topic "${TOPIC_NAME}" \
      --partitions "${PARTITIONS}" \
      --replication-factor "${REPLICATION}" \
      --config retention.ms="${RETENTION_MS}" \
      --config cleanup.policy="${CLEANUP_POLICY}"
  fi
}

echo "--- Domain Event Topics ---"
create_topic "fxops.trade.events"       6 1 2592000000 "delete"
create_topic "fxops.risk.results"       6 1 604800000  "delete"
create_topic "fxops.risk.requests"      6 1 604800000  "delete"
create_topic "fxops.eod.status"         4 1 7776000000 "delete"
create_topic "fxops.sequence.anomalies" 6 1 2592000000 "delete"

echo ""
echo "--- Dead-Letter Queue Topics ---"
create_topic "fxops.dlq.trade-events"   6 1 1209600000 "delete"
create_topic "fxops.dlq.risk-requests"  6 1 1209600000 "delete"

echo ""
echo "=== Topic initialization complete ==="
${KAFKA_BIN}/kafka-topics.sh --bootstrap-server "${BOOTSTRAP_SERVER}" --list
