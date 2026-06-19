import axiosInstance from './axiosInstance'

export const ticketApi = {
  // ── User ──────────────────────────────────────────────────────────────

  /** GET /api/tickets/lookup?pnrCode=XX-XXXXXX → TicketDetailResponse (preview) */
  lookupPnr(pnrCode) {
    return axiosInstance.get('/api/tickets/lookup', { params: { pnrCode } })
  },

  /** POST /api/tickets/claim → TicketDetailResponse */
  claimTicket(pnrCode) {
    return axiosInstance.post('/api/tickets/claim', { pnrCode })
  },

  /** POST /api/tickets/{ticketId}/unclaim → bileti hesaptan kaldır */
  unclaimTicket(ticketId) {
    return axiosInstance.post(`/api/tickets/${ticketId}/unclaim`)
  },

  // ── Admin ─────────────────────────────────────────────────────────────

  /** GET /api/admin/tickets → TicketDetailResponse[] */
  adminGetAll() {
    return axiosInstance.get('/api/admin/tickets')
  },

  /** POST /api/admin/tickets → TicketDetailResponse */
  adminCreate(data) {
    return axiosInstance.post('/api/admin/tickets', data)
  },

  /** DELETE /api/admin/tickets/{id} */
  adminDelete(ticketId) {
    return axiosInstance.delete(`/api/admin/tickets/${ticketId}`)
  },

  // ── Admin — Flights ───────────────────────────────────────────────────

  /** GET /api/admin/flights → AdminFlightResponse[] */
  adminGetFlights() {
    return axiosInstance.get('/api/admin/flights')
  },

  /** POST /api/admin/flights → AdminFlightResponse */
  adminCreateFlight(data) {
    return axiosInstance.post('/api/admin/flights', data)
  },

  /** PUT /api/admin/flights/{id} → AdminFlightResponse */
  adminUpdateFlight(flightId, data) {
    return axiosInstance.put(`/api/admin/flights/${flightId}`, data)
  },

  /** DELETE /api/admin/flights/{id} */
  adminDeleteFlight(flightId) {
    return axiosInstance.delete(`/api/admin/flights/${flightId}`)
  },

  /** GET /api/admin/flights/airlines → AirlineResponse[] */
  getAirlines() {
    return axiosInstance.get('/api/admin/flights/airlines')
  },

  /** GET /api/admin/flights/gates → ZoneResponse[] */
  getGates() {
    return axiosInstance.get('/api/admin/flights/gates')
  },
}
