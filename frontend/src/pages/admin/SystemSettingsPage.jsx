import React, { useState, useEffect, useCallback } from 'react'
import toast from 'react-hot-toast'
import { getSystemStats, getZoneSettings, updateZoneThreshold, getServicesHealth } from '../../api/settingsApi'
import { refreshHeatmap } from '../../api/heatmap'

// ── Sekme sabitleri ─────────────────────────────────────────────────────────
const TABS = [
  { id: 'overview',    label: 'Genel Bakış',       icon: '📊' },
  { id: 'thresholds',  label: 'Bölge Eşikleri',    icon: '🎯' },
  { id: 'ai',          label: 'AI & Model',         icon: '🤖' },
  { id: 'tech',        label: 'Teknoloji',          icon: '🛠️' },
]

// ── Tech stack verisi (CLAUDE.md'den) ───────────────────────────────────────
const TECH_STACK = [
  { layer: 'Backend',     tech: 'Spring Boot 3.2.5',          detail: 'Java 21 · Maven',               color: '#F89820' },
  { layer: 'Frontend',    tech: 'React 18.3.1',               detail: 'Vite · Tailwind CSS · Recharts', color: '#61DAFB' },
  { layer: 'AI Servisi',  tech: 'Flask 3.0 · TensorFlow 2.15',detail: 'Python 3.11 · LSTM Modeli',      color: '#FF6F00' },
  { layer: 'Veritabanı',  tech: 'PostgreSQL 15',              detail: 'Flyway migrations · psycopg2',   color: '#336791' },
  { layer: 'Güvenlik',    tech: 'JWT · BCrypt',               detail: 'jjwt 0.12.5 · Bucket4j',        color: '#2ECC71' },
  { layer: 'Bildirimler', tech: 'Firebase FCM',               detail: 'Admin SDK 9.2.0',                color: '#FFA000' },
  { layer: 'Cache',       tech: 'Redis 7',                    detail: 'Faz 2 için planlandı',           color: '#DC382D' },
  { layer: 'Monitoring',  tech: 'Prometheus · Grafana',       detail: 'Spring Actuator · 15s scrape',   color: '#E6522C' },
  { layer: 'CI/CD',       tech: 'GitHub Actions',             detail: '4 job pipeline (test+build)',    color: '#24292E' },
  { layer: 'Container',   tech: 'Docker Compose',             detail: '8 servis · YOLOv8 dahil',        color: '#2496ED' },
]

const AI_CONFIG = [
  { label: 'Model Mimarisi',     value: 'LSTM(64) → Dropout(0.2) → LSTM(32) → Dense(1, sigmoid)' },
  { label: 'Girdi',              value: 'Son 60 okuma · [people_count, density_pct]' },
  { label: 'Çıktı',             value: 'density_pct tahmini (0.0 – 1.0)' },
  { label: 'Tahmin Aralığı',    value: '5 dakika (Spring @Scheduled)' },
  { label: 'Fallback',          value: 'Son 5 okuma ağırlıklı ortalaması ± gürültü' },
  { label: 'Risk Eşikleri',     value: 'LOW < %60 · MEDIUM %60–84 · HIGH ≥ %85' },
  { label: 'Tahmin Ufku',       value: '30 dakika (next_minutes=30)' },
]

// ── Yardımcı bileşenler ─────────────────────────────────────────────────────

function StatCard({ icon, label, value, sub, color = '#2ECC71' }) {
  return (
    <div className="bg-gray-800 rounded-xl border border-gray-700 p-4">
      <div className="flex items-center gap-2 mb-2">
        <span className="text-lg">{icon}</span>
        <p className="text-gray-400 text-xs">{label}</p>
      </div>
      <p className="text-2xl font-bold" style={{ color }}>{value}</p>
      {sub && <p className="text-gray-500 text-xs mt-1">{sub}</p>}
    </div>
  )
}

