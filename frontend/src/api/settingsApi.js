import axiosInstance from './axiosInstance'

/** GET /api/admin/settings/system → SystemStatsResponse */
export const getSystemStats = () =>
  axiosInstance.get('/api/admin/settings/system')

/** GET /api/admin/settings/zones → ZoneSettingsResponse[] */
export const getZoneSettings = () =>
  axiosInstance.get('/api/admin/settings/zones')

/** PUT /api/admin/settings/zones/{id}/threshold */
export const updateZoneThreshold = (zoneId, criticalThreshold) =>
  axiosInstance.put(`/api/admin/settings/zones/${zoneId}/threshold`, { criticalThreshold })

/** GET /api/admin/settings/services → ServiceHealthResponse[] */
export const getServicesHealth = () =>
  axiosInstance.get('/api/admin/settings/services')
