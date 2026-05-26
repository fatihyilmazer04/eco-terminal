import React, { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { loyaltyApi } from '../api/loyaltyApi'
import TierBadge from './TierBadge'
import ProgressBar from './ProgressBar'

const TIER_CONFIG = {
  GREEN:    { next: 'Gold',     threshold: 500,  color: 'green'  },
  GOLD:     { next: 'Platinum', threshold: 1500, color: 'gold'   },
  PLATINUM: { next: null,       threshold: null, color: 'purple' },
}

/**
 * Dashboard'da eko-puan özetini gösteren kart.
 * Kendi veri çekimini yapar (bağımsız, hafif).
 * Tıklanınca /passenger/rewards sayfasına gider.
 */
export default function EcoPointsCard() {
  const [wallet, setWallet] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loyaltyApi.getWallet()
      .then(r => setWallet(r.data.data))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  if (loading) {
    return (
      <div className="eco-card animate-pulse">
        <div className="h-4 w-24 bg-gray-700 rounded mb-3" />
        <div className="h-8 w-20 bg-gray-700 rounded mb-3" />
        <div className="h-2 bg-gray-700 rounded-full" />
      </div>
    )
  }

  if (!wallet) return null

  const tier       = wallet.tierLevel ?? 'GREEN'
  const balance    = wallet.currentBalance ?? 0
  const cfg        = TIER_CONFIG[tier] ?? TIER_CONFIG.GREEN
  const progressPct = wallet.progressPct ?? 0
  const toNext     = wallet.pointsToNextTier ?? 0

  return (
    <Link
      to="/passenger/rewards"
      className="block eco-card hover:border-eco-green/40 transition-colors group"
    >
      {/* Başlık */}
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <span className="text-base">🌿</span>
          <span className="text-sm text-gray-400 font-medium">Eko-Puanlarım</span>
        </div>
        <span className="text-eco-green text-xs group-hover:text-green-400 transition-colors">
          Ödüller →
        </span>
      </div>

      {/* Bakiye + Tier */}
      <div className="flex items-end justify-between mb-3">
        <div>
          <p className="text-3xl font-bold text-eco-green tabular-nums">{balance}</p>
          <p className="text-xs text-gray-500 mt-0.5">puan</p>
        </div>
        <TierBadge tierLevel={tier} size="md" />
      </div>

      {/* İlerleme çubuğu */}
      {cfg.next ? (
        <div>
          <div className="flex justify-between text-xs text-gray-500 mb-1.5">
            <span>{cfg.next} Member'a</span>
            <span className="text-eco-green font-medium">{toNext} puan kaldı</span>
          </div>
          <ProgressBar
            value={progressPct}
            color={cfg.color}
            height={6}
            showLabel={false}
            animated
          />
        </div>
      ) : (
        <div className="text-xs text-purple-300 text-center mt-1">
          💎 En yüksek seviyedesiniz!
        </div>
      )}
    </Link>
  )
}
