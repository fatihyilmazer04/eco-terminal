import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useNotifications } from '../../hooks/useNotifications'

// Tip → ikon + renk
const TYPE_CONFIG = {
  CROWD_ALERT:      { icon: '⚠️', color: '#F59E0B', label: 'Yoğunluk' },
  ROUTE_SUGGESTION: { icon: '🗺️', color: '#2ECC71', label: 'Rota'     },
  FLIGHT_UPDATE:    { icon: '✈️', color: '#3B82F6', label: 'Uçuş'     },
  REWARD:           { icon: '🌿', color: '#2ECC71', label: 'Ödül'      },
  SYSTEM:           { icon: '🔔', color: '#64748B', label: 'Sistem'    },
}

const TABS = [
  { key: 'ALL',          label: 'Tümü'      },
  { key: 'UNREAD',       label: 'Okunmamış' },
  { key: 'CROWD_ALERT',  label: 'Yoğunluk'  },
  { key: 'FLIGHT_UPDATE',label: 'Uçuş'      },
  { key: 'REWARD',       label: 'Ödül'      },
]

function NotifCard({ notif, onMarkRead }) {
  const cfg = TYPE_CONFIG[notif.type] ?? TYPE_CONFIG.SYSTEM
  const isUnread = !notif.isRead

  return (
    <button
      onClick={() => isUnread && onMarkRead(notif.notifId)}
      className={`
        w-full text-left rounded-xl border p-4
        flex items-start gap-3 transition-all group
        ${isUnread
          ? 'bg-gray-800 border-gray-700 border-l-4 hover:bg-gray-750'
          : 'bg-gray-900/60 border-gray-800/60 opacity-60 hover:opacity-80'}
      `}
      style={isUnread ? { borderLeftColor: cfg.color } : undefined}
    >
      {/* İkon */}
      <div
        className="flex-shrink-0 w-9 h-9 rounded-xl flex items-center justify-center text-base"
        style={{ backgroundColor: cfg.color + '20', border: `1px solid ${cfg.color}30` }}
      >
        {cfg.icon}
      </div>

      {/* İçerik */}
      <div className="flex-1 min-w-0">
        <div className="flex items-start justify-between gap-2">
          <p className={`text-sm font-semibold leading-tight ${isUnread ? 'text-white' : 'text-gray-400'}`}>
            {notif.title}
          </p>
          <span className="text-gray-600 text-[11px] whitespace-nowrap flex-shrink-0 mt-0.5">
            {notif.timeAgo}
          </span>
        </div>

        {notif.body && (
          <p className="text-gray-400 text-xs mt-1 leading-relaxed line-clamp-2">
            {notif.body}
          </p>
        )}

        <div className="flex items-center gap-2 mt-1.5">
          {/* Tip etiketi */}
          <span
            className="text-[10px] px-1.5 py-0.5 rounded font-medium"
            style={{ color: cfg.color, backgroundColor: cfg.color + '15' }}
          >
            {cfg.label}
          </span>
          {/* Zone etiketi */}
          {notif.zoneName && (
            <span className="text-[10px] px-1.5 py-0.5 rounded bg-gray-700 text-gray-400">
              {notif.zoneName}
            </span>
          )}
          {/* Okundu ipucu */}
          {isUnread && (
            <span className="text-[10px] text-gray-600 group-hover:text-gray-400 transition-colors ml-auto">
              Okundu işaretle →
            </span>
          )}
        </div>
      </div>

      {/* Okunmamış nokta */}
      {isUnread && (
        <span
          className="w-2 h-2 rounded-full flex-shrink-0 mt-1.5"
          style={{ backgroundColor: cfg.color }}
        />
      )}
    </button>
  )
}

