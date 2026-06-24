import axiosInstance from './axiosInstance'

export const adminApi = {
  /** GET /api/admin/dashboard */
  getDashboard() {
    return axiosInstance.get('/api/admin/dashboard')
  },
  /** GET /api/admin/system/health */
  getSystemHealth() {
    return axiosInstance.get('/api/admin/system/health')
  },
  /** GET /api/admin/reports/occupancy?date=YYYY-MM-DD */
  getOccupancyReport(date) {
    return axiosInstance.get('/api/admin/reports/occupancy', { params: { date } })
  },
  /** GET /api/admin/reports/energy?date=YYYY-MM-DD */
  getEnergyReport(date) {
    return axiosInstance.get('/api/admin/reports/energy', { params: { date } })
  },
  /** GET /api/admin/reports/occupancy/range?startDate=...&endDate=... */
  getOccupancyReportRange(startDate, endDate) {
    return axiosInstance.get('/api/admin/reports/occupancy/range', { params: { startDate, endDate } })
  },
  /** GET /api/admin/reports/energy/range?startDate=...&endDate=... */
  getEnergyReportRange(startDate, endDate) {
    return axiosInstance.get('/api/admin/reports/energy/range', { params: { startDate, endDate } })
  },
  /** GET /api/admin/reports/occupancy/summary?range=LAST_30 */
  getOccupancySummary(range) {
    return axiosInstance.get('/api/admin/reports/occupancy/summary', { params: { range } })
  },
  /** GET /api/admin/reports/energy/summary?range=LAST_30 */
  getEnergySummary(range) {
    return axiosInstance.get('/api/admin/reports/energy/summary', { params: { range } })
  },
  /** GET /api/admin/reports/users/summary?startDate=...&endDate=... */
  getUserReportSummary(startDate, endDate) {
    return axiosInstance.get('/api/admin/reports/users/summary', { params: { startDate, endDate } })
  },
  /** GET /api/admin/reports/ai-summary?startDate=...&endDate=... */
  getAiSummary(startDate, endDate) {
    return axiosInstance.get('/api/admin/reports/ai-summary', { params: { startDate, endDate } })
  },
  /** GET /api/admin/reports/ai-accuracy?startDate=...&endDate=... */
  getAiAccuracy(startDate, endDate) {
    return axiosInstance.get('/api/admin/reports/ai-accuracy', { params: { startDate, endDate } })
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
  /** PATCH /api/energy/zones/{zoneId}/settings → EnergySettingResponse */
  updateSettings(zoneId, body) {
    return axiosInstance.patch(`/api/energy/zones/${zoneId}/settings`, body)
  },
}
