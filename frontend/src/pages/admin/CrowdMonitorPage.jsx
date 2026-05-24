import React, { useEffect, useState, useCallback } from 'react'
import { getCrowdStatus, getAICrowdAnalysis } from '../../api/crowd'

// ── Sabitler ─────────────────────────────────────────────────────────────────
const REFRESH_INTERVAL = 30_000 // 30 saniye

const ZONE_EMOJIS = {
  GATE:     '🚪',
  LOUNGE:   '🛋️',
  CHECKIN:  '✅',
  SECURITY: '🔒',
  RETAIL:   '🛍️',
  OTHER:    '📍',
}

const STATUS_STYLES = {
  FULL:     { bg: 'bg-red-500/15',    border: 'border-red-500/40',    badge: 'bg-red-500',    text: 'Dolu'    },
  BUSY:     { bg: 'bg-yellow-400/10', border: 'border-yellow-400/40', badge: 'bg-yellow-400', text: 'Yoğun'  },
  MODERATE: { bg: 'bg-blue-500/10',   border: 'border-blue-500/40',   badge: 'bg-blue-500',   text: 'Normal' },
  EMPTY:    { bg: 'bg-green-500/10',  border: 'border-green-500/40',  badge: 'bg-green-500',  text: 'Boş'    },
}

const TREND_ICONS = { INCREASING: '↑', DECREASING: '↓', STABLE: '→', rising: '↑', falling: '↓', stable: '→' }
const TREND_COLORS = { INCREASING: 'text-red-400', DECREASING: 'text-green-400', STABLE: 'text-gray-400', rising: 'text-red-400', falling: 'text-green-400', stable: 'text-gray-400' }

// ── Alt Bileşenler ────────────────────────────────────────────────────────────
function SkeletonCard() {
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
      {/* Başlık */}
      <div className="flex items-center gap-2 mb-3">
        <div className="w-9 h-9 rounded-lg bg-gray-800/70 flex items-center justify-center text-lg flex-shrink-0">
          {emoji}
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-white text-sm font-semibold truncate">{zone.zoneName}</p>
          <p className="text-gray-400 text-xs">{zone.zoneType}</p>
        </div>
        {/* Status badge */}
        <span className={`${style.badge} text-white text-xs font-bold px-2.5 py-0.5 rounded-full flex-shrink-0`}>
          {style.text}
        </span>
      </div>

      {/* Doluluk barı */}
      <div className="mb-2">
        <div className="w-full h-2 bg-gray-700 rounded-full overflow-hidden">
          <div
            className={`h-full ${barColor} rounded-full transition-all duration-500`}
            style={{ width: `${fillPct}%` }}
          />
        </div>
      </div>

      {/* Sayılar + Trend */}
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

function SummaryBar({ zones }) {
  const counts = { FULL: 0, BUSY: 0, MODERATE: 0, EMPTY: 0 }
  zones.forEach(z => { if (counts[z.status] !== undefined) counts[z.status]++ })

  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
      {[
        { label: 'Dolu',   key: 'FULL',     color: 'text-red-400',    border: 'border-red-500/40'    },
        { label: 'Yoğun',  key: 'BUSY',     color: 'text-yellow-400', border: 'border-yellow-500/40' },
        { label: 'Normal', key: 'MODERATE', color: 'text-blue-400',   border: 'border-blue-500/40'   },
        { label: 'Boş',    key: 'EMPTY',    color: 'text-green-400',  border: 'border-green-500/40'  },
      ].map(item => (
        <div key={item.key}
             className={`rounded-xl border ${item.border} bg-gray-800/40 p-3 text-center`}>
          <p className={`text-2xl font-bold ${item.color}`}>{counts[item.key]}</p>
          <p className="text-gray-400 text-xs mt-0.5">{item.label}</p>
        </div>
      ))}
    </div>
  )
}

function AIAnalysisBox({ analysis, loading }) {
  if (loading) {
    return (
      <div className="rounded-xl border border-eco-green/30 bg-eco-green/5 p-4 animate-pulse">
        <div className="h-4 bg-gray-700 rounded w-40 mb-3" />
        <div className="h-3 bg-gray-700 rounded w-full mb-2" />
        <div className="h-3 bg-gray-700 rounded w-3/4" />
      </div>
    )
  }
  if (!analysis) return null

  return (
    <div className="rounded-xl border border-eco-green/30 bg-eco-green/5 p-4 mb-6">
      <div className="flex items-center gap-2 mb-3">
        <span className="text-eco-green">🤖</span>
        <h3 className="text-eco-green font-semibold text-sm">AI Kalabalık Analizi</h3>
        <span className="text-gray-500 text-xs ml-auto">{analysis.timestamp?.slice(11, 16)}</span>
      </div>

      {/* Özet */}
      <p className="text-gray-200 text-sm mb-3">{analysis.summary}</p>

      {/* Uyarı zone'ları */}
      {analysis.alert_zones?.length > 0 && (
        <div className="flex flex-wrap gap-1.5 mb-2">
          <span className="text-red-400 text-xs font-medium mr-1">⚠ Dolu:</span>
          {analysis.alert_zones.map(z => (
            <span key={z} className="bg-red-500/20 border border-red-500/40 text-red-300 text-xs px-2 py-0.5 rounded-full">
              {z}
            </span>
          ))}
        </div>
      )}

      {/* Boş zone'lar */}
      {analysis.empty_zones?.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          <span className="text-green-400 text-xs font-medium mr-1">✓ Boş:</span>
          {analysis.empty_zones.map(z => (
            <span key={z} className="bg-green-500/20 border border-green-500/40 text-green-300 text-xs px-2 py-0.5 rounded-full">
              {z}
            </span>
          ))}
        </div>
      )}
    </div>
  )
}

