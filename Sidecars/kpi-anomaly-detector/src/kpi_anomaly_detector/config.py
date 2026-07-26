"""Environment-variable configuration for kpi-anomaly-detector."""

import os


# Detection thresholds
DETECTION_THRESHOLD = float(os.getenv("DETECTION_THRESHOLD", "2.5"))
ROLLING_WINDOW_SIZE = int(os.getenv("ROLLING_WINDOW_SIZE", "30"))
MIN_DATA_POINTS = int(os.getenv("MIN_DATA_POINTS", "10"))

# Webhook configuration
WEBHOOK_URL = os.getenv(
    "WEBHOOK_URL",
    "http://fxops-n8n:5678/webhook/kpi-anomaly"
)

# Data source
DATA_SOURCE_URL = os.getenv(
    "DATA_SOURCE_URL",
    "http://trade-lifecycle-service:8081/actuator/metrics"
)

# Polling interval in seconds
POLL_INTERVAL_SECONDS = int(os.getenv("POLL_INTERVAL_SECONDS", "60"))
