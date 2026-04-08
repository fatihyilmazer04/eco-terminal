import { useState, useEffect, useCallback, useRef } from 'react'
import toast from 'react-hot-toast'
import { notificationApi } from '../api/notificationApi'

/**
 * Kullanıcı bildirimlerini yöneten hook.
 * - İlk yüklemede bildirimleri + unread count'u çeker
 * - Her 30 saniyede unread count'u yeniler
 * - markAsRead / markAllAsRead mutation'ları sağlar
 */
export function useNotifications() {
  const [notifications, setNotifications] = useState([])
  const [unreadCount, setUnreadCount]     = useState(0)
  const [loading, setLoading]             = useState(true)
  const [error, setError]                 = useState(null)
  const isMounted = useRef(true)

  const fetchAll = useCallback(async () => {
    try {
      const [notifRes, countRes] = await Promise.all([
        notificationApi.getMy(),
        notificationApi.getUnreadCount(),
      ])
      if (!isMounted.current) return
      setNotifications(notifRes.data.data ?? [])
      setUnreadCount(countRes.data.data?.count ?? 0)
      setError(null)
    } catch (err) {
      if (isMounted.current)
        setError(err.response?.data?.message || 'Bildirimler alınamadı')
    } finally {
      if (isMounted.current) setLoading(false)
    }
  }, [])

  const refreshCount = useCallback(async () => {
    try {
      const res = await notificationApi.getUnreadCount()
      if (isMounted.current)
        setUnreadCount(res.data.data?.count ?? 0)
    } catch {
      // sessizce geç
    }
  }, [])

  const markAsRead = useCallback(async (notifId) => {
    try {
      await notificationApi.markAsRead(notifId)
      setNotifications(prev =>
        prev.map(n => n.notifId === notifId ? { ...n, isRead: true } : n)
      )
      setUnreadCount(prev => Math.max(0, prev - 1))
    } catch {
      // sessizce geç
    }
  }, [])

  const markAllAsRead = useCallback(async () => {
    try {
      await notificationApi.markAllAsRead()
      setNotifications(prev => prev.map(n => ({ ...n, isRead: true })))
      setUnreadCount(0)
      toast.success('Tüm bildirimler okundu işaretlendi')
    } catch {
      toast.error('İşlem başarısız')
    }
  }, [])

  useEffect(() => {
    isMounted.current = true
    fetchAll()
    const id = setInterval(refreshCount, 30_000)
    return () => {
      isMounted.current = false
      clearInterval(id)
    }
  }, [fetchAll, refreshCount])

  return { notifications, unreadCount, loading, error, markAsRead, markAllAsRead, refetch: fetchAll }
}
