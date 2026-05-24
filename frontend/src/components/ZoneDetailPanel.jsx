import React, { useState, useEffect } from 'react'
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer
} from 'recharts'
import { getHeatmapHistory } from '../api/heatmap'

const STATUS_TR = { FULL: 'Dolu', BUSY: 'Yoğun', MODERATE: 'Normal', EMPTY: 'Boş' }
const RISK_COLOR = { HIGH: 'text-red-400', MEDIUM: 'text-amber-400', LOW: 'text-emerald-400' }

/**
 * Seçili zone'un detay paneli — 24h grafik, anlık durum, AI tahmini.
 */
export default function ZoneDetailPanel({ zone, onClose }) {
  const [history, setHistory] = useState([])
  const [loadingChart, setLoadingChart] = useState(true)

  useEffect(() => {
    if (!zone?.zoneId) return
    setLoadingChart(true)
    getHeatmapHistory(zone.zoneId, 24)
      .then(data => setHistory(data ?? []))
      .catch(() => setHistory([]))
      .finally(() => setLoadingChart(false))
  }, [zone?.zoneId])

  if (!zone) return null

  const density = zone.currentDensity ?? 0
  const pct     = Math.round(density * 100)
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

      {/* 24 saatlik grafik */}
      <div>
        <p className="text-xs text-gray-500 mb-2">Son 24 Saatlik Doluluk</p>
        {loadingChart ? (
          <div className="h-28 bg-gray-700/40 rounded animate-pulse" />
        ) : history.length < 2 ? (
          <div className="h-28 flex items-center justify-center text-gray-600 text-xs">
            Yeterli veri yok
          </div>
        ) : (
          <ResponsiveContainer width="100%" height={110}>
            <LineChart data={history} margin={{ top: 2, right: 4, left: -20, bottom: 2 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis
                dataKey="time"
                tick={{ fill: '#6B7280', fontSize: 9 }}
                tickLine={false}
                interval={Math.floor(history.length / 6)}
              />
              <YAxis
                tick={{ fill: '#6B7280', fontSize: 9 }}
                tickLine={false}
                axisLine={false}
                domain={[0, 1]}
                tickFormatter={v => `${Math.round(v * 100)}%`}
              />
              <Tooltip
                contentStyle={{
                  background: '#1F2937', border: '1px solid #374151',
                  borderRadius: '6px', fontSize: '10px'
                }}
                formatter={(v, name) => [`%${Math.round(v * 100)}`, 'Doluluk']}
              />
              <Line
                type="monotone"
                dataKey="densityPct"
                stroke={barColor}
                strokeWidth={2}
                dot={false}
                activeDot={{ r: 3 }}
              />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  )
}
