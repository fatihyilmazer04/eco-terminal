import React from 'react'

const STATUS_TR = { FULL: 'Dolu', BUSY: 'Yoğun', MODERATE: 'Normal', EMPTY: 'Boş' }
const RISK_COLOR = { HIGH: 'text-red-400', MEDIUM: 'text-amber-400', LOW: 'text-emerald-400' }

/**
 * Seçili zone'un detay paneli — anlık durum, kapasite bar, AI tahmini.
 */
export default function ZoneDetailPanel({ zone, onClose }) {
  if (!zone) return null

  const density  = zone.currentDensity ?? 0
  const pct      = Math.round(density * 100)
  const statusTr = STATUS_TR[zone.status] ?? zone.status

  const barColor =
    zone.status === 'FULL'     ? '#EF4444' :
    zone.status === 'BUSY'     ? '#F59E0B' :
    zone.status === 'MODERATE' ? '#3B82F6' : '#10B981'

  return (
    <div className="rounded-xl border border-gray-700 bg-gray-800 p-4 flex flex-col gap-4">
      {/* Başlık */}
      <div className="flex items-start justify-between">
        <div>
          <h3 className="text-white font-bold text-base">{zone.zoneName}</h3>
          <p className="text-gray-500 text-xs mt-0.5">{zone.zoneType} · {zone.section ?? ''}</p>
        </div>
        <button
          onClick={onClose}
          className="text-gray-500 hover:text-gray-300 transition-colors p-1"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      {/* Kapasite bar */}
      <div>
        <div className="flex justify-between text-xs text-gray-400 mb-1">
          <span>Doluluk: %{pct}</span>
          <span>{zone.peopleCount ?? 0} / {zone.capacity ?? '?'} kişi</span>
        </div>
        <div className="h-3 rounded-full bg-gray-700 overflow-hidden">
          <div
            className="h-full rounded-full transition-all duration-500"
            style={{ width: `${Math.min(pct, 100)}%`, backgroundColor: barColor }}
          />
        </div>
        <div className="flex justify-between mt-1">
          <span
            className="text-xs font-semibold px-2 py-0.5 rounded-full"
            style={{ backgroundColor: `${barColor}22`, color: barColor }}
          >
            {statusTr}
          </span>
          <span className={`text-xs font-medium ${RISK_COLOR[zone.riskLevel] ?? 'text-gray-400'}`}>
            Risk: {zone.riskLevel ?? 'LOW'}
          </span>
        </div>
      </div>

      {/* AI Tahmini */}
      {zone.predictedLoad != null && (
        <div className="px-3 py-2 rounded-lg bg-gray-700/50 border border-gray-600">
          <p className="text-xs text-gray-500 mb-1">AI Tahmini (30 dk sonra)</p>
          <div className="flex items-center justify-between">
            <span className="text-white font-semibold">
              %{Math.round(zone.predictedLoad * 100)}
            </span>
            <span className="text-xs text-gray-400">
              {zone.trend === 'INCREASING' ? '↑ Artıyor' :
               zone.trend === 'DECREASING' ? '↓ Azalıyor' : '→ Sabit'}
            </span>
          </div>
        </div>
      )}
    </div>
  )
}
