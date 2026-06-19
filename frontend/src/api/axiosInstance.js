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

// ── Refresh token race condition önleme ──────────────────────────────────────
// Aynı anda birden fazla istek 401 alırsa hepsi bağımsız refresh denemez.
// İlk istek refresh'i başlatır; diğerleri sıraya girer ve sonucu paylaşır.
let isRefreshing = false
let refreshQueue = [] // { resolve, reject }[]

function processQueue(error, token = null) {
  refreshQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error)
    else resolve(token)
  })
  refreshQueue = []
}

function forceLogout() {
  localStorage.clear()
  window.location.href = '/login'
}

// Response interceptor — 401 alınırsa refresh token ile yenile
axiosInstance.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      const refreshToken = localStorage.getItem('refreshToken')
      if (!refreshToken) {
        forceLogout()
        return Promise.reject(error)
      }

      // Başka bir refresh zaten uçuktaysa bu isteği kuyruğa al
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          refreshQueue.push({ resolve, reject })
        })
          .then((newToken) => {
            originalRequest.headers.Authorization = `Bearer ${newToken}`
            return axiosInstance(originalRequest)
          })
          .catch(() => Promise.reject(error))
      }

      isRefreshing = true

      try {
        const res = await axios.post(
          `${import.meta.env.VITE_API_BASE_URL ?? ''}/api/auth/refresh`,
          { refreshToken },
        )
        const { accessToken } = res.data.data
        localStorage.setItem('accessToken', accessToken)

        // Kuyruktaki tüm bekleyen istekleri yeni token ile serbest bırak
        processQueue(null, accessToken)

        originalRequest.headers.Authorization = `Bearer ${accessToken}`
        return axiosInstance(originalRequest)
      } catch (refreshError) {
        // Refresh başarısız — kuyruktakileri de hata ile bitir
        processQueue(refreshError, null)
        forceLogout()
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
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
