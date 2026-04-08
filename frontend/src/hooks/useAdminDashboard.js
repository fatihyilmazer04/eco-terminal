import { useState, useEffect, useCallback, useRef } from 'react'
import { adminApi } from '../api/adminApi'

/**
 * Admin dashboard özet verisi.
 * Her 30 saniyede otomatik yenilenir.
 */
export function useAdminDashboard() {
  const [data, setData]       = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState(null)
  const [lastUpdated, setLastUpdated] = useState(null)
  const isMounted = useRef(true)

  const fetchData = useCallback(async () => {
    try {
      const res = await adminApi.getDashboard()
      if (isMounted.current) {
        setData(res.data.data)
        setLastUpdated(new Date())
        setError(null)
      }
    } catch (err) {
      if (isMounted.current)
        setError(err.response?.data?.message || 'Dashboard verisi alınamadı')
    } finally {
      if (isMounted.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    isMounted.current = true
    fetchData()
    const id = setInterval(fetchData, 30_000)
    return () => {
      isMounted.current = false
      clearInterval(id)
    }
  }, [fetchData])

  return { data, loading, error, lastUpdated, refetch: fetchData }
}
