FROM python:3.11-slim

# Sistem bağımlılıkları (curl healthcheck için)
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Önce requirements (Docker layer cache için)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Uygulama kodu
COPY ./app ./app

# Non-root user (güvenlik)
RUN useradd -m -u 1000 appuser && chown -R appuser:appuser /app
USER appuser

EXPOSE 5002

# Healthcheck
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -f http://localhost:5002/health || exit 1

# Production server
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "5002", "--workers", "2"]
