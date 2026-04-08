import axiosInstance from './axiosInstance'

export const flightApi = {
  /** GET /api/flights/my → FlightDetailResponse[] */
  getMyFlights() {
    return axiosInstance.get('/api/flights/my')
  },

  /** GET /api/flights → (ADMIN) FlightDetailResponse[] */
  getAllFlights() {
    return axiosInstance.get('/api/flights')
  },

  /** GET /api/flights/{id} → FlightDetailResponse */
  getFlightDetails(flightId) {
    return axiosInstance.get(`/api/flights/${flightId}`)
  },
}

export const routeApi = {
  /** GET /api/routes/suggest → RouteResponse */
  getSuggestedRoute() {
    return axiosInstance.get('/api/routes/suggest')
  },

  /** GET /api/routes/alternatives/{zoneId} → ZoneOccupancyResponse[] */
  getAlternatives(zoneId) {
    return axiosInstance.get(`/api/routes/alternatives/${zoneId}`)
  },
}
