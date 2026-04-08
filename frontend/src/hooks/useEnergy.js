import { useState, useEffect, useCallback, useRef } from 'react'
import { energyApi } from '../api/adminApi'

/**
 * Tüm bölgelerin enerji durumu + tasarruf önerileri.
 */
export function useEnergy() {
  const [usage, setUsage]           = useState([])
  const [savings, setSavings]       = useState([])
  const [loadingUsage, setLoadingUsage] = useState(true)
  const [error, setError]           = useState(null)
  const isMounted = useRef(true)

  const fetchAll = useCallback(async () => {
    try {
      const [usageRes, savingsRes] = await Promise.all([
        energyApi.getAllUsage(),
        energyApi.getSavings(),
      ])
      if (isMounted.current) {
        setUsage(usageRes.data.data ?? [])
        setSavings(savingsRes.data.data ?? [])
        setError(null)
      }
    } catch (err) {
      if (isMounted.current)
        setError(err.response?.data?.message || 'Enerji verisi alınamadı')
    } finally {
      if (isMounted.current) setLoadingUsage(false)
    }
  }, [])

  useEffect(() => {
    isMounted.current = true
    fetchAll()
    return () => { isMounted.current = false }
  }, [fetchAll])

  return { usage, savings, loadingUsage, error, refetch: fetchAll }
}

/**
 * Tek bölgenin enerji trendi.
 */
export function useEnergyTrend(zoneId, hours = 6) {
  const [data, setData]       = useState([])
  const [loading, setLoading] = useState(false)
  const isMounted = useRef(true)

  useEffect(() => {
    if (!zoneId) return
    isMounted.current = true
    setLoading(true)
    energyApi.getTrend(zoneId, hours)
      .then(res => { if (isMounted.current) setData(res.data.data ?? []) })
      .catch(() => {})
      .finally(() => { if (isMounted.current) setLoading(false) })
    return () => { isMounted.current = false }
  }, [zoneId, hours])

  return { data, loading }
}
