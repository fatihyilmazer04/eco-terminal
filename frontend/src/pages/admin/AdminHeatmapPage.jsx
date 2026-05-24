import React, { useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { useHeatmap } from '../../hooks/useHeatmap'
import AirportHeatmap from '../../components/AirportHeatmap'
import HeatmapSummaryCards from '../../components/HeatmapSummaryCards'
import HeatmapControls from '../../components/HeatmapControls'
import ZoneDetailPanel from '../../components/ZoneDetailPanel'
import AIInsightBox from '../../components/AIInsightBox'
import LoadingSkeleton from '../../components/LoadingSkeleton'

/**
 * /admin/heatmap — Tam ekran canlı terminal heatmap sayfası (admin).
 * AI yenileme butonu + zone detay paneli + AI özet kutusu içerir.
 */
export default function AdminHeatmapPage() {
  const { data, loading, error, refresh, lastUpdated, refreshing } = useHeatmap(60000)
  const [selectedZoneId, setSelectedZoneId] = useState(null)
  const { user } = useAuth()

  const selectedZone = data?.zones?.find(z => z.zoneId === selectedZoneId) ?? null

  const handleZoneClick = (zoneId) => {
    setSelectedZoneId(prev => prev === zoneId ? null : zoneId)
  }

  return (
    <div className="flex-1 p-5 space-y-4 overflow-auto">
      {/* Başlık */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-white">Canlı Terminal Haritası</h1>
          <p className="text-gray-400 text-sm">Gerçek zamanlı zone yoğunluğu — İstanbul Havalimanı</p>
        </div>
      </div>

      {/* Hata */}
      {error && !loading && (
        <div className="px-4 py-2 rounded-lg bg-yellow-500/10 border border-yellow-500/30 text-yellow-400 text-sm">
          {error}
        </div>
      )}

      {/* Özet kartlar */}
      {loading ? <LoadingSkeleton variant="card" count={4} /> : (
        <HeatmapSummaryCards data={data} />
      )}

      {/* Kontrol çubuğu */}
      <HeatmapControls
        lastUpdated={lastUpdated}
        onRefresh={refresh}
        refreshing={refreshing}
        isAdmin={user?.role === 'ADMIN'}
      />

      {/* Ana içerik: harita + detay paneli yan yana */}
      <div className="grid grid-cols-1 xl:grid-cols-4 gap-4">
        {/* Harita (3/4 genişlik) */}
        <div className="xl:col-span-3">
          {loading ? (
            <LoadingSkeleton variant="heatmap" />
          ) : (
            <AirportHeatmap
              zones={data?.zones ?? []}
              onZoneClick={handleZoneClick}
              selectedZoneId={selectedZoneId}
            />
          )}
        </div>

        {/* Detay paneli (1/4 genişlik) */}
        <div className="xl:col-span-1">
          {selectedZone ? (
            <ZoneDetailPanel
              zone={selectedZone}
              onClose={() => setSelectedZoneId(null)}
            />
          ) : (
            <div className="rounded-xl border border-gray-700 bg-gray-800/50 p-6 flex flex-col items-center justify-center h-full min-h-48">
              <div className="w-10 h-10 rounded-lg bg-gray-700 flex items-center justify-center mb-3">
                <span className="text-lg">🗺️</span>
              </div>
              <p className="text-gray-500 text-sm text-center">
                Detayları görmek için haritadan bir bölgeye tıklayın
              </p>
            </div>
          )}
        </div>
      </div>

      {/* AI Özet */}
      {!loading && (
        <AIInsightBox
          summary={data?.aiSummary}
          alertZones={data?.alertZones}
          suggestedZones={data?.suggestedZones}
        />
      )}
    </div>
  )
}
