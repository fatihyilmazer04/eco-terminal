import axiosInstance from './axiosInstance'

export const routeApi = {
  /** GET /api/routes/suggest */
  suggest() {
    return axiosInstance.get('/api/routes/suggest')
  },

  /**
   * POST /api/routes/checkin
   * body: { flightId, stepNumber, zoneName, totalSteps }
   */
  checkinStep({ flightId, stepNumber, zoneName, totalSteps }) {
    return axiosInstance.post('/api/routes/checkin', { flightId, stepNumber, zoneName, totalSteps })
  },

  /**
   * POST /api/routes/complete
   * body: { flightId }
   * Backend kayıtlarından doğrular — tüm adımlar check-in değilse 400 döner.
   */
  completeRoute(flightId) {
    return axiosInstance.post('/api/routes/complete', { flightId })
  },
}
