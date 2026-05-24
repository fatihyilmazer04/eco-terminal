import React, { useState, useEffect, useCallback, useRef } from 'react'
import { Link } from 'react-router-dom'
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, Legend, AreaChart, Area, BarChart, Bar
} from 'recharts'
import KpiCard from '../../components/KpiCard'
import OccupancyCard from '../../components/OccupancyCard'
import RiskBadge from '../../components/RiskBadge'
import AirportHeatmap from '../../components/AirportHeatmap'
import HeatmapSummaryCards from '../../components/HeatmapSummaryCards'
import AIInsightBox from '../../components/AIInsightBox'
import ZoneDetailPanel from '../../components/ZoneDetailPanel'
import { adminApi, statsApi } from '../../api/adminApi'
import { predictionApi } from '../../api/predictionApi'
import { getHeatmapLive } from '../../api/heatmap'

// Bölge çizgi renkleri (recharts)
const ZONE_COLORS = ['#2ECC71', '#F39C12', '#3B82F6', '#E74C3C', '#8B5CF6', '#EC4899']

// Son 1 saatlik yoğunluk trendi için mock/live veri birleştirici
// API'den gelen anlık değerleri timeline'a çevirir
function buildTrendFromOccupancies(zones) {
  // Her bölge için son değeri tek nokta olarak döndür (gerçek trend için polling gerekir)
  // Burada son 6 okumayı simüle ediyoruz — ama şu an tek snapshot var
  const now = new Date()
  return [
    {
      time: now.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' }),
      ...Object.fromEntries(zones.map(z => [
        z.zoneName.replace(/[^a-zA-Z0-9]/g, '_'),
        parseFloat(((z.densityPct ?? 0) * 100).toFixed(1))
      ]))
    }
  ]
}

