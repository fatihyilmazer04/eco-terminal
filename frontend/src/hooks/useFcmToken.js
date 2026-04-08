import { useEffect } from 'react'
import { notificationApi } from '../api/notificationApi'

/**
 * Uygulama açılışında Notification izni ister ve FCM token'ı backend'e kaydeder.
 * İzin reddedilirse veya hata olursa sessizce atlar.
 *
 * Gerçek Firebase kurulumu için:
 *   1. firebase.json veya initializeApp() ile Firebase başlatılmalı
 *   2. getToken(messaging, { vapidKey: 'YOUR_VAPID_KEY' }) ile token alınmalı
 *   3. public/firebase-messaging-sw.js service worker aktif edilmeli
 *
 * Şu an simülasyon modu: izin istenir ama gerçek token alınamaz.
 */
export function useFcmToken() {
  useEffect(() => {
    if (!('Notification' in window)) return
    if (Notification.permission === 'denied') return

    const init = async () => {
      try {
        const permission = await Notification.requestPermission()
        if (permission !== 'granted') {
          console.log('[FCM] Bildirim izni reddedildi')
          return
        }
        // Simülasyon: gerçek Firebase token yerine placeholder
        // Gerçek uygulamada: const token = await getToken(messaging, { vapidKey })
        console.log('[FCM] Bildirim izni verildi — simülasyon modu, gerçek token alınmıyor')
      } catch (err) {
        console.log('[FCM] Token alınamadı:', err.message)
      }
    }

    init()
  }, [])
}
