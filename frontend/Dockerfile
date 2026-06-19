# =============================================================================
# Frontend Dockerfile — Multi-stage: Vite build → Nginx serve
# =============================================================================

# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM node:20-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm install --silent

COPY . .

# VITE_API_BASE_URL build-time değişkeni (Vite bake eder)
# Docker: '' (boş) → nginx proxy; local dev: Vite proxy
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

RUN npm run build

# ── Stage 2: Nginx Runtime ───────────────────────────────────────────────────
FROM nginx:alpine AS runtime

# Runtime varsayılan değerler — Docker Compose'da override edilmezse bunlar kullanılır
# Render'da env var olarak BACKEND_URL ve YOLO_URL geçilir
ENV BACKEND_URL=http://backend:8080
ENV YOLO_URL=http://yolov8-service:5001

# React Router v6 — tüm rotalar index.html'e yönlendirmeli (SPA)
RUN rm /etc/nginx/conf.d/default.conf

# nginx.conf → template olarak kopyala; entrypoint envsubst ile işler
COPY nginx.conf /etc/nginx/conf.d/default.conf.template

COPY --from=builder /app/dist /usr/share/nginx/html

# Runtime entrypoint — envsubst çalıştırır, ardından nginx başlatır
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

EXPOSE 80
CMD ["/docker-entrypoint.sh"]
