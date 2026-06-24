import React from 'react'
import { Link } from 'react-router-dom'

const STATUS_STYLES = {
  SCHEDULED: 'bg-blue-500/20   text-blue-400   border-blue-500/30',
  BOARDING:  'bg-green-500/20  text-green-400  border-green-500/30',
  DEPARTED:  'bg-gray-500/20   text-gray-400   border-gray-500/30',
  DELAYED:   'bg-yellow-500/20 text-yellow-400 border-yellow-500/30',
  CANCELLED: 'bg-red-500/20    text-red-400    border-red-500/30',
}
const STATUS_LABELS = {
  SCHEDULED: 'Zamanında',
  BOARDING:  'Biniş',
  DEPARTED:  'Kalktı',
  DELAYED:   'Gecikmeli',
  CANCELLED: 'İptal',
}

const CLASS_LABEL = { ECONOMY: 'Ekonomi', BUSINESS: 'Business', FIRST: '1. Mevki' }

function minuteColor(mins) {
  if (mins < 0)  return 'text-gray-500'
  if (mins < 30) return 'text-red-400'
  if (mins < 60) return 'text-yellow-400'
  return 'text-green-400'
}

function formatDuration(mins) {
  if (mins < 0)  return 'Kalktı'
  if (mins < 60) return `${mins} dk`
  const h = Math.floor(mins / 60)
  const m = mins % 60
  return m > 0 ? `${h}s ${m}dk` : `${h} saat`
}

export default function FlightCard({
  flightId,
  flightCode,
  airline,
  iataCode,
  destination,
  origin,
  departureTime,
  minutesToDeparture,
  gateZoneName,
  gateDensityLevel,
  colorCode,
  seatNumber,
  flightClass,
  status,
  ticketId,
  onUnclaim,
}) {
  const minsNum   = Number(minutesToDeparture)
  const timeColor = minuteColor(minsNum)
  const badgeCls  = STATUS_STYLES[status] ?? STATUS_STYLES.SCHEDULED
  const statusLbl = STATUS_LABELS[status] ?? status

  const depTime = departureTime
    ? new Date(departureTime).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' })
    : '--:--'
  const depDate = departureTime
    ? new Date(departureTime).toLocaleDateString('tr-TR', { day: 'numeric', month: 'short' })
    : ''

  return (
    <div className="eco-card overflow-hidden hover:border-gray-600 transition-colors">
      {/* Üst Şerit: uçuş kodu + havayolu + status */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <span className="text-eco-green font-mono font-bold text-lg tracking-wide">
            {flightCode}
          </span>
          <span className="text-gray-500 text-sm">
            {iataCode} · {airline}
          </span>
        </div>
        <span className={`text-xs font-medium px-2.5 py-0.5 rounded-full border ${badgeCls}`}>
          {statusLbl}
        </span>
      </div>

      {/* Rota: Kalkış → Varış */}
      <div className="flex items-center gap-3 mb-4">
        <div className="text-center flex-shrink-0">
          <p className="text-xs text-gray-400">{origin?.split('(')[1]?.replace(')', '') ?? 'IST'}</p>
          <p className="text-2xl font-bold text-white">{depTime}</p>
          <p className="text-xs text-gray-500">{depDate}</p>
        </div>

        <div className="flex-1 flex items-center gap-1">
          <div className="flex-1 h-px bg-gray-700" />
          <svg className="w-5 h-5 text-eco-green flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
            <path d="M10.894 2.553a1 1 0 00-1.788 0l-7 14a1 1 0 001.169 1.409l5-1.429A1 1 0 009 15.571V11a1 1 0 112 0v4.571a1 1 0 00.725.962l5 1.428a1 1 0 001.17-1.408l-7-14z" />
          </svg>
          <div className="flex-1 h-px bg-gray-700" />
        </div>

        <div className="text-center flex-shrink-0">
          <p className="text-xs text-gray-400">
            {destination.match(/\((\w+)\)/)?.[1] ?? 'DEST'}
          </p>
          <p className="text-base font-semibold text-white leading-tight">
            {destination.split(' (')[0]}
          </p>
        </div>
      </div>

      {/* Alt Bilgiler */}
      <div className="flex items-center justify-between pt-3 border-t border-gray-700">
        {/* Kapı */}
        <div className="flex items-center gap-1.5">
          <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M17 8l4 4m0 0l-4 4m4-4H3" />
          </svg>
          <div>
            <p className="text-xs text-gray-400">Kapı</p>
            <p className="text-sm font-medium" style={{ color: colorCode ?? '#9CA3AF' }}>
              {gateZoneName}
            </p>
          </div>
        </div>

        {/* Koltuk */}
        <div className="text-center">
          <p className="text-xs text-gray-400">Koltuk</p>
          <p className="text-sm font-medium text-white">
            {seatNumber ?? '--'} <span className="text-xs text-gray-500">({CLASS_LABEL[flightClass] ?? flightClass})</span>
          </p>
        </div>

        {/* Kalkışa kalan süre */}
        <div className="text-right">
          <p className="text-xs text-gray-400">Kalkışa kalan</p>
          <p className={`text-sm font-bold ${timeColor}`}>
            {formatDuration(minsNum)}
          </p>
        </div>
      </div>

      {/* Alt butonlar */}
      {(flightId && minsNum > 0 || onUnclaim) && (
        <div className={`mt-3 flex gap-2 ${flightId && minsNum > 0 && onUnclaim ? 'flex-row' : ''}`}>
          {flightId && minsNum > 0 && (
            <Link
              to={`/passenger/route?flightId=${flightId}`}
              className="flex-1 flex items-center justify-center gap-1.5 py-2 px-3 rounded-lg
                         bg-eco-green/10 hover:bg-eco-green/20 text-eco-green text-sm font-medium
                         border border-eco-green/20 transition-colors"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7" />
              </svg>
              Rotamı Göster
            </Link>
          )}
          {onUnclaim && (
            <button
              onClick={() => {
                if (window.confirm('Bu bileti kaldırmak istediğinize emin misiniz?\nBilet PNR kodunuzla tekrar eklenebilir.')) {
                  onUnclaim(ticketId)
                }
              }}
              className="flex items-center justify-center gap-1.5 py-2 px-3 rounded-lg
                         bg-red-500/10 hover:bg-red-500/20 text-red-400 text-sm font-medium
                         border border-red-500/20 transition-colors"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
              Bileti Kaldır
            </button>
          )}
        </div>
      )}
    </div>
  )
}
