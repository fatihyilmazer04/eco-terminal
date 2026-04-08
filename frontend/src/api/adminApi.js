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
}
