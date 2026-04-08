import React from 'react'
import { useOccupancy } from '../../hooks/useOccupancy'
import OccupancyCard from '../../components/OccupancyCard'

function SkeletonCard() {
  return (
    <div className="eco-card animate-pulse">
      <div className="flex items-start justify-between mb-4">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 bg-gray-700 rounded-lg" />
          <div>
            <div className="h-4 w-24 bg-gray-700 rounded mb-1" />
            <div className="h-3 w-16 bg-gray-700 rounded" />
          </div>
        </div>
        <div className="h-5 w-16 bg-gray-700 rounded-full" />
      </div>
      <div className="flex items-end justify-between mb-3">
        <div className="h-8 w-20 bg-gray-700 rounded" />
        <div className="h-6 w-10 bg-gray-700 rounded" />
      </div>
      <div className="h-2 bg-gray-700 rounded-full" />
    </div>
  )
}

export default function HeatmapPage() {
  const { data, loading, error, lastUpdated, refetch } = useOccupancy(15000)

  const zones = data?.zones ?? []
  const criticalZones = zones.filter(
    z => z.densityLevel === 'CRITICAL' || z.densityLevel === 'HIGH'
  )

  return (
    <div className="min-h-screen bg-gray-900 p-6">
      {/* Başlık */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-white">Terminal Yoğunluk Haritası</h1>
          <p className="text-gray-400 text-sm mt-0.5">Her 15 saniyede bir güncelleniyor</p>
        </div>

        <div className="flex items-center gap-3">
          {/* Canlı göstergesi */}
          <div className="flex items-center gap-1.5 text-sm text-gray-400">
            <span className="relative flex h-2 w-2">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-eco-green opacity-75" />
              <span className="relative inline-flex rounded-full h-2 w-2 bg-eco-green" />
            </span>
            Canlı
          </div>

          {/* Son güncelleme */}
          {lastUpdated && (
            <span className="text-xs text-gray-500 bg-gray-800 px-2 py-1 rounded">
              {lastUpdated.toLocaleTimeString('tr-TR')}
            </span>
          )}

          {/* Manuel yenile */}
          <button
            onClick={refetch}
            className="text-gray-400 hover:text-white transition-colors p-1.5 rounded hover:bg-gray-700"
            title="Yenile"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>
        </div>
      </div>

      {/* Kritik uyarı banner */}
      {criticalZones.length > 0 && !loading && (
        <div className="mb-6 px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/30 flex items-start gap-3">
          <svg className="w-5 h-5 text-red-400 flex-shrink-0 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
          </svg>
          <div>
            <p className="text-red-400 font-medium text-sm">
              {criticalZones.length > 1
                ? `${criticalZones.length} bölge yüksek yoğunlukta!`
                : `${criticalZones[0].zoneName} yüksek yoğunlukta!`}
            </p>
            <p className="text-red-400/70 text-xs mt-0.5">
              {criticalZones.map(z => z.zoneName).join(', ')} — alternatif bölgeleri tercih edin.
            </p>
          </div>
        </div>
      )}

      {/* Hata durumu */}
      {error && !loading && (
        <div className="mb-6 px-4 py-3 rounded-xl bg-yellow-500/10 border border-yellow-500/30 flex items-center gap-3">
          <svg className="w-5 h-5 text-yellow-400" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
          </svg>
          <p className="text-yellow-400 text-sm">
            Veri alınamadı, yeniden deneniyor...{' '}
            <button onClick={refetch} className="underline ml-1">Şimdi dene</button>
          </p>
        </div>
      )}

      {/* 2x2 Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {loading
          ? Array.from({ length: 4 }).map((_, i) => <SkeletonCard key={i} />)
          : zones.map(zone => (
              <OccupancyCard
                key={zone.zoneId}
                zoneName={zone.zoneName}
                type={zone.type}
                currentCount={zone.currentCount}
                maxCapacity={zone.maxCapacity}
                densityPct={zone.densityPct}
                densityLevel={zone.densityLevel}
                colorCode={zone.colorCode}
                criticalThreshold={zone.criticalThreshold}
                lastUpdated={zone.lastUpdated}
              />
            ))
        }
      </div>

      {/* Renk açıklaması */}
      {!loading && (
        <div className="mt-6 flex flex-wrap gap-4 justify-center">
          {[
            { color: '#2ECC71', label: 'Düşük (<%60)' },
            { color: '#F39C12', label: 'Orta (%60–84)' },
            { color: '#E67E22', label: 'Yüksek (%85–94)' },
            { color: '#E74C3C', label: 'Kritik (≥%95)' },
          ].map(item => (
            <div key={item.label} className="flex items-center gap-1.5 text-xs text-gray-400">
              <span className="w-3 h-3 rounded-full" style={{ backgroundColor: item.color }} />
              {item.label}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
