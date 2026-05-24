import axiosInstance from './axiosInstance'

/**
 * Tüm zone'ların anlık kalabalık durumu (Spring Boot → DB)
 * GET /api/crowd/status
 */
export const getCrowdStatus = () =>
  axiosInstance.get('/api/crowd/status').then(r => r.data.data)

/**
 * Flask AI servisinin kalabalık analizi (Spring Boot proxy)
 * GET /api/ai/crowd-analysis → Flask /analyze/crowd
 */
export const getAICrowdAnalysis = () =>
  axiosInstance.get('/api/ai/crowd-analysis').then(r => r.data.data)
