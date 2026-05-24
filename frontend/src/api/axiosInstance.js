import axios from 'axios'
import toast from 'react-hot-toast'

const axiosInstance = axios.create({
  // Docker: VITE_API_BASE_URL='' → göreli URL, nginx /api/ proxy'si devreye girer
  // Local dev: Vite'ın kendi proxy'si (/api/ → localhost:8080) devreye girer
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request interceptor — her isteğe JWT token ekle
axiosInstance.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// Response interceptor — 401 alınırsa refresh token ile yenile
axiosInstance.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      const refreshToken = localStorage.getItem('refreshToken')
      if (!refreshToken) {
        // Refresh token yok → logout
        localStorage.clear()
        window.location.href = '/login'
        return Promise.reject(error)
      }

      try {
        const res = await axios.post(
          `${import.meta.env.VITE_API_BASE_URL ?? ''}/api/auth/refresh`,
          { refreshToken },
        )
        const { accessToken } = res.data.data
        localStorage.setItem('accessToken', accessToken)
        originalRequest.headers.Authorization = `Bearer ${accessToken}`
        return axiosInstance(originalRequest)
      } catch {
        localStorage.clear()
        window.location.href = '/login'
        return Promise.reject(error)
      }
    }

    // 401 dışındaki API hatalarında kırmızı toast (bildirim ve 403 hariç - bunlar UI'da ele alınır)
    const status = error.response?.status
    const url = error.config?.url ?? ''
    const isNotifEndpoint = url.includes('/notifications') || url.includes('/fcm-token')
    if (status && status !== 401 && status !== 403 && !isNotifEndpoint) {
      const msg = error.response?.data?.message || 'Bir hata oluştu. Lütfen tekrar deneyin.'
      toast.error(msg)
    }

    return Promise.reject(error)
  },
)

export default axiosInstance
