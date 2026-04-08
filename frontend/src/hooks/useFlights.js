import { useState, useEffect, useCallback, useRef } from 'react'
import { flightApi, routeApi } from '../api/flightApi'

/** Yolcunun aktif biletleri */
export function useMyFlights() {
  const [data, setData]       = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState(null)
  const isMounted = useRef(true)

  const fetchData = useCallback(async () => {
    try {
      const res = await flightApi.getMyFlights()
      if (isMounted.current) {
        setData(res.data.data ?? [])
        setError(null)
      }
    } catch (err) {
      if (isMounted.current)
        setError(err.response?.data?.message || 'Uçuş bilgisi alınamadı')
    } finally {
      if (isMounted.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    isMounted.current = true
    fetchData()
    return () => { isMounted.current = false }
  }, [fetchData])

  return { data, loading, error, refetch: fetchData }
}

/** Kişisel rota önerisi */
export function useSuggestedRoute() {
  const [data, setData]       = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState(null)
  const isMounted = useRef(true)

  const fetchData = useCallback(async () => {
    try {
      const res = await routeApi.getSuggestedRoute()
      if (isMounted.current) {
        setData(res.data.data)
        setError(null)
      }
    } catch (err) {
      if (isMounted.current)
        setError(err.response?.data?.message || 'Rota bilgisi alınamadı')
    } finally {
      if (isMounted.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    isMounted.current = true
    fetchData()
    return () => { isMounted.current = false }
  }, [fetchData])

  return { data, loading, error, refetch: fetchData }
}

/** Alternatif sakin bölgeler */
export function useAlternatives(zoneId) {
  const [data, setData]       = useState([])
  const [loading, setLoading] = useState(false)
  const isMounted = useRef(true)

  useEffect(() => {
    if (!zoneId) return
    isMounted.current = true
    setLoading(true)
    routeApi.getAlternatives(zoneId)
      .then(res => { if (isMounted.current) setData(res.data.data ?? []) })
      .catch(() => {})
      .finally(() => { if (isMounted.current) setLoading(false) })
    return () => { isMounted.current = false }
  }, [zoneId])

  return { data, loading }
}
