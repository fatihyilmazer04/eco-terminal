FROM python:3.10-slim

WORKDIR /app

# Install dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application
COPY . .

# Expose port
EXPOSE 5000

# Health check
HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -f http://localhost:5000/health || exit 1

# Run application
CMD ["python", "app.py"]
```
```
4. Commit message: "Add Dockerfile"
5. "Commit new file" tıkla
```

---

## **4️⃣ Kubernetes Deployment Ekle**
```
1. "Add file" → "Create new file"
2. Dosya adı: kubernetes/deployment.yaml
   (Slash ile yaz, otomatik klasör oluşturur!)
3. Aşağıdaki kodu yapıştır:
