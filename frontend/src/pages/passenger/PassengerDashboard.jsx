import React, { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { useMyFlights } from '../../hooks/useFlights'
import { useNotifications } from '../../hooks/useNotifications'
import { loungeApi } from '../../api/loungeApi'
import EcoPointsCard from '../../components/EcoPointsCard'

// ── Yardımcılar ───────────────────────────────────────────────────────────────

function formatDepartureTime(instant) {
  if (!instant) return '—'
  return new Date(instant).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' })
}

function formatCountdown(mins) {
  if (mins == null) return '—'
  if (mins <= 0) return 'Kalktı'
  const h = Math.floor(mins / 60)
  const m = mins % 60
  if (h === 0) return `${m} dakika kaldı`
  if (m === 0) return `${h} saat kaldı`
  return `${h} saat ${m} dakika kaldı`
}

const CLASS_LABELS = {
  ECONOMY:  'Economy',
  BUSINESS: 'Business',
  FIRST:    'First Class',
}

const NOTIF_ICONS = {
  CROWD_ALERT: '⚠️',
  REWARD:      '🌿',
  ROUTE:       '🗺️',
  FLIGHT:      '✈️',
  SYSTEM:      '🔔',
}

// ── Alt bileşenler ────────────────────────────────────────────────────────────

/** Yaklaşan uçuş özeti kartı */
function UpcomingFlightCard({ flights, loading }) {
  if (loading) {
    return (
      <div className="rounded-2xl border border-gray-700 bg-gray-800 p-5 animate-pulse">
        <div className="h-4 w-32 bg-gray-700 rounded mb-4" />
        <div className="h-8 w-48 bg-gray-700 rounded mb-3" />
        <div className="h-4 w-64 bg-gray-700 rounded mb-4" />
        <div className="h-10 w-full bg-gray-700 rounded-xl" />
      </div>
    )
  }

  // En yakın gelecekteki uçuşu bul
  const upcoming = flights
    .filter(f => (f.minutesToDeparture ?? -1) > -30) // -30 toleransı: yeni kalktıysa göster
    .sort((a, b) => (a.minutesToDeparture ?? 99999) - (b.minutesToDeparture ?? 99999))[0] ?? null

  if (!upcoming) {
    return (
      <div className="rounded-2xl border border-gray-700 bg-gray-800 p-5 flex items-center gap-4">
        <span className="text-3xl">✈️</span>
        <div>
          <p className="text-white font-semibold text-sm">Yaklaşan uçuşunuz yok</p>
          <p className="text-gray-500 text-xs mt-0.5">Bilet satın aldığınızda burada görünür</p>
        </div>
        <Link to="/passenger/flights"
              className="ml-auto text-xs text-eco-green hover:text-green-400 whitespace-nowrap">
          Uçuşlar →
        </Link>
      </div>
    )
  }

  const mins   = upcoming.minutesToDeparture ?? 0
  const urgent = mins < 60
  const soon   = mins < 120

  const urgencyColor = urgent ? '#E74C3C' : soon ? '#F39C12' : '#2ECC71'
  const urgencyBg    = urgent ? 'bg-red-500/10 border-red-500/30'
                     : soon   ? 'bg-yellow-500/10 border-yellow-500/30'
                     :          'bg-eco-green/10 border-eco-green/30'

  return (
    <div className={`rounded-2xl border p-5 ${urgencyBg}`}>
      {/* Üst satır: uçuş kodu + durum */}
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <span className="text-lg">✈️</span>
          <span className="text-white font-bold text-lg tracking-wide">
            {upcoming.flightCode}
          </span>
          {upcoming.iataCode && (
            <span className="text-xs text-gray-500 font-mono">{upcoming.iataCode}</span>
          )}
        </div>
        <span
          className="text-xs font-bold px-2.5 py-1 rounded-full"
          style={{ color: urgencyColor, background: urgencyColor + '20', border: `1px solid ${urgencyColor}40` }}
        >
          {formatCountdown(mins)}
        </span>
      </div>

      {/* Varış */}
      <div className="flex items-center gap-3 mb-4">
        <div>
          <p className="text-xs text-gray-500 uppercase tracking-wide">Varış</p>
          <p className="text-white font-semibold text-xl">{upcoming.destination}</p>
        </div>
        <div className="flex-1 flex items-center gap-1 px-3">
          <div className="flex-1 h-px bg-gray-600" />
          <div className="w-1.5 h-1.5 rounded-full bg-gray-500" />
        </div>
        <div className="text-right">
          <p className="text-xs text-gray-500 uppercase tracking-wide">Kalkış</p>
          <p className="text-white font-bold text-2xl tabular-nums">
            {formatDepartureTime(upcoming.departureTime)}
          </p>
        </div>
      </div>

      {/* Alt detaylar */}
      <div className="flex items-center gap-4 text-xs text-gray-400 mb-4">
        {upcoming.gateZoneName && (
          <div className="flex items-center gap-1">
            <span>🚪</span>
            <span className="text-white font-medium">{upcoming.gateZoneName}</span>
          </div>
        )}
        {upcoming.flightClass && (
          <div className="flex items-center gap-1">
            <span>💺</span>
            <span>{CLASS_LABELS[upcoming.flightClass] ?? upcoming.flightClass}</span>
          </div>
        )}
        {upcoming.seatNumber && (
          <div className="flex items-center gap-1">
            <span>🪑</span>
            <span>Koltuk {upcoming.seatNumber}</span>
          </div>
        )}
        {upcoming.airline && (
          <div className="flex items-center gap-1">
            <span className="text-gray-500">{upcoming.airline}</span>
          </div>
        )}
      </div>

      {/* Rotayı Gör butonu */}
      <Link
        to="/passenger/route"
        className="block w-full py-2.5 rounded-xl text-center text-sm font-bold
                   bg-eco-green text-gray-900 hover:bg-green-400 active:scale-95
                   transition-all shadow-md shadow-eco-green/20"
      >
        🧭 Rotayı Gör
      </Link>
    </div>
  )
}

/** Okunmamış bildirim özeti kartı */
function NotificationSummaryCard({ notifications, unreadCount, loading }) {
  if (loading) {
    return (
      <div className="rounded-2xl border border-gray-700 bg-gray-800 p-4 animate-pulse">
        <div className="h-4 w-40 bg-gray-700 rounded mb-3" />
        {[1, 2].map(i => <div key={i} className="h-8 bg-gray-700 rounded-xl mb-2" />)}
      </div>
    )
  }

  const unread = notifications.filter(n => !n.isRead).slice(0, 3)

  return (
    <div className="rounded-2xl border border-gray-700 bg-gray-800 p-4">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <span className="text-base">🔔</span>
          <span className="text-white text-sm font-semibold">Bildirimler</span>
          {unreadCount > 0 && (
            <span className="text-xs bg-red-500/20 border border-red-500/30 text-red-400
                             px-2 py-0.5 rounded-full font-bold">
              {unreadCount} yeni
            </span>
          )}
        </div>
        <Link to="/passenger/notifications"
              className="text-xs text-eco-green hover:text-green-400">
          Tümünü Gör →
        </Link>
      </div>

      {unread.length === 0 ? (
        <p className="text-gray-500 text-xs py-2">Yeni bildiriminiz yok</p>
      ) : (
        <div className="space-y-2">
          {unread.map(n => (
            <div key={n.notifId}
                 className="flex items-start gap-2.5 px-3 py-2 rounded-xl bg-gray-700/50">
              <span className="text-sm flex-shrink-0 mt-0.5">
                {NOTIF_ICONS[n.notificationType] ?? '🔔'}
              </span>
              <p className="text-gray-200 text-xs leading-snug line-clamp-2">{n.title}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

/** Hızlı erişim butonları */
function QuickAccess() {
  const links = [
    { to: '/passenger/heatmap',       icon: '🗺️', label: 'Isı Haritası' },
    { to: '/passenger/route',         icon: '🧭', label: 'Rota Öner'    },
    { to: '/passenger/rewards',       icon: '🌿', label: 'Ödüller'      },
    { to: '/passenger/notifications', icon: '🔔', label: 'Bildirimler'  },
  ]

  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
      {links.map(({ to, icon, label }) => (
        <Link
          key={to}
          to={to}
          className="flex flex-col items-center justify-center gap-2 py-4 rounded-xl
                     border border-gray-700 bg-gray-800 hover:border-eco-green/40
                     hover:bg-eco-green/5 transition-all group"
        >
          <span className="text-2xl group-hover:scale-110 transition-transform">{icon}</span>
          <span className="text-xs text-gray-400 group-hover:text-white transition-colors font-medium">
            {label}
          </span>
        </Link>
      ))}
    </div>
  )
}

/** En sakin lounge önerisi kartı */
function BestLoungeCard() {
  const [lounge, setLounge]   = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loungeApi.getBestLounge()
      .then(r => setLounge(r.data.data))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  if (loading) {
    return (
      <div className="rounded-2xl border border-gray-700 bg-gray-800 p-4 animate-pulse">
        <div className="h-4 w-40 bg-gray-700 rounded mb-3" />
        <div className="h-6 w-32 bg-gray-700 rounded" />
      </div>
    )
  }

  if (!lounge) return null

  const pct      = Math.round((lounge.densityPct ?? 0) * 100)
  const stars    = lounge.comfortScore ?? 3
  const starStr  = '★'.repeat(stars) + '☆'.repeat(5 - stars)

  return (
    <div className="rounded-2xl border border-eco-green/20 bg-eco-green/5 p-4
                    flex items-center justify-between gap-4">
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-xl bg-eco-green/15 border border-eco-green/30
                        flex items-center justify-center flex-shrink-0">
          <span className="text-lg">🛋</span>
        </div>
        <div>
          <p className="text-xs text-gray-400 mb-0.5">Şu an en sakin salon</p>
          <p className="text-white font-semibold text-sm">{lounge.zoneName}</p>
          <div className="flex items-center gap-2 mt-0.5">
            <span className="text-xs text-eco-green font-medium">%{pct} dolu</span>
            <span className="text-yellow-400 text-xs tracking-tighter">{starStr}</span>
            {lounge.suggestion && (
              <span className="text-xs text-gray-500">· {lounge.suggestion}</span>
            )}
          </div>
        </div>
      </div>
      <Link
        to="/passenger/lounges"
        className="text-xs text-eco-green hover:text-green-400 whitespace-nowrap flex-shrink-0"
      >
        Tümünü Gör →
      </Link>
    </div>
  )
}

// ── Ana sayfa ─────────────────────────────────────────────────────────────────

export default function PassengerDashboard() {
  const { user } = useAuth()
  const { data: flights, loading: flightsLoading } = useMyFlights()
  const { notifications, unreadCount, loading: notifLoading } = useNotifications()
  return (
    <div className="min-h-screen bg-gray-900 p-6 max-w-2xl mx-auto space-y-5">

      {/* Karşılama */}
      <div>
        <h1 className="text-2xl font-bold text-white">
          Merhaba, {user?.fullName?.split(' ')[0] ?? 'Yolcu'} 👋
        </h1>
        <p className="text-gray-400 text-sm mt-0.5">
          Terminal durumu gerçek zamanlı güncelleniyor
        </p>
      </div>

      {/* 1 — Yaklaşan uçuş özeti */}
      <UpcomingFlightCard flights={flights} loading={flightsLoading} />

      {/* 3 — Eko-Puan Kartı */}
      <EcoPointsCard />

      {/* 4 — Okunmamış bildirim özeti */}
      <NotificationSummaryCard
        notifications={notifications}
        unreadCount={unreadCount}
        loading={notifLoading}
      />

      {/* 5 — Hızlı erişim */}
      <div>
        <p className="text-xs text-gray-500 uppercase tracking-widest mb-3 font-semibold">
          Hızlı Erişim
        </p>
        <QuickAccess />
      </div>

      {/* 6 — En sakin lounge önerisi */}
      <BestLoungeCard />

    </div>
  )
}
