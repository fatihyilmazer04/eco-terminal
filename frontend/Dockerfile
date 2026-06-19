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

# Build-time ARG'lar — Render'da --build-arg ile verilir
# Yerel Docker Compose'da boş bırakılınca varsayılan değerler kullanılır
ARG BACKEND_URL=http://backend:8080
ARG YOLO_URL=http://yolov8-service:5001

# React Router v6 — tüm rotalar index.html'e yönlendirmeli (SPA)
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Placeholder'ları gerçek URL'lerle değiştir (build time)
RUN sed -i "s|BACKEND_URL_PLACEHOLDER|${BACKEND_URL}|g" /etc/nginx/conf.d/default.conf && \
    sed -i "s|YOLO_URL_PLACEHOLDER|${YOLO_URL}|g" /etc/nginx/conf.d/default.conf

COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
