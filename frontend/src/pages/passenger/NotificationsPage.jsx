import React, { useState } from 'react'
import { useNotifications } from '../../hooks/useNotifications'

const TYPE_ICON = {
  CROWD_ALERT:      '⚠',
  ROUTE_SUGGESTION: '🗺',
  FLIGHT_UPDATE:    '✈',
  REWARD:           '🎁',
  SYSTEM:           '🔔',
}

const TABS = [
  { key: 'ALL',          label: 'Tümü' },
  { key: 'UNREAD',       label: 'Okunmamış' },
  { key: 'CROWD_ALERT',  label: 'Yoğunluk' },
  { key: 'FLIGHT_UPDATE',label: 'Uçuş' },
]

export default function NotificationsPage() {
  const { notifications, unreadCount, loading, markAsRead, markAllAsRead } = useNotifications()
  const [activeTab, setActiveTab] = useState('ALL')

  const filtered = notifications.filter(n => {
    if (activeTab === 'ALL')    return true
    if (activeTab === 'UNREAD') return !n.isRead
    return n.type === activeTab
  })

  if (loading) return (
    <div className="min-h-screen bg-gray-900 p-6">
      <div className="animate-pulse space-y-3 max-w-2xl mx-auto">
        <div className="h-8 w-40 bg-gray-700 rounded" />
        {[1,2,3,4,5].map(i => <div key={i} className="h-20 bg-gray-800 rounded-xl" />)}
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
            {unreadCount > 0 && (
              <p className="text-gray-400 text-sm mt-0.5">{unreadCount} okunmamış</p>
            )}
          </div>
          {unreadCount > 0 && (
            <button
              onClick={markAllAsRead}
              className="px-3 py-1.5 rounded-lg bg-eco-green/10 border border-eco-green/30
                         text-eco-green text-sm font-medium hover:bg-eco-green/20 transition-colors"
            >
              Tümünü Okundu İşaretle
            </button>
          )}
        </div>

        {/* Filtre Tabları */}
        <div className="flex items-center gap-1 bg-gray-800/50 rounded-xl p-1">
          {TABS.map(tab => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`flex-1 py-1.5 rounded-lg text-xs font-medium transition-colors
                          ${activeTab === tab.key
                            ? 'bg-eco-green/20 text-eco-green border border-eco-green/30'
                            : 'text-gray-500 hover:text-gray-300'}`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* Bildirim Listesi */}
        {filtered.length === 0 ? (
          <div className="text-center py-16 text-gray-500">
            <p className="text-4xl mb-3">🔔</p>
            <p className="text-sm">Henüz bildirim yok</p>
          </div>
        ) : (
          <div className="space-y-2">
            {filtered.map(notif => (
              <button
                key={notif.notifId}
                onClick={() => !notif.isRead && markAsRead(notif.notifId)}
                className={`w-full text-left rounded-xl border p-4 flex items-start gap-3
                            transition-colors
                            ${!notif.isRead
                              ? 'bg-gray-800 border-eco-green/30 border-l-4 border-l-eco-green'
                              : 'bg-gray-900 border-gray-800 opacity-70'}`}
              >
                <span className="text-xl flex-shrink-0">{TYPE_ICON[notif.type] ?? '🔔'}</span>
                <div className="flex-1 min-w-0">
                  <div className="flex items-start justify-between gap-2">
                    <p className={`text-sm font-semibold ${!notif.isRead ? 'text-white' : 'text-gray-400'}`}>
                      {notif.title}
                    </p>
                    <span className="text-gray-600 text-xs whitespace-nowrap flex-shrink-0">
                      {notif.timeAgo}
                    </span>
                  </div>
                  <p className="text-gray-400 text-xs mt-1">{notif.body}</p>
                  {notif.zoneName && (
                    <span className="inline-block mt-1.5 px-2 py-0.5 rounded-full text-[10px]
                                     bg-gray-700 text-gray-400">
                      {notif.zoneName}
                    </span>
                  )}
                </div>
                {!notif.isRead && (
                  <span className="w-2 h-2 rounded-full bg-eco-green flex-shrink-0 mt-1.5" />
                )}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
