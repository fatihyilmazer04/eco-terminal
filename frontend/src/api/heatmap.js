import axiosInstance from './axiosInstance'

/**
 * Terminal heatmap API modülü
 * Tüm çağrılar axiosInstance üzerinden geçer (JWT otomatik eklenir).
 */

/** GET /api/heatmap/live — Anlık tüm zone durumu + SVG koordinatları + AI özeti */
export const getHeatmapLive = () =>
  axiosInstance.get('/api/heatmap/live').then(r => r.data.data)

/** GET /api/heatmap/history?zone_id={id}&hours={hours} — Zone doluluk grafiği verisi */
export const getHeatmapHistory = (zoneId, hours = 24) =>
  axiosInstance.get(`/api/heatmap/history?zone_id=${zoneId}&hours=${hours}`).then(r => r.data.data)

/** POST /api/heatmap/refresh — AI tahminleri yenile + güncel heatmap döndür (ADMIN) */
export const refreshHeatmap = () =>
  axiosInstance.post('/api/heatmap/refresh').then(r => r.data.data)