function ServiceCard({ name, status, responseMs }) {
  const isUp = status === 'UP'
  return (
    <div className={`rounded-xl border p-4 flex items-center justify-between
      ${isUp ? 'bg-eco-green/5 border-eco-green/20' : 'bg-red-500/5 border-red-500/20'}`}>
      <div className="flex items-center gap-3">
        <span className={`w-2.5 h-2.5 rounded-full flex-shrink-0 ${isUp ? 'bg-eco-green animate-pulse' : 'bg-red-500'}`} />
        <p className="text-white text-sm font-medium">{name}</p>
      </div>
      <div className="text-right">
        <p className={`text-xs font-semibold ${isUp ? 'text-eco-green' : 'text-red-400'}`}>{status}</p>
        {isUp && responseMs >= 0 && (
          <p className="text-gray-500 text-xs">{responseMs}ms</p>
        )}
      </div>
    </div>
  )
}

// ── Genel Bakış Sekmesi ─────────────────────────────────────────────────────
function OverviewTab() {
  const [stats,    setStats]    = useState(null)
  const [services, setServices] = useState([])
  const [loading,  setLoading]  = useState(true)

  const fetchAll = useCallback(async () => {
    setLoading(true)
    try {
      const [sRes, hRes] = await Promise.all([
        getSystemStats(),
        getServicesHealth(),
      ])
      setStats(sRes.data.data)
      setServices(hRes.data.data ?? [])
    } catch {
      toast.error('Veriler alınamadı')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchAll() }, [fetchAll])

  if (loading) return (
    <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 animate-pulse">
      {Array.from({ length: 8 }).map((_, i) => (
        <div key={i} className="h-24 bg-gray-800 rounded-xl border border-gray-700" />
      ))}
    </div>
  )

  return (
    <div className="space-y-6">
      {/* İstatistik kartları */}
      <div>
        <h3 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-3">Sistem İstatistikleri</h3>
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
          <StatCard icon="🗺️" label="Toplam Bölge"    value={stats?.totalZones ?? '—'} />
          <StatCard icon="✅" label="Aktif Bölge"      value={stats?.activeZones ?? '—'} color="#2ECC71" />
          <StatCard icon="📡" label="Toplam Okuma"     value={stats?.totalReadings?.toLocaleString('tr-TR') ?? '—'} color="#3B82F6" />
          <StatCard icon="👤" label="Kullanıcı"        value={stats?.totalUsers ?? '—'} color="#F39C12" />
          <StatCard icon="🔧" label="IoT Cihaz"        value={stats?.totalDevices ?? '—'} color="#9CA3AF" />
          <StatCard icon="🟢" label="Çevrimiçi Cihaz" value={stats?.onlineDevices ?? '—'} color="#2ECC71" />
        </div>
      </div>

      {/* Servis sağlık kartları */}
      <div>
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-semibold text-gray-400 uppercase tracking-wider">Servis Sağlık Durumu</h3>
          <button onClick={fetchAll}
            className="text-xs text-gray-500 hover:text-gray-300 flex items-center gap-1">
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            Yenile
          </button>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          {services.map(s => (
            <ServiceCard key={s.name} name={s.name} status={s.status} responseMs={s.responseMs} />
          ))}
          {/* Redis — Phase 2 */}
          <div className="rounded-xl border border-gray-700 bg-gray-800/50 p-4 flex items-center justify-between opacity-60">
            <div className="flex items-center gap-3">
              <span className="w-2.5 h-2.5 rounded-full bg-gray-600" />
              <p className="text-gray-400 text-sm font-medium">Redis 7</p>
            </div>
            <p className="text-xs text-gray-500">Faz 2</p>
          </div>
        </div>
      </div>

      {/* Versiyon bilgisi */}
      {stats && (
        <div className="rounded-xl border border-gray-700 bg-gray-800 p-4 flex flex-wrap gap-6">
          <div>
            <p className="text-gray-500 text-xs">Backend</p>
            <p className="text-white text-sm font-medium">{stats.backendVersion}</p>
          </div>
          <div>
            <p className="text-gray-500 text-xs">Java</p>
            <p className="text-white text-sm font-medium">{stats.javaVersion}</p>
          </div>
          <div>
            <p className="text-gray-500 text-xs">Auth</p>
            <p className="text-white text-sm font-medium">JWT · Access 15dk · Refresh 7gün</p>
          </div>
          <div>
            <p className="text-gray-500 text-xs">Rate Limit</p>
            <p className="text-white text-sm font-medium">Login 10 istek/dk · FCM 5dk cooldown</p>
          </div>
        </div>
      )}
    </div>
  )
}

// ── Bölge Eşikleri Sekmesi ──────────────────────────────────────────────────
function ThresholdsTab() {
  const [zones,   setZones]   = useState([])
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState({}) // zoneId → draftValue (string)
  const [saving,  setSaving]  = useState(null)

  useEffect(() => {
    getZoneSettings()
      .then(r => setZones(r.data.data ?? []))
      .catch(() => toast.error('Bölge verisi alınamadı'))
      .finally(() => setLoading(false))
  }, [])

  const handleEdit = (zoneId, current) => {
    setEditing(prev => ({ ...prev, [zoneId]: String(Math.round((current ?? 0.85) * 100)) }))
  }

  const handleCancel = (zoneId) => {
    setEditing(prev => { const n = { ...prev }; delete n[zoneId]; return n })
  }

  const handleSave = async (zoneId) => {
    const raw = editing[zoneId]
    const pct = parseFloat(raw)
    if (isNaN(pct) || pct < 10 || pct > 100) {
      toast.error('Eşik %10 ile %100 arasında olmalı')
      return
    }
    setSaving(zoneId)
    try {
      const res = await updateZoneThreshold(zoneId, pct / 100)
      const updated = res.data.data
      setZones(prev => prev.map(z => z.zoneId === zoneId ? updated : z))
      handleCancel(zoneId)
      toast.success('Eşik güncellendi')
    } catch {
      toast.error('Güncelleme başarısız')
    } finally {
      setSaving(null)
    }
  }

  const TYPE_LABELS = {
    CHECKIN: 'Check-In', SECURITY: 'Güvenlik', LOUNGE: 'Bekleme',
    GATE: 'Kapı', RETAIL: 'Mağaza', OTHER: 'Diğer',
  }

  if (loading) return (
    <div className="space-y-2 animate-pulse">
      {Array.from({ length: 6 }).map((_, i) => (
        <div key={i} className="h-14 bg-gray-800 rounded-xl border border-gray-700" />
      ))}
    </div>
  )

  return (
    <div>
      <p className="text-gray-400 text-sm mb-4">
        Her bölgenin doluluk uyarı eşiğini düzenleyin. Bu değerin üzerine çıkıldığında otomatik uyarı tetiklenir.
      </p>
      <div className="rounded-xl border border-gray-700 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-700 bg-gray-800/60">
              <th className="text-left px-4 py-3 text-gray-400 font-medium">Bölge</th>
              <th className="text-left px-4 py-3 text-gray-400 font-medium">Tür</th>
              <th className="text-right px-4 py-3 text-gray-400 font-medium">Kapasite</th>
              <th className="text-center px-4 py-3 text-gray-400 font-medium">Kritik Eşik</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {zones.map((zone, i) => {
              const isEditing = editing[zone.zoneId] !== undefined
              const isSaving  = saving === zone.zoneId
              const pct = Math.round((zone.criticalThreshold ?? 0.85) * 100)

              return (
                <tr key={zone.zoneId}
                  className={`border-b border-gray-800 transition-colors
                    ${i % 2 === 0 ? 'bg-gray-900' : 'bg-gray-800/30'}
                    ${isEditing ? 'bg-eco-green/5' : ''}`}>
                  <td className="px-4 py-3 text-white font-medium">{zone.zoneName}</td>
                  <td className="px-4 py-3 text-gray-400">{TYPE_LABELS[zone.zoneType] ?? zone.zoneType}</td>
                  <td className="px-4 py-3 text-gray-400 text-right">{zone.maxCapacity} kişi</td>
                  <td className="px-4 py-3 text-center">
                    {isEditing ? (
                      <div className="flex items-center justify-center gap-1">
                        <input
                          type="number"
                          min={10} max={100}
                          value={editing[zone.zoneId]}
                          onChange={e => setEditing(prev => ({ ...prev, [zone.zoneId]: e.target.value }))}
                          className="w-16 bg-gray-700 border border-eco-green/40 rounded text-center text-white
                                     text-sm py-1 focus:outline-none focus:border-eco-green"
                        />
                        <span className="text-gray-400 text-xs">%</span>
                      </div>
                    ) : (
                      <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium
                        ${pct >= 85 ? 'bg-red-500/10 text-red-400 border border-red-500/20'
                          : pct >= 70 ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20'
                          : 'bg-eco-green/10 text-eco-green border border-eco-green/20'}`}>
                        %{pct}
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right">
                    {isEditing ? (
                      <div className="flex items-center justify-end gap-2">
                        <button
                          onClick={() => handleSave(zone.zoneId)}
                          disabled={isSaving}
                          className="px-3 py-1 rounded bg-eco-green/20 text-eco-green text-xs
                                     hover:bg-eco-green/30 transition-colors disabled:opacity-50">
                          {isSaving ? '...' : 'Kaydet'}
                        </button>
                        <button
                          onClick={() => handleCancel(zone.zoneId)}
                          className="px-3 py-1 rounded bg-gray-700 text-gray-400 text-xs hover:bg-gray-600 transition-colors">
                          İptal
                        </button>
                      </div>
                    ) : (
                      <button
                        onClick={() => handleEdit(zone.zoneId, zone.criticalThreshold)}
                        className="px-3 py-1 rounded bg-gray-700 text-gray-400 text-xs
                                   hover:text-white hover:bg-gray-600 transition-colors">
                        Düzenle
                      </button>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      <p className="text-gray-600 text-xs mt-3">
        * Eşik değişikliği audit log'a kaydedilir. Önerilen değerler: Gate %85, Lounge %75, Güvenlik %80.
      </p>
    </div>
  )
}

// ── AI & Model Sekmesi ──────────────────────────────────────────────────────
function AITab() {
  const [refreshing, setRefreshing] = useState(false)

  const handleManualRefresh = async () => {
    setRefreshing(true)
    try {
      await refreshHeatmap()
      toast.success('AI tahmin döngüsü manuel olarak tetiklendi')
    } catch {
      toast.error('AI servisine ulaşılamadı')
    } finally {
      setRefreshing(false)
    }
  }

  return (
    <div className="space-y-6">
      {/* Model bilgisi */}
      <div className="rounded-xl border border-gray-700 bg-gray-800 overflow-hidden">
        <div className="px-4 py-3 border-b border-gray-700 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span>🤖</span>
            <h3 className="text-white font-medium text-sm">LSTM Tahmin Modeli</h3>
          </div>
          <span className="px-2 py-0.5 rounded-full bg-eco-green/10 text-eco-green text-xs border border-eco-green/20">
            TensorFlow 2.15
          </span>
        </div>
        <div className="divide-y divide-gray-800">
          {AI_CONFIG.map(item => (
            <div key={item.label} className="px-4 py-3 flex items-start justify-between gap-4">
              <p className="text-gray-400 text-sm flex-shrink-0 w-36">{item.label}</p>
              <p className="text-white text-sm text-right">{item.value}</p>
            </div>
          ))}
        </div>
      </div>

      {/* Risk eşik görseli */}
      <div className="rounded-xl border border-gray-700 bg-gray-800 p-4">
        <h3 className="text-white font-medium text-sm mb-4">Risk Seviyesi Görseli</h3>
        <div className="flex gap-2 h-8 rounded-lg overflow-hidden">
          <div className="flex-[60] bg-eco-green/30 flex items-center justify-center">
            <span className="text-eco-green text-xs font-medium">DÜŞÜK · %0–59</span>
          </div>
          <div className="flex-[25] bg-amber-500/30 flex items-center justify-center">
            <span className="text-amber-400 text-xs font-medium">ORTA · %60–84</span>
          </div>
          <div className="flex-[15] bg-red-500/30 flex items-center justify-center">
            <span className="text-red-400 text-xs font-medium">YÜKSEK · ≥%85</span>
          </div>
        </div>
      </div>

      {/* Manuel tahmin tetikle */}
      <div className="rounded-xl border border-gray-700 bg-gray-800 p-4">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-white font-medium text-sm">Manuel AI Yenileme</h3>
            <p className="text-gray-400 text-xs mt-0.5">
              Zamanlanmış 5 dakikalık döngüyü beklemeden tahmin güncelleme.
            </p>
          </div>
          <button
            onClick={handleManualRefresh}
            disabled={refreshing}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-eco-green/10 border border-eco-green/30
                       text-eco-green text-sm hover:bg-eco-green/20 transition-colors disabled:opacity-50">
            <svg className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`}
              fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            {refreshing ? 'Tetikleniyor...' : 'Tahminleri Yenile'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Teknoloji Sekmesi ───────────────────────────────────────────────────────
function TechTab() {
  return (
    <div className="space-y-4">
      <p className="text-gray-400 text-sm">
        Eco-Terminal sistemini oluşturan teknoloji yığını.
      </p>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        {TECH_STACK.map(item => (
          <div key={item.layer}
            className="rounded-xl border border-gray-700 bg-gray-800 p-4 flex items-start gap-3">
            <div className="w-3 h-3 rounded-full flex-shrink-0 mt-1"
              style={{ backgroundColor: item.color }} />
            <div>
              <p className="text-gray-400 text-xs mb-0.5">{item.layer}</p>
              <p className="text-white text-sm font-medium">{item.tech}</p>
              <p className="text-gray-500 text-xs mt-0.5">{item.detail}</p>
            </div>
          </div>
        ))}
      </div>

      {/* Monitoring linkleri */}
      <div className="rounded-xl border border-gray-700 bg-gray-800 p-4">
        <h3 className="text-white font-medium text-sm mb-3">Monitoring Panelleri</h3>
        <div className="flex flex-wrap gap-3">
          <a href="http://localhost:9090" target="_blank" rel="noreferrer"
            className="px-3 py-2 rounded-lg bg-gray-700 text-gray-300 text-sm hover:bg-gray-600 transition-colors flex items-center gap-2">
            <span>🔥</span> Prometheus :9090
          </a>
          <a href="http://localhost:3001" target="_blank" rel="noreferrer"
            className="px-3 py-2 rounded-lg bg-gray-700 text-gray-300 text-sm hover:bg-gray-600 transition-colors flex items-center gap-2">
            <span>📈</span> Grafana :3001
          </a>
          <a href="http://localhost/api/swagger-ui.html" target="_blank" rel="noreferrer"
            className="px-3 py-2 rounded-lg bg-gray-700 text-gray-300 text-sm hover:bg-gray-600 transition-colors flex items-center gap-2">
            <span>📄</span> Swagger UI
          </a>
        </div>
      </div>
    </div>
  )
}

// ── Ana Sayfa ───────────────────────────────────────────────────────────────
export default function SystemSettingsPage() {
  const [activeTab, setActiveTab] = useState('overview')

  return (
    <div className="flex-1 p-6 overflow-auto">
      {/* Başlık */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-white">Sistem Ayarları</h1>
        <p className="text-gray-400 text-sm mt-0.5">
          Servis sağlığı, bölge eşikleri ve sistem yapılandırması
        </p>
      </div>

      {/* Sekmeler */}
      <div className="flex gap-1 mb-6 bg-gray-800 p-1 rounded-xl border border-gray-700 w-fit">
        {TABS.map(tab => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm transition-colors
              ${activeTab === tab.id
                ? 'bg-eco-green/10 text-eco-green border border-eco-green/30 font-medium'
                : 'text-gray-400 hover:text-gray-200'}`}>
            <span className="text-base">{tab.icon}</span>
            {tab.label}
          </button>
        ))}
      </div>

      {/* Sekme içeriği */}
      {activeTab === 'overview'   && <OverviewTab />}
      {activeTab === 'thresholds' && <ThresholdsTab />}
      {activeTab === 'ai'         && <AITab />}
      {activeTab === 'tech'       && <TechTab />}
    </div>
  )
}