export default function NotificationsPage() {
  const { notifications, unreadCount, loading, error, markAsRead, markAllAsRead } = useNotifications()
  const [activeTab, setActiveTab] = useState('ALL')

  const filtered = notifications.filter(n => {
    if (activeTab === 'ALL')    return true
    if (activeTab === 'UNREAD') return !n.isRead
    return n.type === activeTab
  })

  // Tab'lara okunmamış sayısı ekle
  const tabCounts = {
    ALL:          notifications.length,
    UNREAD:       unreadCount,
    CROWD_ALERT:  notifications.filter(n => n.type === 'CROWD_ALERT'  && !n.isRead).length,
    FLIGHT_UPDATE:notifications.filter(n => n.type === 'FLIGHT_UPDATE'&& !n.isRead).length,
    REWARD:       notifications.filter(n => n.type === 'REWARD'       && !n.isRead).length,
  }

  if (loading) return (
    <div className="min-h-screen bg-gray-900 p-6">
      <div className="animate-pulse space-y-3 max-w-2xl mx-auto">
        <div className="h-8 w-40 bg-gray-700 rounded mb-6" />
        {[1,2,3,4,5].map(i => (
          <div key={i} className="h-20 bg-gray-800 rounded-xl border border-gray-700" />
        ))}
      </div>
    </div>
  )

  return (
    <div className="min-h-screen bg-gray-900 p-6">
      <div className="max-w-2xl mx-auto space-y-4">

        {/* Başlık */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-white">Bildirimler</h1>
            <p className="text-gray-500 text-sm mt-0.5">
              {unreadCount > 0
                ? <span className="text-eco-green">{unreadCount} okunmamış</span>
                : 'Tüm bildirimler okundu'}
            </p>
          </div>
          {unreadCount > 0 && (
            <button
              onClick={markAllAsRead}
              className="px-3 py-1.5 rounded-lg bg-eco-green/10 border border-eco-green/30
                         text-eco-green text-sm font-medium hover:bg-eco-green/20 transition-colors
                         flex items-center gap-1.5"
            >
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
              Tümünü Okundu İşaretle
            </button>
          )}
        </div>

        {/* Hata mesajı */}
        {error && (
          <div className="px-4 py-3 rounded-xl bg-yellow-500/10 border border-yellow-500/30 text-yellow-400 text-sm">
            {error}
          </div>
        )}

        {/* Filtre Tabları */}
        <div className="flex items-center gap-1 bg-gray-800/50 rounded-xl p-1 overflow-x-auto">
          {TABS.map(tab => {
            const count = tabCounts[tab.key]
            const isActive = activeTab === tab.key
            return (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                className={`
                  flex-shrink-0 flex items-center gap-1.5 px-3 py-1.5 rounded-lg
                  text-xs font-medium transition-colors
                  ${isActive
                    ? 'bg-eco-green/20 text-eco-green border border-eco-green/30'
                    : 'text-gray-500 hover:text-gray-300'}
                `}
              >
                {tab.label}
                {count > 0 && tab.key !== 'ALL' && (
                  <span className={`
                    text-[10px] px-1 min-w-[16px] h-4 rounded-full flex items-center justify-center
                    ${isActive ? 'bg-eco-green/30 text-eco-green' : 'bg-gray-700 text-gray-400'}
                  `}>
                    {count}
                  </span>
                )}
              </button>
            )
          })}
        </div>

        {/* Bildirim Listesi */}
        {filtered.length === 0 ? (
          <div className="text-center py-16 text-gray-500">
            <div className="w-16 h-16 rounded-2xl bg-gray-800 flex items-center justify-center mx-auto mb-4">
              <span className="text-3xl">
                {activeTab === 'UNREAD' ? '✅' : '🔔'}
              </span>
            </div>
            <p className="text-white font-medium mb-1">
              {activeTab === 'UNREAD' ? 'Tüm bildirimler okundu!' : 'Bildirim yok'}
            </p>
            <p className="text-gray-600 text-sm">
              {activeTab === 'UNREAD'
                ? 'Yeni bildirim geldiğinde burada görünür.'
                : 'Bu kategoride henüz bildiriminiz yok.'}
            </p>
          </div>
        ) : (
          <>
            <div className="space-y-2">
              {filtered.map(notif => (
                <NotifCard
                  key={notif.notifId}
                  notif={notif}
                  onMarkRead={markAsRead}
                />
              ))}
            </div>

            {/* Bildirim sayısı notu */}
            <p className="text-center text-xs text-gray-700 pb-2">
              {filtered.length} bildirim gösteriliyor
              {notifications.length === 50 && ' (son 50)'}
            </p>
          </>
        )}
      </div>
    </div>
  )
}
