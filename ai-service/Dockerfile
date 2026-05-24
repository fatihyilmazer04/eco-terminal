FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    libpq-dev \
    curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 5000

# Dev: FLASK_ENV=development → Flask debug sunucu
# Prod: gunicorn çok-iş parçacıklı (2 worker)
CMD ["sh", "-c", "if [ \"$FLASK_ENV\" = 'development' ]; then python app.py; else gunicorn --bind 0.0.0.0:5000 --workers 2 --timeout 120 app:app; fi"]
