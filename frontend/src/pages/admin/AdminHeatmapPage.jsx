import React, { useState, useEffect, useCallback, useRef } from 'react'
import { useAuth } from '../../context/AuthContext'
import { useHeatmap } from '../../hooks/useHeatmap'
import AirportHeatmap from '../../components/AirportHeatmap'
import HeatmapSummaryCards from '../../components/HeatmapSummaryCards'
import HeatmapControls from '../../components/HeatmapControls'
import ZoneDetailPanel from '../../components/ZoneDetailPanel'
import AIInsightBox from '../../components/AIInsightBox'
import LoadingSkeleton from '../../components/LoadingSkeleton'
import ImageAnalysisPanel from '../../components/ImageAnalysisPanel'
import { getCrowdStatus, getAICrowdAnalysis } from '../../api/crowd'

// ── YOLOv8 bölümü sabitleri ─────────────────────────────────────────────────
const CROWD_REFRESH = 30_000

const ZONE_EMOJIS = {
  GATE: '🚪', LOUNGE: '🛋️', CHECKIN: '✅',
  SECURITY: '🔒', RETAIL: '🛍️', OTHER: '📍',
}

const STATUS_STYLES = {
  FULL:     { bg: 'bg-red-500/15',    border: 'border-red-500/40',    badge: 'bg-red-500',    text: 'Dolu'    },
  BUSY:     { bg: 'bg-yellow-400/10', border: 'border-yellow-400/40', badge: 'bg-yellow-400', text: 'Yoğun'  },
  MODERATE: { bg: 'bg-blue-500/10',   border: 'border-blue-500/40',   badge: 'bg-blue-500',   text: 'Normal' },
  EMPTY:    { bg: 'bg-green-500/10',  border: 'border-green-500/40',  badge: 'bg-green-500',  text: 'Boş'    },
}

const TREND_ICONS  = { INCREASING: '↑', DECREASING: '↓', STABLE: '→', rising: '↑', falling: '↓', stable: '→' }
const TREND_COLORS = { INCREASING: 'text-red-400', DECREASING: 'text-green-400', STABLE: 'text-gray-400', rising: 'text-red-400', falling: 'text-green-400', stable: 'text-gray-400' }

// ── YOLOv8 alt bileşenler ────────────────────────────────────────────────────
function CrowdSkeletonCard() {
  return (
    <div className="rounded-xl border border-gray-700 bg-gray-800/50 p-4 animate-pulse">
      <div className="flex items-center gap-2 mb-3">
        <div className="w-8 h-8 rounded-lg bg-gray-700" />
        <div className="flex-1">
          <div className="h-4 bg-gray-700 rounded w-24 mb-1" />
          <div className="h-3 bg-gray-700 rounded w-16" />
        </div>
        <div className="h-5 w-16 bg-gray-700 rounded-full" />
      </div>
      <div className="h-2 bg-gray-700 rounded-full mb-2" />
      <div className="flex justify-between">
        <div className="h-3 w-20 bg-gray-700 rounded" />
        <div className="h-3 w-16 bg-gray-700 rounded" />
      </div>
    </div>
  )
}

