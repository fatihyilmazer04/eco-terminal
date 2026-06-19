#!/bin/sh
# Runtime entrypoint: BACKEND_URL ve YOLO_URL env var'larını nginx config'e yaz.
# envsubst'a sadece bu iki değişkeni ver — nginx'in kendi $host, $remote_addr
# gibi değişkenleri korunur, bozulmaz.
envsubst '${BACKEND_URL} ${YOLO_URL}' \
  < /etc/nginx/conf.d/default.conf.template \
  > /etc/nginx/conf.d/default.conf

exec nginx -g 'daemon off;'
