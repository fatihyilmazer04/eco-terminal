import axiosInstance from './axiosInstance'

export const qrApi = {
  /**
   * GET /api/admin/zones/qr-codes
   * Tüm aktif zone'ların QR token ve içeriklerini döner (yalnızca ADMIN).
   */
  getZoneQrCodes() {
    return axiosInstance.get('/api/admin/zones/qr-codes')
  },

  /**
   * GET /api/admin/zones/list
   * Tüm aktif zone'ları döner (QR token olmayan dahil — modal dropdown için).
   */
  getAllZones() {
    return axiosInstance.get('/api/admin/zones/list')
  },

  /**
   * POST /api/admin/zones/{zoneId}/qr/generate
   * Zone için yeni QR token üretir ve döner.
   */
  generateQrToken(zoneId) {
    return axiosInstance.post(`/api/admin/zones/${zoneId}/qr/generate`)
  },

  /**
   * DELETE /api/admin/zones/{zoneId}/qr
   * Zone QR token'ını siler.
   */
  deleteQrToken(zoneId) {
    return axiosInstance.delete(`/api/admin/zones/${zoneId}/qr`)
  },
}
