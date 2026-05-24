import React from 'react'

const CARDS = [
  { key: 'fullCount',     label: 'Dolu',   color: 'red',     icon: '🔴', bg: 'bg-red-500/10',    border: 'border-red-500/30',    text: 'text-red-400'    },
  { key: 'busyCount',     label: 'Yoğun',  color: 'amber',   icon: '🟡', bg: 'bg-amber-500/10',  border: 'border-amber-500/30',  text: 'text-amber-400'  },
  { key: 'moderateCount', label: 'Normal', color: 'blue',    icon: '🔵', bg: 'bg-blue-500/10',   border: 'border-blue-500/30',   text: 'text-blue-400'   },
  { key: 'emptyCount',    label: 'Boş',    color: 'emerald', icon: '🟢', bg: 'bg-emerald-500/10',border: 'border-emerald-500/30',text: 'text-emerald-400'},
]

/**
 * 4 özet kart: Dolu / Yoğun / Normal / Boş zone sayıları.
 */
export default function HeatmapSummaryCards({ data }) {
  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
      {CARDS.map(card => (
        <div
          key={card.key}
          className={`rounded-xl p-4 border ${card.bg} ${card.border} flex items-center gap-3`}
        >
          <span className="text-2xl">{card.icon}</span>
          <div>
            <p className={`text-2xl font-bold ${card.text}`}>
              {data?.[card.key] ?? 0}
            </p>
            <p className="text-xs text-gray-400">{card.label} Zone</p>
          </div>
        </div>
      ))}
    </div>
  )
}
