import axiosInstance from './axiosInstance'

export const adminApi = {
  /** GET /api/admin/dashboard */
  getDashboard() {
    return axiosInstance.get('/api/admin/dashboard')
  },
  /** GET /api/admin/reports/occupancy?date=YYYY-MM-DD */
  getOccupancyReport(date) {
    return axiosInstance.get('/api/admin/reports/occupancy', { params: { date } })
  },
  /** GET /api/admin/reports/energy?date=YYYY-MM-DD */
  getEnergyReport(date) {
    return axiosInstance.get('/api/admin/reports/energy', { params: { date } })
  },
}

export const statsApi = {
  /** GET /api/stats/visitors — 24s saatlik yolcu istatistiği */
  getVisitors() { return axiosInstance.get('/api/stats/visitors') },
  /** GET /api/stats/energy — 24s saatlik enerji istatistiği */
  getEnergy()   { return axiosInstance.get('/api/stats/energy') },
  /** GET /api/stats/cameras — IoT cihaz durumları */
  getCameras()  { return axiosInstance.get('/api/stats/cameras') },
}

export const energyApi = {
  /** GET /api/energy/usage → EnergyResponse[] */
  getAllUsage() {
    return axiosInstance.get('/api/energy/usage')
  },
  /** GET /api/energy/usage/{zoneId} → EnergyResponse */
  getZoneUsage(zoneId) {
    return axiosInstance.get(`/api/energy/usage/${zoneId}`)
  },
  /** GET /api/energy/savings → SavingSuggestion[] */
  getSavings() {
    return axiosInstance.get('/api/energy/savings')
  },
  /** GET /api/energy/trend/{zoneId}?hours=6 → EnergyTrendPoint[] */
  getTrend(zoneId, hours = 6) {
    return axiosInstance.get(`/api/energy/trend/${zoneId}`, { params: { hours } })
  },
  /** PATCH /api/energy/zones/{zoneId}/settings → EnergySettingResponse */
  updateSettings(zoneId, body) {
    return axiosInstance.patch(`/api/energy/zones/${zoneId}/settings`, body)
  },
}