// ── Ana Sayfa ─────────────────────────────────────────────────────────────────
export default function CrowdMonitorPage() {
  const [zones,        setZones]        = useState([])
  const [analysis,     setAnalysis]     = useState(null)
  const [loading,      setLoading]      = useState(true)
  const [aiLoading,    setAiLoading]    = useState(true)
  const [lastRefresh,  setLastRefresh]  = useState(null)
  const [error,        setError]        = useState(null)

  const fetchData = useCallback(async () => {
    try {
      const data = await getCrowdStatus()
      setZones(Array.isArray(data) ? data : [])
      setLastRefresh(new Date())
      setError(null)
    } catch (err) {
      setError('Kalabalık verileri yüklenemedi.')
    } finally {
      setLoading(false)
    }
  }, [])

  const fetchAI = useCallback(async () => {
    try {
      setAiLoading(true)
      const data = await getAICrowdAnalysis()
      setAnalysis(data)
    } catch {
      // AI servisi erişilemez — sessizce geç
    } finally {
      setAiLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchData()
    fetchAI()

    const interval = setInterval(() => {
      fetchData()
      fetchAI()
    }, REFRESH_INTERVAL)

    return () => clearInterval(interval)
  }, [fetchData, fetchAI])

  // Filtre state
  const [filter, setFilter] = useState('ALL')
  const filtered = filter === 'ALL' ? zones : zones.filter(z => z.status === filter)

  return (
    <div className="p-6 max-w-7xl mx-auto">
      {/* Başlık */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-bold text-white">Kalabalık İzleme</h1>
          <p className="text-gray-400 text-sm mt-0.5">
            YOLOv8 + Sensör verisi — gerçek zamanlı
            {lastRefresh && (
              <span className="ml-2 text-gray-500">
                • Son güncelleme: {lastRefresh.toLocaleTimeString('tr-TR')}
              </span>
            )}
          </p>
        </div>
        <button
          onClick={() => { fetchData(); fetchAI() }}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-eco-green/10
                     border border-eco-green/30 text-eco-green text-sm hover:bg-eco-green/20 transition"
        >
          ↻ Yenile
        </button>
      </div>

      {/* Hata */}
      {error && (
        <div className="rounded-xl border border-red-500/30 bg-red-500/10 text-red-300 p-3 mb-4 text-sm">
          {error}
        </div>
      )}

      {/* Özet bar */}
      {!loading && <SummaryBar zones={zones} />}

      {/* AI Analiz */}
      <AIAnalysisBox analysis={analysis} loading={aiLoading} />

      {/* Filtre butonları */}
      <div className="flex gap-2 mb-4 flex-wrap">
        {['ALL', 'FULL', 'BUSY', 'MODERATE', 'EMPTY'].map(f => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={`px-3 py-1 rounded-lg text-xs font-medium border transition
              ${filter === f
                ? 'bg-eco-green text-gray-900 border-eco-green'
                : 'border-gray-700 text-gray-400 hover:border-gray-500 hover:text-gray-200'}`}
          >
            {f === 'ALL' ? `Tümü (${zones.length})` :
             f === 'FULL' ? `🔴 Dolu` :
             f === 'BUSY' ? `🟡 Yoğun` :
             f === 'MODERATE' ? `🔵 Normal` : `🟢 Boş`}
          </button>
        ))}
      </div>

      {/* Zone kartları grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
        {loading
          ? Array.from({ length: 8 }).map((_, i) => <SkeletonCard key={i} />)
          : filtered.length === 0
            ? (
              <div className="col-span-full text-center py-12 text-gray-500">
                Bu filtreye uygun zone bulunamadı.
              </div>
            )
            : filtered.map(z => <ZoneCrowdCard key={z.zoneId} zone={z} />)
        }
      </div>

      {/* Alt bilgi */}
      <p className="text-gray-600 text-xs text-center mt-6">
        Her {REFRESH_INTERVAL / 1000} saniyede otomatik yenilenir
      </p>
    </div>
  )
}