function ZoneCrowdCard({ zone }) {
  const style = STATUS_STYLES[zone.status] ?? STATUS_STYLES.EMPTY
  const emoji = ZONE_EMOJIS[zone.zoneType] ?? '📍'
  const trendIcon  = TREND_ICONS[zone.trend]  ?? '→'
  const trendColor = TREND_COLORS[zone.trend] ?? 'text-gray-400'
  const fillPct = Math.round((zone.currentDensity ?? 0) * 100)
  const barColor =
    zone.status === 'FULL'     ? 'bg-red-500'    :
    zone.status === 'BUSY'     ? 'bg-yellow-400' :
    zone.status === 'MODERATE' ? 'bg-blue-500'   : 'bg-green-500'

  return (
    <div className={`rounded-xl border p-4 transition-all hover:scale-[1.01] ${style.bg} ${style.border}`}>
      <div className="flex items-center gap-2 mb-3">
        <div className="w-9 h-9 rounded-lg bg-gray-800/70 flex items-center justify-center text-lg flex-shrink-0">
          {emoji}
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-white text-sm font-semibold truncate">{zone.zoneName}</p>
          <p className="text-gray-400 text-xs">{zone.zoneType}</p>
        </div>
        <span className={`${style.badge} text-white text-xs font-bold px-2.5 py-0.5 rounded-full flex-shrink-0`}>
          {style.text}
        </span>
      </div>

      <div className="mb-2">
        <div className="w-full h-2 bg-gray-700 rounded-full overflow-hidden">
          <div className={`h-full ${barColor} rounded-full transition-all duration-500`} style={{ width: `${fillPct}%` }} />
        </div>
      </div>

      <div className="flex items-center justify-between text-xs text-gray-400">
        <span>
          <span className="text-white font-medium">{zone.peopleCount ?? 0}</span>
          {zone.capacity ? ` / ${zone.capacity} kişi` : ' kişi'}
        </span>
        <div className="flex items-center gap-2">
          <span className={`font-bold text-sm ${trendColor}`}>{trendIcon}</span>
          {zone.predictedLoad != null && (
            <span>%{Math.round(zone.predictedLoad * 100)} tahmini</span>
          )}
        </div>
      </div>
    </div>
  )
}

function CrowdSummaryBar({ zones }) {
  const counts = { FULL: 0, BUSY: 0, MODERATE: 0, EMPTY: 0 }
  zones.forEach(z => { if (counts[z.status] !== undefined) counts[z.status]++ })
  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-4">
      {[
        { label: 'Dolu',   key: 'FULL',     color: 'text-red-400',    border: 'border-red-500/40'    },
        { label: 'Yoğun',  key: 'BUSY',     color: 'text-yellow-400', border: 'border-yellow-500/40' },
        { label: 'Normal', key: 'MODERATE', color: 'text-blue-400',   border: 'border-blue-500/40'   },
        { label: 'Boş',    key: 'EMPTY',    color: 'text-green-400',  border: 'border-green-500/40'  },
      ].map(item => (
        <div key={item.key} className={`rounded-xl border ${item.border} bg-gray-800/40 p-3 text-center`}>
          <p className={`text-2xl font-bold ${item.color}`}>{counts[item.key]}</p>
          <p className="text-gray-400 text-xs mt-0.5">{item.label}</p>
        </div>
      ))}
    </div>
  )
}

function CrowdAIBox({ analysis, loading }) {
  if (loading) {
    return (
      <div className="rounded-xl border border-eco-green/30 bg-eco-green/5 p-4 animate-pulse mb-4">
        <div className="h-4 bg-gray-700 rounded w-40 mb-3" />
        <div className="h-3 bg-gray-700 rounded w-full mb-2" />
        <div className="h-3 bg-gray-700 rounded w-3/4" />
      </div>
    )
  }
  if (!analysis) return null
  return (
    <div className="rounded-xl border border-eco-green/30 bg-eco-green/5 p-4 mb-4">
      <div className="flex items-center gap-2 mb-3">
        <span className="text-eco-green">🤖</span>
        <h3 className="text-eco-green font-semibold text-sm">AI Kalabalık Analizi</h3>
        <span className="text-gray-500 text-xs ml-auto">{analysis.timestamp?.slice(11, 16)}</span>
      </div>
      <p className="text-gray-200 text-sm mb-3">{analysis.summary}</p>
      {analysis.alert_zones?.length > 0 && (
        <div className="flex flex-wrap gap-1.5 mb-2">
          <span className="text-red-400 text-xs font-medium mr-1">Dolu:</span>
          {analysis.alert_zones.map(z => (
            <span key={z} className="bg-red-500/20 border border-red-500/40 text-red-300 text-xs px-2 py-0.5 rounded-full">{z}</span>
          ))}
        </div>
      )}
      {analysis.empty_zones?.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          <span className="text-green-400 text-xs font-medium mr-1">Boş:</span>
          {analysis.empty_zones.map(z => (
            <span key={z} className="bg-green-500/20 border border-green-500/40 text-green-300 text-xs px-2 py-0.5 rounded-full">{z}</span>
          ))}
        </div>
      )}
    </div>
  )
}

