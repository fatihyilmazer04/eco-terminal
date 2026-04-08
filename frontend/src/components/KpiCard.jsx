import React from 'react'

const COLOR_MAP = {
  green:  { border: 'border-l-eco-green',  text: 'text-eco-green',  bg: 'bg-eco-green/10'  },
  red:    { border: 'border-l-red-500',    text: 'text-red-400',    bg: 'bg-red-500/10'    },
  orange: { border: 'border-l-orange-500', text: 'text-orange-400', bg: 'bg-orange-500/10' },
  blue:   { border: 'border-l-blue-500',   text: 'text-blue-400',   bg: 'bg-blue-500/10'   },
  yellow: { border: 'border-l-yellow-500', text: 'text-yellow-400', bg: 'bg-yellow-500/10' },
}

/**
 * KpiCard — sayısal metrik göstergesi.
 * Props:
 *   title     — üst başlık (küçük, gri)
 *   value     — büyük sayı / değer
 *   subtitle  — alt açıklama (küçük, gri)
 *   icon      — emoji string
 *   color     — "green" | "red" | "orange" | "blue" | "yellow"
 */
export default function KpiCard({ title, value, subtitle, icon, color = 'green' }) {
  const c = COLOR_MAP[color] ?? COLOR_MAP.green

  return (
    <div className={`bg-gray-800 rounded-xl p-4 border border-gray-700 border-l-4 ${c.border} flex items-start gap-3`}>
      {/* İkon */}
      <div className={`w-10 h-10 rounded-lg ${c.bg} flex items-center justify-center flex-shrink-0 text-xl`}>
        {icon}
      </div>
      {/* İçerik */}
      <div className="min-w-0">
        <p className="text-gray-400 text-xs font-medium uppercase tracking-wide truncate">{title}</p>
        <p className={`text-2xl font-bold ${c.text} leading-tight mt-0.5`}>{value ?? '—'}</p>
        {subtitle && <p className="text-gray-500 text-xs mt-0.5 truncate">{subtitle}</p>}
      </div>
    </div>
  )
}
