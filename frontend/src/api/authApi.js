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
   * POST /api/auth/register (eski — geriye dönük uyumluluk, doğrulama olmadan)
   */
  register(email, password, fullName) {
    return axiosInstance.post('/api/auth/register', { email, password, fullName })
  },

  /**
   * POST /api/auth/register/send-code
   * Adım 1: Doğrulama kodu gönder
   */
  sendRegisterCode(email, fullName, password) {
    return axiosInstance.post('/api/auth/register/send-code', { email, fullName, password })
  },

  /**
   * POST /api/auth/register/verify
   * Adım 2: Kodu doğrula ve hesabı oluştur
   */
  verifyRegister(email, code, fullName, password) {
    return axiosInstance.post('/api/auth/register/verify', { email, code, fullName, password })
  },

  /**
   * POST /api/auth/refresh
   */
  refresh(refreshToken) {
    return axiosInstance.post('/api/auth/refresh', { refreshToken })
  },
}
