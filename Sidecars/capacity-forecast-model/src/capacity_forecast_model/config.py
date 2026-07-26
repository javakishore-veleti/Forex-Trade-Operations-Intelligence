"""Environment-variable configuration for capacity-forecast-model."""

import os


# Forecast thresholds
SHORTFALL_THRESHOLD_MINUTES = int(os.getenv("SHORTFALL_THRESHOLD_MINUTES", "30"))
CONFIDENCE_LEVEL = float(os.getenv("CONFIDENCE_LEVEL", "0.9"))
LOOKBACK_PERIODS = int(os.getenv("LOOKBACK_PERIODS", "10"))

# Webhook configuration
WEBHOOK_URL = os.getenv(
    "WEBHOOK_URL",
    "http://fxops-n8n:5678/webhook/capacity-shortfall"
)

# Data source
DATA_SOURCE_URL = os.getenv(
    "DATA_SOURCE_URL",
    "http://eod-processing-service:8084/api/v1/eod/branches/status"
)

# Polling interval in seconds
POLL_INTERVAL_SECONDS = int(os.getenv("POLL_INTERVAL_SECONDS", "60"))
