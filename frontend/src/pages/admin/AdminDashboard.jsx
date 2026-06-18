import React, { useState, useEffect, useCallback, useRef } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { adminApi, statsApi } from '../../api/adminApi'
import { predictionApi } from '../../api/predictionApi'
import { getHeatmapLive } from '../../api/heatmap'

// ── Sabitler ─────────────────────────────────────────────────────────────────

const QUICK_LINKS = [
  { label: 'Canlı Isı Haritası',  icon: '🗺️',  to: '/admin/heatmap'     },
  { label: 'Yoğunluk Yönetimi',   icon: '📊',  to: '/admin/occupancy'   },
  { label: 'Enerji Yönetimi',     icon: '⚡',  to: '/admin/energy'      },
  { label: 'AI Tahminleri',        icon: '🔮',  to: '/admin/predictions' },
  { label: 'Raporlar',             icon: '📋',  to: '/admin/reports'     },
  { label: 'Sistem Ayarları',      icon: '⚙️',  to: '/admin/settings'    },
]

const SERVICE_LABELS = {
  backend:   'Backend',
  database:  'Veritabanı',
  redis:     'Redis',
  aiService: 'AI Servisi',
  yolov8:    'YOLOv8',
}

// ── Yardımcı fonksiyonlar ─────────────────────────────────────────────────────

function toDateStr(offsetDays = 0) {
  const d = new Date()
  d.setDate(d.getDate() + offsetDays)
  return d.toISOString().slice(0, 10)
}

function sumHourly(arr) {
  return (arr ?? []).reduce((s, p) => s + (p.value ?? 0), 0)
}

// ── Ana bileşen ───────────────────────────────────────────────────────────────

