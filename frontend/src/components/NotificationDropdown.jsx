import React from 'react'
import { Link } from 'react-router-dom'

const TYPE_ICON = {
  CROWD_ALERT:      '⚠',
  ROUTE_SUGGESTION: '🗺',
  FLIGHT_UPDATE:    '✈',
  REWARD:           '🎁',
  SYSTEM:           '🔔',
}

export default function NotificationDropdown({ notifications, onMarkAsRead, onMarkAllAsRead, onClose }) {
  const recent = notifications.slice(0, 5)
  const unreadInList = recent.filter(n => !n.isRead).length

  return (
    <div className="absolute right-0 top-full mt-2 w-80 bg-gray-800 border border-gray-700
                    rounded-xl shadow-2xl z-50 overflow-hidden">
      {/* Başlık */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-700">
        <h3 className="text-white font-semibold text-sm">Bildirimler</h3>
        {unreadInList > 0 && (
          <button
            onClick={onMarkAllAsRead}
            className="text-eco-green text-xs hover:underline"
          >
            Tümünü Okundu İşaretle
          </button>
        )}
      </div>

      {/* Bildirim Listesi */}
      <div className="max-h-80 overflow-y-auto divide-y divide-gray-700/50">
        {recent.length === 0 ? (
          <div className="px-4 py-8 text-center text-gray-500 text-sm">
            Henüz bildirim yok 🔔
          </div>
        ) : recent.map(notif => (
          <button
            key={notif.notifId}
            onClick={() => onMarkAsRead(notif.notifId)}
            className={`w-full text-left flex items-start gap-3 px-4 py-3 transition-colors
                        hover:bg-gray-700/50
                        ${!notif.isRead ? 'border-l-2 border-eco-green' : 'border-l-2 border-transparent'}`}
          >
            <span className="text-base flex-shrink-0 mt-0.5">
              {TYPE_ICON[notif.type] ?? '🔔'}
            </span>
            <div className="flex-1 min-w-0">
              <div className="flex items-start justify-between gap-2">
                <p className={`text-sm font-medium truncate ${!notif.isRead ? 'text-white' : 'text-gray-400'}`}>
                  {notif.title}
                </p>
                <span className="text-gray-600 text-xs whitespace-nowrap flex-shrink-0">
                  {notif.timeAgo}
                </span>
              </div>
              <p className="text-gray-500 text-xs mt-0.5 line-clamp-2">{notif.body}</p>
            </div>
          </button>
        ))}
      </div>

      {/* Alt Linkler */}
      <div className="flex items-center justify-between px-4 py-2.5 border-t border-gray-700 bg-gray-900/50">
        <button
          onClick={onMarkAllAsRead}
          className="text-gray-500 text-xs hover:text-gray-300 transition-colors"
        >
          Tümünü Okundu İşaretle
        </button>
        <Link
          to="/passenger/notifications"
          onClick={onClose}
          className="text-eco-green text-xs hover:underline font-medium"
        >
          Tüm Bildirimler →
        </Link>
      </div>
    </div>
  )
}
