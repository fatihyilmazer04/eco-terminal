import React from 'react'

const ZONE_ICONS = {
  GATE:     '✈',
  SECURITY: '🔒',
  LOUNGE:   '🛋',
  CHECKIN:  '🎫',
  RETAIL:   '🛍',
  OTHER:    '📍',
}

const LEVEL_LABELS = {
  LOW:      'Düşük',
  MEDIUM:   'Orta',
  HIGH:     'Yüksek',
  CRITICAL: 'Kritik',
}

const LEVEL_BADGE_CLASSES = {
  LOW:      'bg-green-500/20 text-green-400 border-green-500/30',
  MEDIUM:   'bg-yellow-500/20 text-yellow-400 border-yellow-500/30',
  HIGH:     'bg-orange-500/20 text-orange-400 border-orange-500/30',
  CRITICAL: 'bg-red-500/20 text-red-400 border-red-500/30',
}

/**
 * Tek bölge yoğunluk kartı.
 * Props: ZoneOccupancyResponse alanları
 */
export default function OccupancyCard({
  zoneName,
  type,
  currentCount,
  maxCapacity,
  densityPct,
  densityLevel,
  colorCode,
  criticalThreshold,
  lastUpdated,
  compact = false,
}) {
  const pct     = densityPct ?? 0
  const barPct  = Math.min(Math.round(pct * 100), 100)
  const icon    = ZONE_ICONS[type] ?? '📍'
  const label   = LEVEL_LABELS[densityLevel] ?? densityLevel
  const badge   = LEVEL_BADGE_CLASSES[densityLevel] ?? LEVEL_BADGE_CLASSES.LOW
  const isCrit  = densityLevel === 'CRITICAL' || densityLevel === 'HIGH'

  return (
    <div className={`
      eco-card relative overflow-hidden transition-all duration-300
      ${isCrit ? 'border-orange-500/40 shadow-orange-500/10' : ''}
      ${compact ? 'p-4' : 'p-6'}
    `}>
      {/* Pulsing indicator — kritik bölge için */}
      {isCrit && (
        <span className="absolute top-3 right-3 flex h-3 w-3">
          <span className="animate-ping absolute inline-flex h-full w-full rounded-full opacity-75"
                style={{ backgroundColor: colorCode }} />
          <span className="relative inline-flex rounded-full h-3 w-3"
                style={{ backgroundColor: colorCode }} />
        </span>
      )}

      {/* Başlık */}
      <div className="flex items-start justify-between mb-4">
        <div className="flex items-center gap-2">
          <span className="text-xl">{icon}</span>
          <div>
            <h3 className={`font-semibold text-white ${compact ? 'text-sm' : 'text-base'}`}>
              {zoneName}
            </h3>
            <p className="text-xs text-gray-400 capitalize">{type?.toLowerCase()}</p>
          </div>
        </div>

        {/* Density Badge */}
        <span className={`
          text-xs font-medium px-2 py-0.5 rounded-full border
          ${badge} ${isCrit ? 'animate-pulse-slow' : ''}
        `}>
          {label}
        </span>
      </div>

      {/* Kişi sayısı */}
      <div className="flex items-end justify-between mb-3">
        <div>
          <span className="text-2xl font-bold text-white">{currentCount ?? 0}</span>
          <span className="text-gray-400 text-sm ml-1">/ {maxCapacity} kişi</span>
        </div>
        <span className="text-lg font-semibold" style={{ color: colorCode }}>
          %{barPct}
        </span>
      </div>

      {/* Progress Bar */}
      <div className="w-full h-2 bg-gray-700 rounded-full overflow-hidden">
        <div
          className="h-full rounded-full transition-all duration-700 ease-out"
          style={{ width: `${barPct}%`, backgroundColor: colorCode }}
        />
      </div>

      {/* Kritik eşik çizgisi */}
      {criticalThreshold && (
        <div className="relative mt-1">
          <div
            className="absolute h-3 w-0.5 bg-gray-500 -top-3 rounded"
            style={{ left: `${Math.round(criticalThreshold * 100)}%` }}
            title={`Kritik eşik: %${Math.round(criticalThreshold * 100)}`}
          />
          <p className="text-xs text-gray-500 text-right">
            eşik %{Math.round((criticalThreshold ?? 0) * 100)}
          </p>
        </div>
      )}

      {/* Son güncelleme */}
      {lastUpdated && !compact && (
        <p className="text-xs text-gray-600 mt-2">
          {new Date(lastUpdated).toLocaleTimeString('tr-TR')}
        </p>
      )}
    </div>
  )
}
