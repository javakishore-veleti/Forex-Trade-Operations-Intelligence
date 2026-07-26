FROM python:3.11-slim

WORKDIR /app

COPY pyproject.toml .
COPY src/ src/

RUN pip install --no-cache-dir .

COPY tests/ tests/

CMD ["python", "-m", "capacity_forecast_model"]
