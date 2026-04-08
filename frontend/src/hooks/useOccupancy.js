import { useState, useEffect, useCallback, useRef } from 'react'
import { occupancyApi } from '../api/occupancyApi'

/**
 * Heatmap verisi için custom hook.
 * - İlk yüklemede API'yi çağırır
 * - intervalMs (varsayılan 15s) süreyle otomatik yeniler
 * - Unmount'ta interval temizlenir (bellek sızıntısı yok)
 */
export function useOccupancy(intervalMs = 15000) {
  const [data, setData]       = useState(null)   // HeatmapResponse
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState(null)
  const [lastUpdated, setLastUpdated] = useState(null)
  const isMounted = useRef(true)

  const fetchData = useCallback(async () => {
    try {
      const res = await occupancyApi.getHeatmap()
      if (isMounted.current) {
        setData(res.data.data)
        setLastUpdated(new Date())
        setError(null)
      }
    } catch (err) {
      if (isMounted.current) {
        setError(err.response?.data?.message || 'Veri alınamadı')
      }
    } finally {
      if (isMounted.current) {
        setLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    isMounted.current = true
    fetchData()

    const interval = setInterval(fetchData, intervalMs)

    return () => {
      isMounted.current = false
      clearInterval(interval)
    }
  }, [fetchData, intervalMs])

  return { data, loading, error, lastUpdated, refetch: fetchData }
}

/** Tek bölge yoğunluğu için ayrı hook */
export function useZoneOccupancy(zoneId, intervalMs = 15000) {
  const [data, setData]       = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState(null)
  const isMounted = useRef(true)

  const fetchData = useCallback(async () => {
    if (!zoneId) return
    try {
      const res = await occupancyApi.getZoneOccupancy(zoneId)
      if (isMounted.current) {
        setData(res.data.data)
        setError(null)
      }
    } catch (err) {
      if (isMounted.current) {
        setError(err.response?.data?.message || 'Veri alınamadı')
      }
    } finally {
      if (isMounted.current) setLoading(false)
    }
  }, [zoneId])

  useEffect(() => {
    isMounted.current = true
    fetchData()
    const interval = setInterval(fetchData, intervalMs)
    return () => {
      isMounted.current = false
      clearInterval(interval)
    }
  }, [fetchData, intervalMs])

  return { data, loading, error, refetch: fetchData }
}