export default function AdminDashboard() {
  const [summary, setSummary]     = useState(null)
  const [trendData, setTrendData] = useState([])
  const [loading, setLoading]     = useState(true)
  const [error, setError]         = useState(null)
  const [lastUpdated, setLastUpdated] = useState(null)
  const [heatmapData, setHeatmapData] = useState(null)
  const [selectedZoneId, setSelectedZoneId] = useState(null)
  const [visitorStats, setVisitorStats] = useState([])
  const [energyStats,  setEnergyStats]  = useState([])
  const [cameras,      setCameras]      = useState([])
  const isMounted = useRef(true)
  const trendBuffer = useRef([])  // son 10 snapshot tutarız

  const fetchData = useCallback(async () => {
    try {
      const res = await adminApi.getDashboard()
      if (!isMounted.current) return
      const data = res.data.data
      setSummary(data)
      setLastUpdated(new Date())
      setError(null)

      // Trend buffer'a ekle — her 30s'de bir yeni nokta
      const now = new Date()
      const point = {
        time: now.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' }),
        ...Object.fromEntries(
          (data.zoneOccupancies ?? []).map(z => [
            z.zoneName,
            parseFloat(((z.densityPct ?? 0) * 100).toFixed(1))
          ])
        )
      }
      trendBuffer.current = [...trendBuffer.current.slice(-11), point]
      setTrendData([...trendBuffer.current])
    } catch (err) {
      if (isMounted.current)
        setError(err.response?.data?.message || 'Veriler alınamadı')
    } finally {
      if (isMounted.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    isMounted.current = true
    fetchData()
    const id = setInterval(fetchData, 30_000)
    // Heatmap'i ayrıca çek (60 sn)
    getHeatmapLive().then(d => { if (isMounted.current) setHeatmapData(d) }).catch(() => {})
    const heatmapId = setInterval(() => {
      getHeatmapLive().then(d => { if (isMounted.current) setHeatmapData(d) }).catch(() => {})
    }, 60_000)
    // İstatistikleri bir kez çek
    statsApi.getVisitors().then(r => { if (isMounted.current) setVisitorStats(r.data.data ?? []) }).catch(() => {})
    statsApi.getEnergy().then(r => { if (isMounted.current) setEnergyStats(r.data.data ?? []) }).catch(() => {})
    statsApi.getCameras().then(r => { if (isMounted.current) setCameras(r.data.data ?? []) }).catch(() => {})
    return () => {
      isMounted.current = false
      clearInterval(id)
      clearInterval(heatmapId)
    }
  }, [fetchData])

  if (loading) return <DashboardSkeleton />

  if (error) return (
    <div className="flex-1 p-6">
      <div className="px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-400 text-sm">{error}</div>
    </div>
  )

  const {
    totalPassengers, criticalZoneCount, averageDensityPct,
    totalEnergyKwh, activeFlightCount, savingSuggestionCount,
    zoneOccupancies = []
  } = summary

  const densityColor = averageDensityPct > 0.75 ? 'red' : averageDensityPct > 0.50 ? 'orange' : 'green'
  const criticalColor = criticalZoneCount > 0 ? 'red' : 'green'

  const zoneKeys = zoneOccupancies.map(z => z.zoneName)

  return (
    <div className="flex-1 p-6 space-y-6 overflow-auto">
      {/* Başlık + son güncelleme */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Admin Paneli</h1>
          <p className="text-gray-400 text-sm mt-0.5">Sistem geneli anlık özet</p>
        </div>
        <div className="flex items-center gap-3">
          {lastUpdated && (
            <span className="text-gray-500 text-xs">
              Son güncelleme: {lastUpdated.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' })}
            </span>
          )}
          <button
            onClick={fetchData}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-gray-800
                       border border-gray-700 text-gray-300 text-sm hover:border-eco-green/50 transition-colors"
          >
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            Yenile
          </button>
        </div>
      </div>

      {/* 5 KPI Kartı */}
      <div className="grid grid-cols-2 lg:grid-cols-5 gap-3">
        <KpiCard
          title="Toplam Yolcu"
          value={totalPassengers}
          subtitle="terminal içinde"
          icon="✈️"
          color="blue"
        />
        <KpiCard
          title="Kritik Bölge"
          value={criticalZoneCount}
          subtitle={criticalZoneCount > 0 ? 'müdahale gerekiyor' : 'tüm bölgeler normal'}
          icon="⚠️"
          color={criticalColor}
        />
        <KpiCard
          title="Ort. Doluluk"
          value={`%${((averageDensityPct ?? 0) * 100).toFixed(0)}`}
          subtitle="tüm bölge ortalaması"
          icon="📊"
          color={densityColor}
        />
        <KpiCard
          title="Toplam Enerji"
          value={`${(totalEnergyKwh ?? 0).toFixed(1)} kWh`}
          subtitle={`${savingSuggestionCount} tasarruf önerisi`}
          icon="⚡"
          color="orange"
        />
        <KpiCard
          title="Aktif Uçuş"
          value={activeFlightCount}
          subtitle="planlanmış + biniş"
          icon="🛫"
          color="blue"
        />
      </div>

      {/* Orta satır: LineChart + Bölge Kartları */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Sol: Yoğunluk Trendi — son 12 snapshot */}
        <div className="lg:col-span-2 bg-gray-800 rounded-xl p-4 border border-gray-700">
          <h2 className="text-white font-semibold mb-1">Yoğunluk Trendi</h2>
          <p className="text-gray-500 text-xs mb-4">Her 30 saniyede güncellenir</p>
          {trendData.length < 2 ? (
            <div className="flex items-center justify-center h-40 text-gray-500 text-sm">
              Yeterli veri biriktirilmeye devam ediyor...
              <br />Anlık doluluk: {zoneOccupancies.map(z =>
                `${z.zoneName} %${((z.densityPct ?? 0) * 100).toFixed(0)}`
              ).join(' | ')}
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={200}>
              <LineChart data={trendData} margin={{ top: 5, right: 5, left: -15, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
                <XAxis
                  dataKey="time"
                  tick={{ fill: '#9CA3AF', fontSize: 10 }}
                  tickLine={false}
                  interval="preserveStartEnd"
                />
                <YAxis
                  tick={{ fill: '#9CA3AF', fontSize: 10 }}
                  tickLine={false}
                  axisLine={false}
                  domain={[0, 100]}
                  tickFormatter={v => `${v}%`}
                />
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#1F2937',
                    border: '1px solid #374151',
                    borderRadius: '0.5rem',
                    fontSize: '11px',
                  }}
                  labelStyle={{ color: '#F9FAFB', marginBottom: 4 }}
                  formatter={(v, name) => [`%${v}`, name]}
                />
                <Legend wrapperStyle={{ fontSize: '11px', color: '#9CA3AF' }} />
                {zoneKeys.map((key, i) => (
                  <Line
                    key={key}
                    type="monotone"
                    dataKey={key}
                    name={key}
                    stroke={ZONE_COLORS[i % ZONE_COLORS.length]}
                    strokeWidth={2}
                    dot={false}
                    activeDot={{ r: 3 }}
                  />
                ))}
              </LineChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* Sağ: Bölge Kartları (küçük) */}
        <div className="space-y-2 overflow-auto max-h-72 lg:max-h-none">
          <h2 className="text-white font-semibold">Bölge Durumu</h2>
          {zoneOccupancies.map(z => (
            <OccupancyCard
              key={z.zoneId}
              zoneName={z.zoneName}
              type={z.type}
              currentCount={z.currentCount}
              maxCapacity={z.maxCapacity}
              densityPct={z.densityPct}
              densityLevel={z.densityLevel}
              colorCode={z.colorCode}
              criticalThreshold={z.criticalThreshold}
            />
          ))}
        </div>
      </div>

      {/* Alt satır: Tasarruf Önerileri + AI Tahminleri yan yana */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-gray-800 rounded-xl p-4 border border-gray-700">
          <h2 className="text-white font-semibold mb-3">Enerji Tasarruf Önerileri</h2>
          {savingSuggestionCount === 0 ? (
            <div className="flex items-center gap-2 text-eco-green text-sm">
              <span>✅</span>
              <span>Tüm sistemler verimli çalışıyor — tasarruf önerisi yok.</span>
            </div>
          ) : (
            <SavingsBanner />
          )}
        </div>

        {/* AI Tahmin Mini Özet */}
        <AIPredictionMini />
      </div>

      {/* ── 24s İstatistikler ──────────────────────────────────────────────── */}
      {(visitorStats.length > 0 || energyStats.length > 0) && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* 24s Ziyaretçi AreaChart */}
          {visitorStats.length > 0 && (
            <div className="bg-gray-800 rounded-xl p-4 border border-gray-700">
              <h2 className="text-white font-semibold mb-1">24s Ziyaretçi İstatistiği</h2>
              <p className="text-gray-500 text-xs mb-4">Saatlik ortalama kişi sayısı</p>
              <ResponsiveContainer width="100%" height={180}>
                <AreaChart
                  data={visitorStats.map(p => ({ saat: `${String(p.hour).padStart(2,'0')}:00`, kisi: Math.round(p.value ?? 0) }))}
                  margin={{ top: 5, right: 5, left: -20, bottom: 5 }}
                >
                  <defs>
                    <linearGradient id="visGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%"  stopColor="#2ECC71" stopOpacity={0.3} />
                      <stop offset="95%" stopColor="#2ECC71" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
                  <XAxis dataKey="saat" tick={{ fill: '#9CA3AF', fontSize: 9 }} tickLine={false} interval={3} />
                  <YAxis tick={{ fill: '#9CA3AF', fontSize: 9 }} tickLine={false} axisLine={false} />
                  <Tooltip
                    contentStyle={{ backgroundColor: '#1F2937', border: '1px solid #374151', borderRadius: '0.5rem', fontSize: '11px' }}
                    formatter={v => [`${v} kişi`, 'Ortalama']}
                  />
                  <Area type="monotone" dataKey="kisi" stroke="#2ECC71" strokeWidth={2}
                        fill="url(#visGrad)" dot={false} activeDot={{ r: 3 }} />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          )}

          {/* 24s Enerji BarChart */}
          {energyStats.length > 0 && (
            <div className="bg-gray-800 rounded-xl p-4 border border-gray-700">
              <h2 className="text-white font-semibold mb-1">24s Enerji Tüketimi</h2>
              <p className="text-gray-500 text-xs mb-4">Saatlik toplam kWh</p>
              <ResponsiveContainer width="100%" height={180}>
                <BarChart
                  data={energyStats.map(p => ({ saat: `${String(p.hour).padStart(2,'0')}:00`, kwh: parseFloat((p.value ?? 0).toFixed(1)) }))}
                  margin={{ top: 5, right: 5, left: -20, bottom: 5 }}
                >
                  <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
                  <XAxis dataKey="saat" tick={{ fill: '#9CA3AF', fontSize: 9 }} tickLine={false} interval={3} />
                  <YAxis tick={{ fill: '#9CA3AF', fontSize: 9 }} tickLine={false} axisLine={false} />
                  <Tooltip
                    contentStyle={{ backgroundColor: '#1F2937', border: '1px solid #374151', borderRadius: '0.5rem', fontSize: '11px' }}
                    formatter={v => [`${v} kWh`, 'Enerji']}
                  />
                  <Bar dataKey="kwh" fill="#F39C12" fillOpacity={0.8} radius={[2, 2, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </div>
      )}

      {/* Kamera / Cihaz Durumu */}
      {cameras.length > 0 && (
        <div className="bg-gray-800 rounded-xl border border-gray-700 overflow-hidden">
          <div className="p-4 border-b border-gray-700 flex items-center justify-between">
            <h2 className="text-white font-semibold">Kamera / IoT Cihaz Durumu</h2>
            <div className="flex gap-3 text-xs">
              <span className="text-eco-green">{cameras.filter(c => c.status === 'ONLINE').length} Çevrimiçi</span>
              <span className="text-red-400">{cameras.filter(c => c.status !== 'ONLINE').length} Çevrimdışı</span>
            </div>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-2 p-4">
            {cameras.map(c => (
              <div key={c.deviceId}
                className={`rounded-lg p-2.5 border text-xs ${
                  c.status === 'ONLINE'
                    ? 'bg-eco-green/5 border-eco-green/20'
                    : c.status === 'MAINTENANCE'
                    ? 'bg-yellow-500/5 border-yellow-500/20'
                    : 'bg-red-500/5 border-red-500/20'
                }`}
              >
                <div className="flex items-center gap-1.5 mb-1">
                  <div className={`w-1.5 h-1.5 rounded-full ${
                    c.status === 'ONLINE' ? 'bg-eco-green' :
                    c.status === 'MAINTENANCE' ? 'bg-yellow-400' : 'bg-red-400'
                  }`} />
                  <span className={`font-medium ${
                    c.status === 'ONLINE' ? 'text-eco-green' :
                    c.status === 'MAINTENANCE' ? 'text-yellow-400' : 'text-red-400'
                  }`}>{c.status}</span>
                </div>
                <p className="text-gray-300 font-medium truncate">{c.serialNumber}</p>
                <p className="text-gray-500 truncate">{c.zoneName}</p>
                <p className="text-gray-600">{c.deviceType}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ── Terminal Heatmap Özeti ──────────────────────────────────────────── */}
      {heatmapData && (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-white font-semibold">Terminal Yoğunluk Haritası</h2>
            <Link
              to="/admin/heatmap"
              className="text-eco-green text-xs hover:underline font-medium"
            >
              Tam ekran →
            </Link>
          </div>
          <HeatmapSummaryCards data={heatmapData} />
          <div className="grid grid-cols-1 xl:grid-cols-4 gap-4">
            <div className="xl:col-span-3">
              <AirportHeatmap
                zones={heatmapData.zones ?? []}
                onZoneClick={id => setSelectedZoneId(prev => prev === id ? null : id)}
                selectedZoneId={selectedZoneId}
              />
            </div>
            <div className="xl:col-span-1">
              {selectedZoneId ? (
                <ZoneDetailPanel
                  zone={heatmapData.zones?.find(z => z.zoneId === selectedZoneId)}
                  onClose={() => setSelectedZoneId(null)}
                />
              ) : (
                <div className="rounded-xl border border-gray-700 bg-gray-800/50 p-4 flex items-center justify-center h-full min-h-32">
                  <p className="text-gray-600 text-xs text-center">Zone seçin</p>
                </div>
              )}
            </div>
          </div>
          <AIInsightBox
            summary={heatmapData.aiSummary}
            alertZones={heatmapData.alertZones}
            suggestedZones={heatmapData.suggestedZones}
          />
        </div>
      )}
    </div>
  )
}

// AI tahmin özetini ayrı fetch eden alt bileşen
function AIPredictionMini() {
  const [predictions, setPredictions] = useState([])
  const [highRiskCount, setHighRiskCount] = useState(0)

  useEffect(() => {
    predictionApi.getAll()
      .then(r => {
        const data = r.data.data ?? []
        // Riske göre sırala, ilk 3'ü al
        const sorted = [...data].sort((a, b) => {
          const order = { HIGH: 0, MEDIUM: 1, LOW: 2 }
          return (order[a.riskLevel] ?? 3) - (order[b.riskLevel] ?? 3)
        })
        setPredictions(sorted.slice(0, 3))
        setHighRiskCount(data.filter(p => p.riskLevel === 'HIGH').length)
      })
      .catch(() => {})
  }, [])

  return (
    <div className="bg-gray-800 rounded-xl p-4 border border-gray-700 flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <h2 className="text-white font-semibold">AI Tahminleri</h2>
        <Link
          to="/admin/predictions"
          className="text-eco-green text-xs hover:underline font-medium"
        >
          Tümünü gör →
        </Link>
      </div>

      {/* HIGH risk banner */}
      {highRiskCount > 0 && (
        <div className="flex items-center gap-2 px-3 py-2 rounded-lg bg-red-500/10 border border-red-500/30">
          <span className="relative flex h-2 w-2">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-red-400 opacity-75" />
            <span className="relative inline-flex rounded-full h-2 w-2 bg-red-500" />
          </span>
          <p className="text-red-400 text-xs font-medium">
            {highRiskCount} bölge YÜKSEK RİSK seviyesinde
          </p>
        </div>
      )}

      {/* Son 3 tahmin */}
      <div className="space-y-2">
        {predictions.length === 0 ? (
          <p className="text-gray-500 text-xs py-4 text-center">AI tahmin verisi yok</p>
        ) : predictions.map(p => (
          <div key={p.zoneId}
               className="flex items-center justify-between px-3 py-2 rounded-lg bg-gray-900/50">
            <div>
              <p className="text-gray-200 text-sm font-medium">{p.zoneName}</p>
              <p className="text-gray-500 text-xs">
                Tahmin: %{((p.predictedLoad ?? 0) * 100).toFixed(0)}
              </p>
            </div>
            <RiskBadge riskLevel={p.riskLevel} trend={p.trend} />
          </div>
        ))}
      </div>
    </div>
  )
}

// Tasarruf önerilerini ayrı fetch eden alt bileşen
function SavingsBanner() {
  const [savings, setSavings] = useState([])

  useEffect(() => {
    import('../../api/adminApi').then(({ energyApi }) => {
      energyApi.getSavings()
        .then(r => setSavings(r.data.data ?? []))
        .catch(() => {})
    })
  }, [])

  return (
    <div className="space-y-2">
      {savings.map(s => (
        <div key={s.zoneId}
             className="flex items-start gap-3 px-3 py-2.5 rounded-lg bg-yellow-500/10 border border-yellow-500/20">
          <span className="text-yellow-400 mt-0.5">⚠️</span>
          <div>
            <p className="text-yellow-300 text-sm font-medium">{s.zoneName}</p>
            <p className="text-yellow-400/80 text-xs">{s.suggestion}</p>
            <p className="text-yellow-500 text-xs mt-0.5">
              Doluluk: %{((s.currentDensity ?? 0) * 100).toFixed(0)} &nbsp;·&nbsp;
              Enerji: {(s.currentEnergyKwh ?? 0).toFixed(1)} kWh &nbsp;·&nbsp;
              ~%{s.potentialSavingPct} tasarruf potansiyeli
            </p>
          </div>
        </div>
      ))}
    </div>
  )
}

function DashboardSkeleton() {
  return (
    <div className="flex-1 p-6 space-y-6 animate-pulse">
      <div className="h-8 w-48 bg-gray-700 rounded" />
      <div className="grid grid-cols-2 lg:grid-cols-5 gap-3">
        {[1,2,3,4,5].map(i => (
          <div key={i} className="bg-gray-800 rounded-xl p-4 border border-gray-700">
            <div className="h-3 w-20 bg-gray-700 rounded mb-3" />
            <div className="h-7 w-14 bg-gray-700 rounded" />
          </div>
        ))}
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-gray-800 rounded-xl p-4 border border-gray-700 h-56" />
        <div className="space-y-2">
          {[1,2,3,4].map(i => <div key={i} className="bg-gray-800 rounded-xl h-16 border border-gray-700" />)}
        </div>
      </div>
    </div>
  )
}
