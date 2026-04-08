import { useState, useEffect, useCallback, useRef } from 'react'
import { predictionApi } from '../api/predictionApi'

/**
 * Tüm AI tahminlerini 5 dakikada bir yenileyen hook.
 * returns { predictions, highRisk, loading, error, lastUpdated, refresh, refreshing }
 */
export function useAIPredictions() {
  const [predictions, setPredictions] = useState([])
  const [highRisk, setHighRisk]       = useState([])
  const [loading, setLoading]         = useState(true)
  const [error, setError]             = useState(null)
  const [lastUpdated, setLastUpdated] = useState(null)
  const [refreshing, setRefreshing]   = useState(false)
  const isMounted = useRef(true)

  const fetchAll = useCallback(async () => {
    try {
      const [allRes, highRes] = await Promise.all([
        predictionApi.getAll(),
        predictionApi.getHighRisk(),
      ])
      if (!isMounted.current) return
      setPredictions(allRes.data.data ?? [])
      setHighRisk(highRes.data.data ?? [])
      setLastUpdated(new Date())
      setError(null)
    } catch (err) {
      if (isMounted.current)
        setError(err.response?.data?.message || 'AI tahminleri alınamadı')
    } finally {
      if (isMounted.current) setLoading(false)
    }
  }, [])

  // Manuel yenile — POST /refresh, sonra yeniden fetch
  const refresh = useCallback(async () => {
    setRefreshing(true)
    try {
      await predictionApi.refresh()
      await fetchAll()
    } catch (err) {
      if (isMounted.current)
        setError(err.response?.data?.message || 'AI yenileme başarısız')
    } finally {
      if (isMounted.current) setRefreshing(false)
    }
  }, [fetchAll])

  useEffect(() => {
    isMounted.current = true
    fetchAll()
    // 5 dakikada bir otomatik yenile
    const id = setInterval(fetchAll, 5 * 60 * 1000)
    return () => {
      isMounted.current = false
      clearInterval(id)
    }
  }, [fetchAll])

  return { predictions, highRisk, loading, error, lastUpdated, refresh, refreshing }
}