// ── Ana Sayfa ─────────────────────────────────────────────────────────────────
/**
 * /admin/heatmap — Canlı terminal heatmap + YOLOv8 kalabalık tespiti
 */
export default function AdminHeatmapPage() {
  // Heatmap
  const { data, loading, error, refresh, refetch: refetchHeatmap, lastUpdated, refreshing } = useHeatmap(15000)
  const [selectedZoneId, setSelectedZoneId] = useState(null)
  const { user } = useAuth()
  const selectedZone = data?.zones?.find(z => z.zoneId === selectedZoneId) ?? null

  // YOLOv8 Kalabalık Tespiti
  const [crowdZones,    setCrowdZones]    = useState([])
  const [crowdAnalysis, setCrowdAnalysis] = useState(null)
  const [crowdLoading,  setCrowdLoading]  = useState(true)
  const [crowdAiLoad,   setCrowdAiLoad]   = useState(true)
  const [crowdError,    setCrowdError]    = useState(null)
  const [crowdFilter,   setCrowdFilter]   = useState('ALL')
  const [crowdRefresh,  setCrowdRefresh]  = useState(null)
  const isMounted = useRef(true)

  const fetchCrowd = useCallback(async () => {
    try {
      const data = await getCrowdStatus()
      if (!isMounted.current) return
      setCrowdZones(Array.isArray(data) ? data : [])
      setCrowdRefresh(new Date())
      setCrowdError(null)
    } catch {
      if (isMounted.current) setCrowdError('Kalabalık verileri yüklenemedi.')
    } finally {
      if (isMounted.current) setCrowdLoading(false)
    }
  }, [])

  const fetchCrowdAI = useCallback(async () => {
    try {
      if (isMounted.current) setCrowdAiLoad(true)
      const res = await getAICrowdAnalysis()
      if (isMounted.current) setCrowdAnalysis(res)
    } catch {
      // AI servisi erişilemez — sessizce geç
    } finally {
      if (isMounted.current) setCrowdAiLoad(false)
    }
  }, [])

  // Görüntü analizi tamamlandığında heatmap + kalabalık verilerini anında güncelle
  const handleAnalysisComplete = useCallback(() => {
    refetchHeatmap()
    fetchCrowd()
  }, [refetchHeatmap, fetchCrowd])

  useEffect(() => {
    isMounted.current = true
    fetchCrowd()
    fetchCrowdAI()
    const interval = setInterval(() => { fetchCrowd(); fetchCrowdAI() }, CROWD_REFRESH)
    return () => { isMounted.current = false; clearInterval(interval) }
  }, [fetchCrowd, fetchCrowdAI])

  const filteredZones = crowdFilter === 'ALL'
    ? crowdZones
    : crowdZones.filter(z => z.status === crowdFilter)

  return (
    <div className="flex-1 p-5 space-y-4 overflow-auto">
      {/* ── Başlık ── */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-white">Canlı Terminal Haritası</h1>
          <p className="text-gray-400 text-sm">Gerçek zamanlı zone yoğunluğu — İstanbul Havalimanı</p>
        </div>
      </div>

      {/* ── Görüntü Analizi Paneli ── */}
      <ImageAnalysisPanel onAnalysisComplete={handleAnalysisComplete} />

      {/* Hata */}
      {error && !loading && (
        <div className="px-4 py-2 rounded-lg bg-yellow-500/10 border border-yellow-500/30 text-yellow-400 text-sm">
          {error}
        </div>
      )}

      {/* Özet kartlar */}
      {loading ? <LoadingSkeleton variant="card" count={4} /> : <HeatmapSummaryCards data={data} />}

      {/* Kontrol çubuğu */}
      <HeatmapControls
        lastUpdated={lastUpdated}
        onRefresh={refresh}
        refreshing={refreshing}
        isAdmin={user?.role === 'ADMIN'}
      />

      {/* Ana içerik: harita + detay paneli */}
      <div className="grid grid-cols-1 xl:grid-cols-4 gap-4">
        <div className="xl:col-span-3">
          {loading ? (
            <LoadingSkeleton variant="heatmap" />
          ) : (
            <AirportHeatmap
              zones={data?.zones ?? []}
              onZoneClick={(zoneId) => setSelectedZoneId(prev => prev === zoneId ? null : zoneId)}
              selectedZoneId={selectedZoneId}
            />
          )}
        </div>

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

      {/* ── YOLOv8 Kalabalık Tespiti ─────────────────────────────────────── */}
      <div className="border-t border-gray-800 pt-6">
        {/* Bölüm başlığı */}
        <div className="flex items-center justify-between mb-5">
          <div>
            <h2 className="text-lg font-bold text-white flex items-center gap-2">
              <span className="w-7 h-7 rounded-lg bg-eco-green/10 border border-eco-green/30
                               flex items-center justify-center text-sm">👁️</span>
              YOLOv8 Kalabalık Tespiti
            </h2>
            <p className="text-gray-400 text-sm mt-0.5">
              YOLOv8 + Sensör verisi — gerçek zamanlı
              {crowdRefresh && (
                <span className="ml-2 text-gray-500">
                  • Son güncelleme: {crowdRefresh.toLocaleTimeString('tr-TR')}
                </span>
              )}
            </p>
          </div>
          <button
            onClick={() => { fetchCrowd(); fetchCrowdAI() }}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-eco-green/10
                       border border-eco-green/30 text-eco-green text-sm hover:bg-eco-green/20 transition"
          >
            ↻ Yenile
          </button>
        </div>

        {/* Hata */}
        {crowdError && (
          <div className="rounded-xl border border-red-500/30 bg-red-500/10 text-red-300 p-3 mb-4 text-sm">
            {crowdError}
          </div>
        )}

        {/* Özet sayaçlar */}
        {!crowdLoading && <CrowdSummaryBar zones={crowdZones} />}

        {/* AI Analiz */}
        <CrowdAIBox analysis={crowdAnalysis} loading={crowdAiLoad} />

        {/* Filtre butonları */}
        <div className="flex gap-2 mb-4 flex-wrap">
          {['ALL', 'FULL', 'BUSY', 'MODERATE', 'EMPTY'].map(f => (
            <button
              key={f}
              onClick={() => setCrowdFilter(f)}
              className={`px-3 py-1 rounded-lg text-xs font-medium border transition
                ${crowdFilter === f
                  ? 'bg-eco-green text-gray-900 border-eco-green'
                  : 'border-gray-700 text-gray-400 hover:border-gray-500 hover:text-gray-200'}`}
            >
              {f === 'ALL'      ? `Tümü (${crowdZones.length})` :
               f === 'FULL'     ? '🔴 Dolu'   :
               f === 'BUSY'     ? '🟡 Yoğun'  :
               f === 'MODERATE' ? '🔵 Normal' : '🟢 Boş'}
            </button>
          ))}
        </div>

        {/* Zone kartları */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
          {crowdLoading
            ? Array.from({ length: 8 }).map((_, i) => <CrowdSkeletonCard key={i} />)
            : filteredZones.length === 0
              ? (
                <div className="col-span-full text-center py-10 text-gray-500">
                  Bu filtreye uygun zone bulunamadı.
                </div>
              )
              : filteredZones.map(z => <ZoneCrowdCard key={z.zoneId} zone={z} />)
          }
        </div>

        <p className="text-gray-600 text-xs text-center mt-4">
          Her {CROWD_REFRESH / 1000} saniyede otomatik yenilenir
        </p>
      </div>
    </div>
  )
}
