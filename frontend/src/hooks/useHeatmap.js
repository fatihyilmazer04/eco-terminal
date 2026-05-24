import { useState, useEffect, useCallback, useRef } from 'react'
import { getHeatmapLive, refreshHeatmap } from '../api/heatmap'

/**
 * Heatmap veri hook'u — anlık zone durumları, otomatik yenileme, manuel refresh.
 *
 * @param {number} intervalMs - Otomatik yenileme aralığı (ms), 0 → devre dışı
 * @returns {{ data, loading, error, refresh, lastUpdated }}
 */
export function useHeatmap(intervalMs = 60000) {
  const [data, setData]             = useState(null)
  const [loading, setLoading]       = useState(true)
  const [error, setError]           = useState(null)
  const [lastUpdated, setLastUpdated] = useState(null)
  const [refreshing, setRefreshing] = useState(false)
  const isMounted = useRef(true)

  const fetchLive = useCallback(async () => {
    try {
      const result = await getHeatmapLive()
      if (!isMounted.current) return
      setData(result)
      setLastUpdated(new Date())
      setError(null)
    } catch (err) {
      if (isMounted.current)
        setError(err.response?.data?.message || 'Heatmap verisi alınamadı')
    } finally {
      if (isMounted.current) setLoading(false)
    }
  }, [])

  /** Admin refresh — AI tahminleri yenileyip güncel veriyi çeker */
  const refresh = useCallback(async () => {
    setRefreshing(true)
    try {
      const result = await refreshHeatmap()
      if (!isMounted.current) return
      setData(result)
      setLastUpdated(new Date())
      setError(null)
    } catch {
      // Refresh başarısız → normal fetch dene
      await fetchLive()
    } finally {
      if (isMounted.current) setRefreshing(false)
    }
  }, [fetchLive])

  useEffect(() => {
    isMounted.current = true
    fetchLive()

    if (intervalMs > 0) {
      const id = setInterval(fetchLive, intervalMs)
      return () => {
        isMounted.current = false
        clearInterval(id)
      }
    }
    return () => { isMounted.current = false }
  }, [fetchLive, intervalMs])

  return { data, loading, error, refresh, lastUpdated, refreshing }
}
