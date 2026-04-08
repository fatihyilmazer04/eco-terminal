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
}
