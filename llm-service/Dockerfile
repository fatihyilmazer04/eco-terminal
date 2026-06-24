FROM python:3.11-slim

# Sistem bağımlılıkları (curl healthcheck için)
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Önce requirements (Docker layer cache için)
COPY requirements.txt .

# Ana bağımlılıklar (hafif — ML kütüphaneleri dahil değil)
RUN pip install --no-cache-dir --timeout 120 --retries 5 -r requirements.txt

# OPSİYONEL: DistilBERT için CPU-only torch (GPU/CUDA kütüphaneleri dahil değil ~250 MB)
# Aktifleştirmek için aşağıdaki satırı yorum dışına alın:
# RUN pip install --no-cache-dir --timeout 120 torch==2.3.1+cpu \
#       --index-url https://download.pytorch.org/whl/cpu && \
#     pip install --no-cache-dir --timeout 120 transformers==4.45.0 accelerate==0.30.0

# Uygulama kodu
COPY ./app ./app

# Non-root user (güvenlik)
RUN useradd -m -u 1000 appuser && chown -R appuser:appuser /app
USER appuser

EXPOSE 5002

# Healthcheck
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -f http://localhost:${PORT:-5002}/health || exit 1

# Production server — PORT env var'ı Render tarafından atanır, yoksa 5002'ye düşer
CMD sh -c "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-5002} --workers 2"
