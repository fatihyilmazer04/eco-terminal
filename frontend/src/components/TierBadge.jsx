import React from 'react'

const TIER_CONFIG = {
  GREEN: {
    icon:  '🌿',
    label: 'Green Member',
    cls:   'bg-green-500/20 border-green-500/40 text-green-400',
    glow:  '',
  },
  GOLD: {
    icon:  '⭐',
    label: 'Gold Member',
    cls:   'bg-yellow-500/20 border-yellow-500/40 text-yellow-400',
    glow:  '',
  },
  PLATINUM: {
    icon:  '💎',
    label: 'Platinum Member',
    cls:   'bg-purple-500/20 border-purple-400/50 text-purple-300',
    glow:  'animate-pulse shadow-[0_0_8px_rgba(168,85,247,0.4)]',
  },
}

const SIZE_CLS = {
  sm:  'text-[10px] px-1.5 py-0.5 gap-0.5',
  md:  'text-xs px-2 py-1 gap-1',
  lg:  'text-sm px-3 py-1.5 gap-1.5',
}

export default function TierBadge({ tierLevel = 'GREEN', size = 'md' }) {
  const cfg  = TIER_CONFIG[tierLevel] ?? TIER_CONFIG.GREEN
  const sCls = SIZE_CLS[size] ?? SIZE_CLS.md

  return (
    <span className={`inline-flex items-center rounded-full border font-semibold
                      ${cfg.cls} ${sCls} ${cfg.glow}`}>
      <span>{cfg.icon}</span>
      <span>{cfg.label}</span>
    </span>
  )
}
