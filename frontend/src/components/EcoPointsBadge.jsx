import React, { useEffect, useRef, useState } from 'react'
import TierBadge from './TierBadge'

/**
 * Navbar veya profil'de gösterilen Eko-Puan göstergesi.
 * Props: balance (int), tierLevel ('GREEN'|'GOLD'|'PLATINUM')
 * Puan artınca 0'dan hedef değere kısa sayaç animasyonu çalışır.
 */
export default function EcoPointsBadge({ balance = 0, tierLevel = 'GREEN' }) {
  const [displayed, setDisplayed] = useState(balance)
  const prevBalance = useRef(balance)

  useEffect(() => {
    if (balance === prevBalance.current) return
    const start  = prevBalance.current
    const end    = balance
    const diff   = end - start
    const steps  = 20
    const delay  = 600 / steps   // ~600ms toplam
    let step = 0

    const id = setInterval(() => {
      step++
      setDisplayed(Math.round(start + (diff * step) / steps))
      if (step >= steps) {
        clearInterval(id)
        setDisplayed(end)
        prevBalance.current = end
      }
    }, delay)

    return () => clearInterval(id)
  }, [balance])

  return (
    <div className="flex items-center gap-1.5">
      <span className="text-eco-green text-sm">🌿</span>
      <span className="text-white font-semibold text-sm tabular-nums">{displayed}</span>
      <TierBadge tierLevel={tierLevel} size="sm" />
    </div>
  )
}
