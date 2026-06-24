import axiosInstance from './axiosInstance'

export const notificationApi = {
  /** GET /api/notifications/my */
  getMy() {
    return axiosInstance.get('/api/notifications/my')
  },
  /** GET /api/notifications/unread-count */
  getUnreadCount() {
    return axiosInstance.get('/api/notifications/unread-count')
  },
  /** PUT /api/notifications/{id}/read */
  markAsRead(notifId) {
    return axiosInstance.put(`/api/notifications/${notifId}/read`)
  },
  /** PUT /api/notifications/read-all */
  markAllAsRead() {
    return axiosInstance.put('/api/notifications/read-all')
  },
  /** POST /api/notifications/send — admin only */
  sendManual(payload) {
    return axiosInstance.post('/api/notifications/send', payload)
  },
  /** DELETE /api/notifications/{id} — tek bildirimi sil */
  deleteOne(notifId) {
    return axiosInstance.delete(`/api/notifications/${notifId}`)
  },
  /** DELETE /api/notifications/clear-all — tüm bildirimleri sil */
  clearAll() {
    return axiosInstance.delete('/api/notifications/clear-all')
  },
  /** PUT /api/users/fcm-token */
  updateFcmToken(fcmToken) {
    return axiosInstance.put('/api/users/fcm-token', { fcmToken })
  },
}
