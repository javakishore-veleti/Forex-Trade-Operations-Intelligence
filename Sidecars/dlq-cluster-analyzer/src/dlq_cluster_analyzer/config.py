"""Environment-variable configuration for dlq-cluster-analyzer."""

import os


# Clustering thresholds
SIMILARITY_THRESHOLD = float(os.getenv("SIMILARITY_THRESHOLD", "0.8"))
MIN_CLUSTER_SIZE = int(os.getenv("MIN_CLUSTER_SIZE", "3"))
MAX_CLUSTERS = int(os.getenv("MAX_CLUSTERS", "20"))

# Webhook configuration
WEBHOOK_URL = os.getenv(
    "WEBHOOK_URL",
    "http://fxops-n8n:5678/webhook/dlq-cluster"
)

# Data source
DATA_SOURCE_URL = os.getenv(
    "DATA_SOURCE_URL",
    "http://trade-lifecycle-service:8081/api/v1/dlq"
)

# Polling interval in seconds
POLL_INTERVAL_SECONDS = int(os.getenv("POLL_INTERVAL_SECONDS", "120"))
