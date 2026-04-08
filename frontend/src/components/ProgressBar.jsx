import React from 'react'

const COLOR_MAP = {
  green:  'bg-eco-green',
  gold:   'bg-yellow-400',
  red:    'bg-red-500',
  orange: 'bg-orange-400',
  blue:   'bg-blue-500',
  purple: 'bg-purple-500',
}

/**
 * Genel kullanım ilerleme çubuğu.
 * Props:
 *   value     — 0–100
 *   color     — 'green' | 'gold' | 'red' | 'orange' | 'blue' | 'purple'
 *   height    — px sayısı (default 8)
 *   showLabel — sağda "% değer" yazar
 *   animated  — gradient kayma efekti
 */
export default function ProgressBar({
  value = 0,
  color = 'green',
  height = 8,
  showLabel = false,
  animated = false,
}) {
  const clamped = Math.min(100, Math.max(0, value))
  const barColor = COLOR_MAP[color] ?? 'bg-eco-green'

  return (
    <div className="flex items-center gap-2">
      <div
        className="flex-1 bg-gray-700 rounded-full overflow-hidden"
        style={{ height: `${height}px` }}
      >
        <div
          className={`h-full rounded-full transition-all duration-500 ease-out ${barColor}
                      ${animated ? 'relative overflow-hidden' : ''}`}
          style={{ width: `${clamped}%` }}
        >
          {animated && (
            <span className="absolute inset-0 bg-gradient-to-r from-transparent via-white/20
                             to-transparent animate-[shimmer_1.5s_infinite]" />
          )}
        </div>
      </div>
      {showLabel && (
        <span className="text-xs text-gray-400 w-9 text-right flex-shrink-0">
          %{clamped.toFixed(0)}
        </span>
      )}
    </div>
  )
}
