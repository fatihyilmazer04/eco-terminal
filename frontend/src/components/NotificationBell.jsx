import React, { useRef, useState, useCallback, useEffect } from 'react'
import { useClickOutside } from '../hooks/useClickOutside'
import NotificationDropdown from './NotificationDropdown'

/**
 * Navbar'a eklenen bildirim çan ikonu.
 * - Okunmamış varsa kırmızı badge (max 9+)
 * - Yeni bildirim gelince 3 sn pulse animasyonu
 * - Tıklayınca NotificationDropdown açılır
 */
export default function NotificationBell({ notifications, unreadCount, onMarkAsRead, onMarkAllAsRead }) {
  const [open, setOpen] = useState(false)
  const [pulsing, setPulsing] = useState(false)
  const containerRef = useRef(null)
  const prevCount = useRef(unreadCount)

  // Yeni bildirim gelince 3 sn pulse
  useEffect(() => {
    if (unreadCount > prevCount.current) {
      setPulsing(true)
      const t = setTimeout(() => setPulsing(false), 3000)
      return () => clearTimeout(t)
    }
    prevCount.current = unreadCount
  }, [unreadCount])

  const close = useCallback(() => setOpen(false), [])
  useClickOutside(containerRef, close)

  const badgeLabel = unreadCount > 9 ? '9+' : String(unreadCount)

  return (
    <div ref={containerRef} className="relative">
      <button
        onClick={() => setOpen(prev => !prev)}
        className={`relative p-2 rounded-lg text-gray-300 hover:text-white hover:bg-gray-700
                    transition-colors ${pulsing ? 'animate-pulse' : ''}`}
        aria-label="Bildirimler"
      >
        {/* Çan ikonu */}
        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
            d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
        </svg>

        {/* Okunmamış badge */}
        {unreadCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 min-w-[1.1rem] h-[1.1rem] flex items-center
                           justify-center rounded-full bg-red-500 text-white text-[10px] font-bold px-0.5">
            {badgeLabel}
          </span>
        )}
      </button>

      {open && (
        <NotificationDropdown
          notifications={notifications}
          onMarkAsRead={(id) => { onMarkAsRead(id) }}
          onMarkAllAsRead={() => { onMarkAllAsRead(); }}
          onClose={close}
        />
      )}
    </div>
  )
}
