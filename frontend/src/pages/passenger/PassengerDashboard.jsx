import React from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { useOccupancy } from '../../hooks/useOccupancy'
import OccupancyCard from '../../components/OccupancyCard'
import EcoPointsCard from '../../components/EcoPointsCard'
import { useHeatmap } from '../../hooks/useHeatmap'

function StatCard({ label, value, sub, color }) {
  return (
    <div className="eco-card">
      <p className="text-sm text-gray-400 mb-1">{label}</p>
      <p className="text-3xl font-bold" style={{ color: color ?? 'white' }}>{value}</p>
      {sub && <p className="text-xs text-gray-500 mt-1">{sub}</p>}
    </div>
  )
}

export default function PassengerDashboard() {
  const { user } = useAuth()
  const { data, loading, error } = useOccupancy(15000)
  const { data: heatmapData } = useHeatmap(60000)

  const zones = data?.zones ?? []
  const criticalZones  = zones.filter(z => z.densityLevel === 'CRITICAL' || z.densityLevel === 'HIGH')
  const totalPeople    = data?.totalPeople ?? 0
  const avgDensity     = zones.length > 0
    ? Math.round((zones.reduce((s, z) => s + (z.densityPct ?? 0), 0) / zones.length) * 100)
    : 0

  return (
    <div className="min-h-screen bg-gray-900 p-6">
      {/* Karşılama */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-white">
          Merhaba, {user?.fullName?.split(' ')[0] ?? 'Yolcu'} 👋
        </h1>
        <p className="text-gray-400 text-sm mt-0.5">
          Terminal durumu gerçek zamanlı güncelleniyor
        </p>
      </div>

      {/* API hata banner */}
      {error && !loading && (
        <div className="mb-6 px-4 py-3 rounded-xl bg-yellow-500/10 border border-yellow-500/30 flex items-center gap-3">
          <svg className="w-5 h-5 text-yellow-400 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
          </svg>
          <p className="text-yellow-400 text-sm">{error} — Veriler alınamıyor, yeniden deneniyor...</p>
        </div>
      )}

      {/* Kritik uyarı banner */}
      {criticalZones.length > 0 && !loading && (
        <div className="mb-6 px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/30 flex items-start gap-3">
          <span className="text-red-400 text-xl flex-shrink-0">⚠</span>
          <div className="flex-1">
            <p className="text-red-400 font-medium text-sm">
              {criticalZones.map(z => z.zoneName).join(', ')} kritik yoğunlukta!
              Alternatif bölgelere yönlendiriliyorsunuz.
            </p>
          </div>
          <Link
            to="/passenger/route"
            className="text-xs text-red-400 underline whitespace-nowrap"
          >
            Rota öner
          </Link>
        </div>
      )}

      {/* Eko-Puan Kartı */}
      <div className="mb-6">
        <EcoPointsCard />
      </div>

      {/* Özet Kartlar */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
        {loading ? (
          Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="eco-card animate-pulse">
              <div className="h-4 w-24 bg-gray-700 rounded mb-2" />
              <div className="h-8 w-16 bg-gray-700 rounded" />
            </div>
          ))
        ) : (
          <>
            <StatCard
              label="Toplam Yolcu"
              value={totalPeople.toLocaleString('tr-TR')}
              sub="terminaldeki anlık kişi sayısı"
            />
            <StatCard
              label="Kritik Bölge"
              value={criticalZones.length}
              sub={criticalZones.length > 0 ? criticalZones.map(z => z.zoneName).join(', ') : 'Tüm bölgeler normal'}
              color={criticalZones.length > 0 ? '#E74C3C' : '#2ECC71'}
            />
            <StatCard
              label="Ortalama Doluluk"
              value={`%${avgDensity}`}
              sub={avgDensity < 60 ? 'Rahat seviye' : avgDensity < 85 ? 'Orta yoğunluk' : 'Yüksek yoğunluk'}
              color={avgDensity >= 85 ? '#E74C3C' : avgDensity >= 60 ? '#F39C12' : '#2ECC71'}
            />
          </>
        )}
      </div>

      {/* Mini terminal yoğunluk durumu kartı */}
      {heatmapData && (
        <Link to="/passenger/heatmap" className="block mb-4">
          <div className="rounded-xl border border-gray-700 bg-gray-800 p-4 hover:border-eco-green/40 transition-colors">
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-2">
                <span className="text-sm">🗺️</span>
                <span className="text-white text-sm font-medium">Terminal Yoğunluk Durumu</span>
              </div>
              <span className="text-eco-green text-xs">Haritayı gör →</span>
            </div>
            <div className="flex gap-3 text-xs">
              {heatmapData.fullCount > 0 && (
                <span className="text-red-400">🔴 {heatmapData.fullCount} dolu</span>
              )}
              {heatmapData.busyCount > 0 && (
                <span className="text-amber-400">🟡 {heatmapData.busyCount} yoğun</span>
              )}
              <span className="text-emerald-400">🟢 {heatmapData.emptyCount} boş</span>
              <span className="text-gray-500">· {heatmapData.totalZones} bölge</span>
            </div>
            {heatmapData.alertZones?.length > 0 && (
              <p className="text-red-400/80 text-xs mt-1.5">
                ⚠ {heatmapData.alertZones.join(', ')} dolu — alternatif rota için tıklayın
              </p>
            )}
          </div>
        </Link>
      )}

      {/* Bölge Kartları */}
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-semibold text-white">Bölge Durumu</h2>
        <Link
          to="/passenger/heatmap"
          className="text-sm text-eco-green hover:text-green-400 transition-colors"
        >
          Haritayı Gör →
        </Link>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {loading
          ? Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="eco-card animate-pulse">
                <div className="h-4 w-32 bg-gray-700 rounded mb-3" />
                <div className="h-6 w-20 bg-gray-700 rounded mb-3" />
                <div className="h-2 bg-gray-700 rounded-full" />
              </div>
            ))
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
                compact
              />
            ))
        }
      </div>
    </div>
  )
}
