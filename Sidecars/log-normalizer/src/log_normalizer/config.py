"""Environment-variable configuration for log-normalizer."""

import os


# Extraction settings
MAX_FIELD_LENGTH = int(os.getenv("MAX_FIELD_LENGTH", "500"))
INCLUDE_RAW_LOG = os.getenv("INCLUDE_RAW_LOG", "false").lower() == "true"

# Webhook configuration
WEBHOOK_URL = os.getenv(
    "WEBHOOK_URL",
    "http://fxops-n8n:5678/webhook/log-normalized"
)

# Data source (log input — could be file path or endpoint)
LOG_SOURCE = os.getenv(
    "LOG_SOURCE",
    "/var/log/fxops/application.log"
)

# Batch size for processing
BATCH_SIZE = int(os.getenv("BATCH_SIZE", "100"))