export default function AdminDashboard() {
  const navigate = useNavigate()

  // Ana dashboard verisi (30 sn polling)
  const [summary,     setSummary]     = useState(null)
  const [loading,     setLoading]     = useState(true)
  const [error,       setError]       = useState(null)
  const [lastUpdated, setLastUpdated] = useState(null)

  // Heatmap (60 sn polling) — alertZones + fullCount/busyCount için
  const [heatmap, setHeatmap] = useState(null)

  // AI tahminleri (60 sn polling)
  const [predictions,    setPredictions]    = useState([])
  const [highRiskCount,  setHighRiskCount]  = useState(0)
  const [mediumRiskCount,setMediumRiskCount]= useState(0)

  // Sistem sağlığı (60 sn polling)
  const [health,        setHealth]        = useState(null)
  const [healthLoading, setHealthLoading] = useState(true)

  // Enerji trendi (tek seferlik)
  const [energyToday,     setEnergyToday]     = useState(null)
  const [energyYesterday, setEnergyYesterday] = useState(null)

  const isMounted = useRef(true)

  // ── Veri çekme fonksiyonları ────────────────────────────────────────────────

  const fetchSummary = useCallback(async () => {
    try {
      const res = await adminApi.getDashboard()
      if (!isMounted.current) return
      setSummary(res.data.data)
      setLastUpdated(new Date())
      setError(null)
    } catch (err) {
      if (isMounted.current)
        setError(err.response?.data?.message || 'Veriler alınamadı')
    } finally {
      if (isMounted.current) setLoading(false)
    }
  }, [])

  const fetchHeatmap = useCallback(async () => {
    try {
      const data = await getHeatmapLive()
      if (isMounted.current) setHeatmap(data)
    } catch { /* sessizce */ }
  }, [])

  const fetchPredictions = useCallback(async () => {
    try {
      const res = await predictionApi.getAll()
      if (!isMounted.current) return
      const data = res.data.data ?? []
      setHighRiskCount(data.filter(p => p.riskLevel === 'HIGH').length)
      setMediumRiskCount(data.filter(p => p.riskLevel === 'MEDIUM').length)
      setPredictions(
        [...data]
          .filter(p => p.riskLevel === 'HIGH')
          .sort((a, b) => (b.predictedLoad ?? 0) - (a.predictedLoad ?? 0))
          .slice(0, 5)
      )
    } catch { /* sessizce */ }
  }, [])

  const fetchHealth = useCallback(async () => {
    try {
      const res = await adminApi.getSystemHealth()
      if (isMounted.current) {
        setHealth(res.data.data)
        setHealthLoading(false)
      }
    } catch {
      if (isMounted.current) setHealthLoading(false)
    }
  }, [])

  const fetchEnergyTrend = useCallback(async () => {
    try {
      const [todayRes, yestRes] = await Promise.all([
        adminApi.getEnergyReport(toDateStr(0)).catch(() => ({ data: { data: [] } })),
        adminApi.getEnergyReport(toDateStr(-1)).catch(() => ({ data: { data: [] } })),
      ])
      if (!isMounted.current) return
      setEnergyToday(sumHourly(todayRes.data.data))
      setEnergyYesterday(sumHourly(yestRes.data.data))
    } catch { /* sessizce */ }
  }, [])

  // ── Mount / Unmount ──────────────────────────────────────────────────────────

  useEffect(() => {
    isMounted.current = true

    fetchSummary()
    fetchHeatmap()
    fetchPredictions()
    fetchHealth()
    fetchEnergyTrend()

    const summaryInterval     = setInterval(fetchSummary,     30_000)
    const heatmapInterval     = setInterval(fetchHeatmap,     60_000)
    const predictionsInterval = setInterval(fetchPredictions, 60_000)
    const healthInterval      = setInterval(fetchHealth,      60_000)

    return () => {
      isMounted.current = false
      clearInterval(summaryInterval)
      clearInterval(heatmapInterval)
      clearInterval(predictionsInterval)
      clearInterval(healthInterval)
    }
  }, [fetchSummary, fetchHeatmap, fetchPredictions, fetchHealth, fetchEnergyTrend])

  // ── Skeleton / Hata ──────────────────────────────────────────────────────────

  if (loading) return <DashboardSkeleton />

  if (error) return (
    <div className="flex-1 p-6">
      <div className="px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-400 text-sm">
        {error}
      </div>
    </div>
  )

  // ── Hesaplamalar ─────────────────────────────────────────────────────────────

  const {
    criticalZoneCount = 0,
    totalEnergyKwh    = 0,
    savingSuggestionCount = 0,
    totalUsers        = 0,
    newUsersToday     = 0,
    zoneOccupancies   = [],
  } = summary ?? {}

  // Heatmap'ten 4'lü doluluk dağılımı (varsa), yoksa dashboard verisinden
  const fullCount     = heatmap?.fullCount     ?? 0
  const busyCount     = heatmap?.busyCount     ?? 0
  const moderateCount = heatmap?.moderateCount ?? 0
  const emptyCount    = heatmap?.emptyCount    ?? 0
  const alertZones    = heatmap?.alertZones    ?? []

  // En yoğun zone'lar (density sıralı, ilk 6)
  const topZones = [...zoneOccupancies]
    .sort((a, b) => (b.densityPct ?? 0) - (a.densityPct ?? 0))
    .slice(0, 6)

  // Enerji trendi
  let energyTrendPct = null
  let energyTrendDir = null
  if (energyYesterday > 0 && energyToday !== null) {
    energyTrendPct = (((energyToday - energyYesterday) / energyYesterday) * 100).toFixed(1)
    energyTrendDir = parseFloat(energyTrendPct) >= 0 ? 'up' : 'down'
  }

  return (
    <div className="flex-1 p-6 space-y-5 overflow-auto">

      {/* ── Başlık ─────────────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Admin Paneli</h1>
          <p className="text-gray-400 text-sm mt-0.5">Sistem geneli anlık özet</p>
        </div>
        <div className="flex items-center gap-3">
          {lastUpdated && (
            <span className="text-gray-500 text-xs">
              {lastUpdated.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' })}
            </span>
          )}
          <button
            onClick={fetchSummary}
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

      {/* ── 4 KPI Kartı ────────────────────────────────────────────────────── */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">

        {/* Yoğunluk */}
        <KpiCard
          to="/admin/occupancy"
          icon="🏢"
          title="Anlık Yoğunluk"
          mainValue={`${fullCount + busyCount} kritik`}
          sub1={`${moderateCount} orta · ${emptyCount} boş`}
          sub2={`Toplam ${zoneOccupancies.length} bölge`}
          accent={fullCount + busyCount > 0 ? 'red' : 'green'}
          badge={fullCount + busyCount > 0 ? `${fullCount + busyCount} alarm` : 'Normal'}
          badgeColor={fullCount + busyCount > 0 ? 'red' : 'green'}
        />

        {/* Enerji */}
        <KpiCard
          to="/admin/energy"
          icon="⚡"
          title="Enerji Tüketimi"
          mainValue={`${(totalEnergyKwh ?? 0).toFixed(1)} kWh`}
          sub1={
            energyTrendPct !== null
              ? `${energyTrendDir === 'up' ? '↑' : '↓'} %${Math.abs(energyTrendPct)} dünden`
              : 'Anlık tüketim'
          }
          sub2={
            savingSuggestionCount > 0
              ? `${savingSuggestionCount} tasarruf önerisi`
              : 'Tüm sistemler verimli'
          }
          accent={savingSuggestionCount > 0 ? 'orange' : 'green'}
          badge={
            energyTrendPct !== null
              ? `${energyTrendDir === 'up' ? '↑' : '↓'} %${Math.abs(energyTrendPct)}`
              : null
          }
          badgeColor={
            energyTrendDir === 'up' ? 'orange' : energyTrendDir === 'down' ? 'green' : 'gray'
          }
        />

        {/* AI Risk */}
        <KpiCard
          to="/admin/predictions"
          icon="🔮"
          title="AI Risk Durumu"
          mainValue={`${highRiskCount} yüksek risk`}
          sub1={`${mediumRiskCount} orta risk`}
          sub2="Bölge tahmin analizi"
          accent={highRiskCount > 0 ? 'red' : 'green'}
          badge={highRiskCount > 0 ? 'DİKKAT' : 'Normal'}
          badgeColor={highRiskCount > 0 ? 'red' : 'green'}
          pulse={highRiskCount > 0}
        />

        {/* Kullanıcı */}
        <KpiCard
          to="/admin/reports"
          icon="👥"
          title="Kullanıcılar"
          mainValue={`${totalUsers} kayıtlı`}
          sub1={`Bugün ${newUsersToday} yeni kayıt`}
          sub2="Tüm raporlar için tıklayın"
          accent="blue"
          badge={newUsersToday > 0 ? `+${newUsersToday} bugün` : null}
          badgeColor="blue"
        />
      </div>

      {/* ── Orta: Sistem Sağlığı + Kritik Bölgeler ─────────────────────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">

        {/* Sistem Sağlığı */}
        <SystemHealthPanel health={health} loading={healthLoading} />

        {/* Kritik Bölgeler */}
        <CriticalZonesPanel
          alertZones={alertZones}
          topZones={topZones}
          onNavigate={() => navigate('/admin/occupancy')}
        />
      </div>

      {/* ── Hızlı Erişim ───────────────────────────────────────────────────── */}
      <div>
        <h2 className="text-white font-semibold mb-3">Hızlı Erişim</h2>
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
          {QUICK_LINKS.map(link => (
            <Link
              key={link.to}
              to={link.to}
              className="flex flex-col items-center gap-2 p-4 rounded-xl
                         bg-gray-800 border border-gray-700 text-center
                         hover:border-eco-green/50 hover:bg-gray-700/80 transition-all group"
            >
              <span className="text-2xl group-hover:scale-110 transition-transform">{link.icon}</span>
              <span className="text-gray-300 text-xs font-medium leading-tight">{link.label}</span>
            </Link>
          ))}
        </div>
      </div>
    </div>
  )
}

// ── KPI Kartı ─────────────────────────────────────────────────────────────────

const ACCENT_STYLES = {
  red:    { border: 'border-red-500/40',    bg: 'bg-red-500/5',    badge: 'bg-red-500/20 text-red-400',    dot: 'bg-red-500'    },
  orange: { border: 'border-orange-500/40', bg: 'bg-orange-500/5', badge: 'bg-orange-500/20 text-orange-400', dot: 'bg-orange-500' },
  green:  { border: 'border-eco-green/40',  bg: 'bg-eco-green/5',  badge: 'bg-eco-green/20 text-eco-green',  dot: 'bg-eco-green'  },
  blue:   { border: 'border-blue-500/40',   bg: 'bg-blue-500/5',   badge: 'bg-blue-500/20 text-blue-400',   dot: 'bg-blue-500'   },
  gray:   { border: 'border-gray-500/40',   bg: 'bg-gray-500/5',   badge: 'bg-gray-500/20 text-gray-400',   dot: 'bg-gray-500'   },
}

function KpiCard({ to, icon, title, mainValue, sub1, sub2, accent = 'blue', badge, badgeColor = 'blue', pulse = false }) {
  const styles = ACCENT_STYLES[accent] ?? ACCENT_STYLES.blue
  const badgeStyles = ACCENT_STYLES[badgeColor] ?? ACCENT_STYLES.blue

  return (
    <Link
      to={to}
      className={`block p-4 rounded-xl border ${styles.border} ${styles.bg}
                  bg-gray-800 hover:bg-gray-750 hover:border-opacity-70
                  transition-all cursor-pointer group`}
    >
      <div className="flex items-start justify-between mb-3">
        <div className="flex items-center gap-2">
          <span className="text-xl">{icon}</span>
          <span className="text-gray-400 text-xs font-medium">{title}</span>
        </div>
        {badge && (
          <span className={`flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold ${badgeStyles.badge}`}>
            {pulse && (
              <span className="relative flex h-1.5 w-1.5">
                <span className={`animate-ping absolute inline-flex h-full w-full rounded-full ${badgeStyles.dot} opacity-75`} />
                <span className={`relative inline-flex rounded-full h-1.5 w-1.5 ${badgeStyles.dot}`} />
              </span>
            )}
            {badge}
          </span>
        )}
      </div>

      <p className="text-white text-xl font-bold leading-tight mb-1">{mainValue}</p>
      <p className="text-gray-400 text-xs">{sub1}</p>
      <p className="text-gray-600 text-xs mt-0.5">{sub2}</p>

      <div className="mt-3 flex items-center justify-end">
        <span className="text-eco-green text-xs opacity-0 group-hover:opacity-100 transition-opacity font-medium">
          Detaylar →
        </span>
      </div>
    </Link>
  )
}

// ── Sistem Sağlığı Paneli ─────────────────────────────────────────────────────

function SystemHealthPanel({ health, loading }) {
  const services = health
    ? Object.entries(SERVICE_LABELS).map(([key, label]) => ({
        key,
        label,
        status: health[key] ?? 'UNKNOWN',
      }))
    : Object.entries(SERVICE_LABELS).map(([key, label]) => ({
        key,
        label,
        status: loading ? 'LOADING' : 'UNKNOWN',
      }))

  const upCount   = services.filter(s => s.status === 'UP').length
  const downCount = services.filter(s => s.status === 'DOWN').length

  return (
    <div className="bg-gray-800 rounded-xl border border-gray-700 p-4">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-white font-semibold">Sistem Sağlığı</h2>
          {!loading && (
            <p className="text-gray-500 text-xs mt-0.5">
              {downCount === 0
                ? `${upCount} servis çevrimiçi`
                : `${upCount} çevrimiçi · ${downCount} sorunlu`}
            </p>
          )}
        </div>
        <div className={`w-2.5 h-2.5 rounded-full ${
          loading ? 'bg-gray-600' :
          downCount === 0 ? 'bg-eco-green animate-pulse' : 'bg-red-500 animate-pulse'
        }`} />
      </div>

      <div className="space-y-2.5">
        {services.map(({ key, label, status }) => (
          <div key={key} className="flex items-center justify-between">
            <span className="text-gray-300 text-sm">{label}</span>
            <ServiceStatusBadge status={status} />
          </div>
        ))}
      </div>
    </div>
  )
}

function ServiceStatusBadge({ status }) {
  if (status === 'LOADING') return (
    <span className="flex items-center gap-1.5 text-xs text-gray-500">
      <span className="w-1.5 h-1.5 rounded-full bg-gray-600 animate-pulse" />
      Kontrol ediliyor
    </span>
  )
  if (status === 'UP') return (
    <span className="flex items-center gap-1.5 text-xs text-eco-green font-medium">
      <span className="w-1.5 h-1.5 rounded-full bg-eco-green" />
      Çevrimiçi
    </span>
  )
  if (status === 'DOWN') return (
    <span className="flex items-center gap-1.5 text-xs text-red-400 font-medium">
      <span className="w-1.5 h-1.5 rounded-full bg-red-500" />
      Çevrimdışı
    </span>
  )
  return (
    <span className="flex items-center gap-1.5 text-xs text-gray-500">
      <span className="w-1.5 h-1.5 rounded-full bg-gray-600" />
      Bilinmiyor
    </span>
  )
}

// ── Kritik Bölgeler Paneli ────────────────────────────────────────────────────

function CriticalZonesPanel({ alertZones, topZones, onNavigate }) {
  const hasCritical = alertZones.length > 0 || topZones.some(z => (z.densityPct ?? 0) >= 0.85)

  return (
    <div className="bg-gray-800 rounded-xl border border-gray-700 p-4">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-white font-semibold">En Yoğun Bölgeler</h2>
          <p className="text-gray-500 text-xs mt-0.5">
            {hasCritical ? 'Kritik bölgeler üstte gösteriliyor' : 'Anlık doluluk sıralaması'}
          </p>
        </div>
        <button
          onClick={onNavigate}
          className="text-eco-green text-xs hover:underline font-medium"
        >
          Yönetim →
        </button>
      </div>

      {topZones.length === 0 ? (
        <div className="flex items-center justify-center h-32 text-gray-600 text-sm">
          Bölge verisi yükleniyor...
        </div>
      ) : !hasCritical ? (
        <div className="flex flex-col items-center justify-center h-28 gap-2">
          <span className="text-2xl">✅</span>
          <p className="text-eco-green text-sm font-medium">Şu an kritik bölge yok</p>
          <p className="text-gray-500 text-xs">Tüm bölgeler normal seviyede</p>
        </div>
      ) : (
        <div className="space-y-2">
          {topZones.map(z => {
            const pct = Math.round((z.densityPct ?? 0) * 100)
            const isCritical = pct >= 85
            const isBusy     = pct >= 60 && pct < 85
            return (
              <button
                key={z.zoneId}
                onClick={onNavigate}
                className="w-full flex items-center justify-between px-3 py-2.5 rounded-lg
                           bg-gray-900/50 hover:bg-gray-700/50 transition-colors text-left"
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  <div className={`w-2 h-2 rounded-full flex-shrink-0 ${
                    isCritical ? 'bg-red-500' :
                    isBusy     ? 'bg-yellow-400' : 'bg-eco-green'
                  }`} />
                  <span className="text-gray-200 text-sm font-medium truncate">
                    {z.zoneName}
                  </span>
                </div>
                <div className="flex items-center gap-3 flex-shrink-0 ml-2">
                  <div className="w-20 h-1.5 bg-gray-700 rounded-full overflow-hidden">
                    <div
                      className={`h-full rounded-full transition-all ${
                        isCritical ? 'bg-red-500' :
                        isBusy     ? 'bg-yellow-400' : 'bg-eco-green'
                      }`}
                      style={{ width: `${Math.min(pct, 100)}%` }}
                    />
                  </div>
                  <span className={`text-xs font-semibold w-8 text-right ${
                    isCritical ? 'text-red-400' :
                    isBusy     ? 'text-yellow-400' : 'text-eco-green'
                  }`}>
                    %{pct}
                  </span>
                  <ZoneLevelBadge pct={pct} />
                </div>
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}

function ZoneLevelBadge({ pct }) {
  if (pct >= 85) return (
    <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-red-500/20 text-red-400 w-14 text-center">
      DOLU
    </span>
  )
  if (pct >= 60) return (
    <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-yellow-500/20 text-yellow-400 w-14 text-center">
      YOĞUN
    </span>
  )
  if (pct >= 20) return (
    <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-blue-500/20 text-blue-400 w-14 text-center">
      ORTA
    </span>
  )
  return (
    <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-eco-green/20 text-eco-green w-14 text-center">
      BOŞ
    </span>
  )
}

// ── Skeleton ─────────────────────────────────────────────────────────────────

function DashboardSkeleton() {
  return (
    <div className="flex-1 p-6 space-y-5 animate-pulse">
      <div className="h-8 w-44 bg-gray-700 rounded" />
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        {[1, 2, 3, 4].map(i => (
          <div key={i} className="bg-gray-800 rounded-xl p-4 border border-gray-700 h-36" />
        ))}
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div className="bg-gray-800 rounded-xl p-4 border border-gray-700 h-52" />
        <div className="bg-gray-800 rounded-xl p-4 border border-gray-700 h-52" />
      </div>
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
        {[1, 2, 3, 4, 5, 6].map(i => (
          <div key={i} className="bg-gray-800 rounded-xl h-20 border border-gray-700" />
        ))}
      </div>
    </div>
  )
}
