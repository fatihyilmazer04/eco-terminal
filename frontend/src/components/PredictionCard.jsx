import React from 'react'
import RiskBadge from './RiskBadge'

const RISK_BG = {
  HIGH:   'bg-red-500/10 border-red-500/30',
  MEDIUM: 'bg-orange-500/10 border-orange-500/30',
  LOW:    'bg-gray-800 border-gray-700',
}

const RISK_LOAD_COLOR = {
  HIGH:   'text-red-400',
  MEDIUM: 'text-orange-400',
  LOW:    'text-eco-green',
}

function formatTime(isoString) {
  if (!isoString) return '--'
  const d = new Date(isoString)
  return d.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' })
}

/**
 * Tek bölge AI tahmin kartı.
 * Props: prediction — { zoneId, zoneName, forecastTime, predictedLoad,
 *                       densityPct, riskLevel, trend, confidence, generatedAt }
 */
export default function PredictionCard({ prediction }) {
  const {
    zoneName, forecastTime, predictedLoad,
    densityPct, riskLevel, trend, confidence, generatedAt
  } = prediction

  const bg        = RISK_BG[riskLevel]    ?? RISK_BG.LOW
  const loadColor = RISK_LOAD_COLOR[riskLevel] ?? RISK_LOAD_COLOR.LOW
  const loadPct   = ((predictedLoad ?? 0) * 100).toFixed(1)
  const densityPctVal = ((densityPct ?? 0) * 100).toFixed(0)
  const confidencePct = ((confidence ?? 0) * 100).toFixed(0)

  return (
    <div className={`rounded-xl border p-4 flex flex-col gap-3 ${bg}`}>
      {/* Başlık + Risk Badge */}
      <div className="flex items-start justify-between gap-2">
        <h3 className="text-white font-semibold text-sm leading-tight">{zoneName}</h3>
        <RiskBadge riskLevel={riskLevel} trend={trend} />
      </div>

      {/* Büyük yük değeri */}
      <div className="text-center py-2">
        <p className={`text-4xl font-bold ${loadColor}`}>%{loadPct}</p>
        <p className="text-gray-500 text-xs mt-1">tahmini doluluk</p>
      </div>

      {/* Alt detaylar */}
      <div className="grid grid-cols-2 gap-2 text-xs">
        <div className="bg-black/20 rounded-lg px-2 py-1.5">
          <p className="text-gray-500">Tahmin Zamanı</p>
          <p className="text-gray-300 font-medium">{formatTime(forecastTime)}</p>
        </div>
        <div className="bg-black/20 rounded-lg px-2 py-1.5">
          <p className="text-gray-500">Güncel Yoğunluk</p>
          <p className="text-gray-300 font-medium">%{densityPctVal}</p>
        </div>
        <div className="bg-black/20 rounded-lg px-2 py-1.5">
          <p className="text-gray-500">Güven</p>
          <p className="text-gray-300 font-medium">%{confidencePct}</p>
        </div>
        <div className="bg-black/20 rounded-lg px-2 py-1.5">
          <p className="text-gray-500">Üretildi</p>
          <p className="text-gray-300 font-medium">{formatTime(generatedAt)}</p>
        </div>
      </div>
    </div>
  )
}
