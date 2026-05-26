import { useState, useEffect, useCallback, useRef } from 'react'
import toast from 'react-hot-toast'
import { profileApi } from '../api/profileApi'

export function useProfile() {
  const [profile, setProfile] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState(null)
  const isMounted = useRef(true)

  const fetchProfile = useCallback(async () => {
    try {
      const res = await profileApi.getProfile()
      if (isMounted.current) {
        setProfile(res.data.data)
        setError(null)
      }
    } catch (err) {
      if (isMounted.current)
        setError(err.response?.data?.message || 'Profil alınamadı')
    } finally {
      if (isMounted.current) setLoading(false)
    }
  }, [])

  const updateProfile = useCallback(async (data) => {
    try {
      const res = await profileApi.updateProfile(data)
      if (isMounted.current) setProfile(res.data.data)
      toast.success('Profil güncellendi')
      return res.data.data
    } catch (err) {
      toast.error(err.response?.data?.message || 'Güncelleme başarısız')
      throw err
    }
  }, [])

  const updatePreferences = useCallback(async (data) => {
    try {
      const res = await profileApi.updatePreferences(data)
      if (isMounted.current) setProfile(res.data.data)
      return res.data.data
    } catch (err) {
      toast.error(err.response?.data?.message || 'Tercihler kaydedilemedi')
      throw err
    }
  }, [])

  useEffect(() => {
    isMounted.current = true
    fetchProfile()
    return () => { isMounted.current = false }
  }, [fetchProfile])

  return { profile, loading, error, updateProfile, updatePreferences, refetch: fetchProfile }
}
