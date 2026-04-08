import axiosInstance from './axiosInstance'

export const predictionApi = {
  /** GET /api/ai/predictions — tüm son tahminler */
  getAll() {
    return axiosInstance.get('/api/ai/predictions')
  },
  /** GET /api/ai/predictions/{zoneId} — tek bölge (cache veya live) */
  getForZone(zoneId) {
    return axiosInstance.get(`/api/ai/predictions/${zoneId}`)
  },
  /** GET /api/ai/predictions/high-risk — HIGH riskli bölgeler */
  getHighRisk() {
    return axiosInstance.get('/api/ai/predictions/high-risk')
  },
  /** POST /api/ai/predictions/refresh — AI servisini çağırıp DB'yi güncelle */
  refresh() {
    return axiosInstance.post('/api/ai/predictions/refresh')
  },
}
