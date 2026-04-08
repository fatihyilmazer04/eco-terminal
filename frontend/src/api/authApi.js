import axiosInstance from './axiosInstance'

export const authApi = {
  /**
   * POST /api/auth/login
   * Returns: { success, data: { accessToken, refreshToken, role, userId, email, fullName } }
   */
  login(email, password) {
    return axiosInstance.post('/api/auth/login', { email, password })
  },

  /**
   * POST /api/auth/register
   */
  register(email, password, fullName) {
    return axiosInstance.post('/api/auth/register', { email, password, fullName })
  },

  /**
   * POST /api/auth/refresh
   */
  refresh(refreshToken) {
    return axiosInstance.post('/api/auth/refresh', { refreshToken })
  },
}
