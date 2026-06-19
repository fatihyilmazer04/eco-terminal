import React from 'react'

/**
 * Heatmap kontrol çubuğu — manuel refresh.
 *
 * @param {Function} onRefresh   - Manuel yenileme butonu callback'i
 * @param {boolean}  refreshing  - Yenileme devam ediyor mu
 */
export default function HeatmapControls({
  onRefresh,
  refreshing = false,
}) {

  return (
    <div className="flex flex-wrap items-center justify-end gap-3 px-4 py-2.5
                    rounded-xl border border-gray-700 bg-gray-800/50">
      {/* Manuel yenile */}
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
        {refreshing ? 'Yenileniyor...' : 'Yenile'}
      </button>
    </div>
  )
}
