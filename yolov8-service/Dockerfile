FROM python:3.11-slim

WORKDIR /app

# Sistem bağımlılıkları (psycopg2, PIL, OpenCV için)
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    libpq-dev \
    libgl1 \
    libglib2.0-0 \
    curl \
    && rm -rf /var/lib/apt/lists/*

# CPU-only PyTorch (GPU yok → CUDA paketlerine gerek yok: ~200MB vs 2GB+)
RUN pip install --no-cache-dir \
    torch==2.1.2+cpu \
    torchvision==0.16.2+cpu \
    --index-url https://download.pytorch.org/whl/cpu

# Diğer bağımlılıklar (ultralytics torch'u hazır bulacak, CUDA indirmeyecek)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Uygulama dosyaları
COPY . .

# YOLOv8n modelini build sırasında indir (container içinde hazır olsun)
RUN python -c "from ultralytics import YOLO; YOLO('yolov8n.pt')" || true

EXPOSE 5001

CMD ["python", "app.py"]
