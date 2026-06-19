import axiosInstance from './axiosInstance'

export const occupancyApi = {
  /** GET /api/zones → ZoneResponse[] */
  getZones() {
    return axiosInstance.get('/api/zones')
  },

  /** GET /api/zones/{id}/occupancy → ZoneOccupancyResponse */
  getZoneOccupancy(zoneId) {
    return axiosInstance.get(`/api/zones/${zoneId}/occupancy`)
  },

  /** GET /api/occupancy/heatmap → HeatmapResponse */
  getHeatmap() {
    return axiosInstance.get('/api/occupancy/heatmap')
  },

  /** GET /api/occupancy/current → ZoneOccupancyResponse[] */
  getCurrent() {
    return axiosInstance.get('/api/occupancy/current')
  },

  /** POST /api/occupancy/redirect → RedirectResponse */
  redirect(body) {
    return axiosInstance.post('/api/occupancy/redirect', body)
  },

  /**
   * POST /api/zones/{zoneId}/analyze-image
   * base64Image: data URI (data:image/jpeg;base64,...) veya saf base64
   * @returns {Promise} ImageAnalysisResponse
   */
  analyzeImage(zoneId, base64Image) {
    return axiosInstance.post(
      `/api/zones/${zoneId}/analyze-image`,
      { image_base64: base64Image },
      { timeout: 65_000 }   // YOLOv8 analizi uzun sürebilir
    )
  },
}
