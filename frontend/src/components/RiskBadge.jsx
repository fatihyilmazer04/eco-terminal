import React from 'react'

const TREND_ICON = {
  INCREASING: '↑',
  DECREASING: '↓',
  STABLE:     '→',
}

const RISK_CONFIG = {
  HIGH: {
    label:  'YÜKSEK RİSK',
    badge:  'bg-red-500/20 border border-red-500/50 text-red-400',
    pulse:  true,
  },
  MEDIUM: {
    label:  'ORTA RİSK',
    badge:  'bg-orange-500/20 border border-orange-500/50 text-orange-400',
    pulse:  false,
  },
  LOW: {
    label:  'DÜŞÜK RİSK',
    badge:  'bg-green-500/20 border border-green-500/50 text-green-400',
    pulse:  false,
  },
}

/**
 * Risk seviyesi + trend ok işareti gösteren küçük badge.
 * Props: riskLevel ("HIGH"|"MEDIUM"|"LOW"), trend ("INCREASING"|"DECREASING"|"STABLE")
 */
export default function RiskBadge({ riskLevel, trend }) {
  const config = RISK_CONFIG[riskLevel] ?? RISK_CONFIG.LOW
  const trendIcon = TREND_ICON[trend] ?? '→'

  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold ${config.badge}`}>
      {config.pulse && (
        <span className="relative flex h-2 w-2">
          <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-red-400 opacity-75" />
          <span className="relative inline-flex rounded-full h-2 w-2 bg-red-500" />
        </span>
      )}
      {config.label}
      <span className="ml-0.5 font-bold">{trendIcon}</span>
    </span>
  )
}
