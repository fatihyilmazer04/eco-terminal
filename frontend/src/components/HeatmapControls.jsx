import React, { useState } from 'react'

const INTERVAL_OPTIONS = [
  { label: '30 sn', value: 30000 },
  { label: '1 dk',  value: 60000 },
  { label: '5 dk',  value: 300000 },
]

/**
 * Heatmap kontrol çubuğu — son güncelleme, oto-yenileme toggle, manuel refresh.
 *
 * @param {Date}     lastUpdated      - Son başarılı fetch zamanı
 * @param {Function} onRefresh        - Manuel yenileme butonu callback'i
 * @param {boolean}  refreshing       - Yenileme devam ediyor mu
 * @param {boolean}  isAdmin          - Admin ise refresh butonu gösterilir
 */
export default function HeatmapControls({
  lastUpdated,
  onRefresh,
  refreshing = false,
  isAdmin = false,
}) {
  const [autoRefresh, setAutoRefresh] = useState(true)
  const [interval, setInterval_]     = useState(60000)

  const timeStr = lastUpdated
    ? lastUpdated.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
    : '--:--:--'

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-2.5
                    rounded-xl border border-gray-700 bg-gray-800/50">
      {/* Sol: Canlı göstergesi + son güncelleme */}
      <div className="flex items-center gap-3">
        <div className="flex items-center gap-1.5 text-sm text-gray-400">
          <span className="relative flex h-2 w-2">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-eco-green opacity-75" />
            <span className="relative inline-flex rounded-full h-2 w-2 bg-eco-green" />
          </span>
          Canlı
        </div>
        <span className="text-xs text-gray-500">
          Son: <span className="text-gray-400 font-mono">{timeStr}</span>
        </span>
      </div>

      {/* Sağ: Kontroller */}
      <div className="flex items-center gap-3">
        {/* Oto-yenileme toggle */}
        <div className="flex items-center gap-2">
          <span className="text-xs text-gray-500">Oto-yenile</span>
          <button
            onClick={() => setAutoRefresh(v => !v)}
            className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors
              ${autoRefresh ? 'bg-eco-green' : 'bg-gray-600'}`}
          >
            <span className={`inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform
              ${autoRefresh ? 'translate-x-4' : 'translate-x-1'}`} />
          </button>
        </div>

        {/* Aralık seçici */}
        {autoRefresh && (
          <select
            value={interval}
            onChange={e => setInterval_(Number(e.target.value))}
            className="text-xs bg-gray-700 border border-gray-600 rounded px-2 py-1
                       text-gray-300 focus:outline-none focus:border-eco-green/50"
          >
            {INTERVAL_OPTIONS.map(o => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
        )}

        {/* Manuel yenile (herkes görür) veya AI refresh (sadece admin) */}
        <button
          onClick={onRefresh}
          disabled={refreshing}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium
                     border border-gray-600 text-gray-300
                     hover:border-eco-green/50 hover:text-eco-green transition-colors
                     disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <svg
            className={`w-3.5 h-3.5 ${refreshing ? 'animate-spin' : ''}`}
            fill="none" stroke="currentColor" viewBox="0 0 24 24"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          {refreshing ? 'Yenileniyor...' : isAdmin ? 'AI Yenile' : 'Yenile'}
        </button>
      </div>
    </div>
  )
}
