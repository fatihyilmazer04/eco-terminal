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
    const interval = setInterval(fetchAll, 15_000)
    return () => { isMounted.current = false; clearInterval(interval) }
  }, [fetchAll])

  return { usage, savings, loadingUsage, error, refetch: fetchAll }
}

