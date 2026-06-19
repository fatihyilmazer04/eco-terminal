import React, { useState, useEffect, useCallback } from 'react'
import toast from 'react-hot-toast'
import { getSystemStats, getZoneSettings, updateZoneThreshold, getServicesHealth,
         getAllUsers, updateUser, changePassword } from '../../api/settingsApi'

// ── Sekme sabitleri ─────────────────────────────────────────────────────────
const TABS = [
  { id: 'overview',    label: 'Genel Bakış',       icon: '📊' },
  { id: 'thresholds',  label: 'Bölge Eşikleri',    icon: '🎯' },
  { id: 'users',       label: 'Kullanıcı Yönetimi', icon: '👤' },
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


// ── Kullanıcı Yönetimi Sekmesi ──────────────────────────────────────────────
function UsersTab() {
  const [users,    setUsers]    = useState([])
  const [loading,  setLoading]  = useState(true)
  const [editId,   setEditId]   = useState(null)
  const [editVals, setEditVals] = useState({})
  const [pwForm,   setPwForm]   = useState({ current: '', next: '' })
  const [pwSaving, setPwSaving] = useState(false)

  useEffect(() => {
    getAllUsers()
      .then(r => setUsers(r.data.data ?? []))
      .catch(() => toast.error('Kullanıcılar alınamadı'))
      .finally(() => setLoading(false))
  }, [])

  async function saveUser(id) {
    try {
      const res = await updateUser(id, editVals)
      setUsers(prev => prev.map(u => u.userId === id ? { ...res.data.data, fullName: u.fullName } : u))
      toast.success('Kullanıcı güncellendi')
      setEditId(null)
    } catch (err) {
      toast.error(err.response?.data?.message || 'Güncelleme başarısız')
    }
  }

  async function handlePwChange(e) {
    e.preventDefault()
    setPwSaving(true)
    try {
      await changePassword({ currentPassword: pwForm.current, newPassword: pwForm.next })
      toast.success('Şifre değiştirildi')
      setPwForm({ current: '', next: '' })
    } catch (err) {
      toast.error(err.response?.data?.message || 'Şifre değiştirilemedi')
    } finally {
      setPwSaving(false)
    }
  }

  const ROLE_BADGE = {
    ADMIN: 'text-yellow-400 bg-yellow-500/10 border border-yellow-500/30',
    USER:  'text-blue-400 bg-blue-500/10 border border-blue-500/30',
  }

  return (
    <div className="space-y-6">
      {/* Kullanıcı Listesi */}
      <div className="bg-gray-800 rounded-xl border border-gray-700 overflow-hidden">
        <div className="p-4 border-b border-gray-700 flex items-center justify-between">
          <h3 className="text-white font-medium">Kullanıcılar ({users.length})</h3>
        </div>
        {loading ? (
          <div className="p-6 text-center text-gray-500 text-sm animate-pulse">Yükleniyor...</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-gray-400 text-left border-b border-gray-700">
                  <th className="px-4 py-2.5 font-medium">Ad Soyad</th>
                  <th className="px-4 py-2.5 font-medium">E-posta</th>
                  <th className="px-4 py-2.5 font-medium text-center">Rol</th>
                  <th className="px-4 py-2.5 font-medium text-center">Durum</th>
                  <th className="px-4 py-2.5 font-medium text-right">Son Giriş</th>
                  <th className="px-4 py-2.5 font-medium text-right">İşlem</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-700/50">
                {users.map(u => {
                  const isEditing = editId === u.userId
                  return (
                    <tr key={u.userId} className="hover:bg-gray-700/20 transition-colors">
                      <td className="px-4 py-3 text-gray-300">{u.fullName ?? '—'}</td>
                      <td className="px-4 py-3 text-gray-200">{u.email}</td>
                      <td className="px-4 py-3 text-center">
                        {isEditing ? (
                          <select
                            value={editVals.role ?? u.role}
                            onChange={e => setEditVals(v => ({ ...v, role: e.target.value }))}
                            className="bg-gray-700 border border-gray-600 text-gray-200 text-xs rounded px-2 py-1"
                          >
                            <option value="USER">USER</option>
                            <option value="ADMIN">ADMIN</option>
                          </select>
                        ) : (
                          <span className={`text-xs px-2 py-0.5 rounded-full ${ROLE_BADGE[u.role] ?? ''}`}>
                            {u.role}
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-center">
                        {isEditing ? (
                          <select
                            value={editVals.isActive !== undefined ? String(editVals.isActive) : String(u.isActive)}
                            onChange={e => setEditVals(v => ({ ...v, isActive: e.target.value === 'true' }))}
                            className="bg-gray-700 border border-gray-600 text-gray-200 text-xs rounded px-2 py-1"
                          >
                            <option value="true">Aktif</option>
                            <option value="false">Pasif</option>
                          </select>
                        ) : (
                          <span className={`text-xs px-2 py-0.5 rounded-full ${
                            u.isActive ? 'text-eco-green bg-eco-green/10 border border-eco-green/20'
                                       : 'text-red-400 bg-red-500/10 border border-red-500/20'
                          }`}>
                            {u.isActive ? 'Aktif' : 'Pasif'}
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right text-gray-500 text-xs">
                        {u.lastLogin ? new Date(u.lastLogin).toLocaleDateString('tr-TR') : '—'}
                      </td>
                      <td className="px-4 py-3 text-right">
                        {isEditing ? (
                          <div className="flex gap-2 justify-end">
                            <button onClick={() => saveUser(u.userId)}
                              className="text-xs px-2.5 py-1 rounded bg-eco-green/20 text-eco-green border border-eco-green/30 hover:bg-eco-green/30">
                              Kaydet
                            </button>
                            <button onClick={() => { setEditId(null); setEditVals({}) }}
                              className="text-xs px-2.5 py-1 rounded bg-gray-700 text-gray-400 hover:bg-gray-600">
                              İptal
                            </button>
                          </div>
                        ) : (
                          <button onClick={() => { setEditId(u.userId); setEditVals({ role: u.role, isActive: u.isActive }) }}
                            className="text-xs px-2.5 py-1 rounded bg-gray-700 text-gray-400 hover:text-white hover:bg-gray-600 transition-colors">
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
        )}
      </div>

      {/* Şifre Değiştirme */}
      <div className="bg-gray-800 rounded-xl border border-gray-700 p-5">
        <h3 className="text-white font-medium mb-4">Şifre Değiştir</h3>
        <form onSubmit={handlePwChange} className="space-y-3 max-w-sm">
          <div>
            <label className="text-xs text-gray-400 mb-1 block">Mevcut Şifre</label>
            <input
              type="password"
              value={pwForm.current}
              onChange={e => setPwForm(v => ({ ...v, current: e.target.value }))}
              className="w-full bg-gray-700 border border-gray-600 text-gray-200 text-sm rounded-lg px-3 py-2
                         focus:outline-none focus:border-eco-green/50"
              required
            />
          </div>
          <div>
            <label className="text-xs text-gray-400 mb-1 block">Yeni Şifre</label>
            <input
              type="password"
              value={pwForm.next}
              onChange={e => setPwForm(v => ({ ...v, next: e.target.value }))}
              className="w-full bg-gray-700 border border-gray-600 text-gray-200 text-sm rounded-lg px-3 py-2
                         focus:outline-none focus:border-eco-green/50"
              minLength={6}
              required
            />
          </div>
          <button
            type="submit"
            disabled={pwSaving}
            className="px-4 py-2 rounded-lg bg-eco-green/10 border border-eco-green/30 text-eco-green
                       text-sm hover:bg-eco-green/20 transition-colors disabled:opacity-50"
          >
            {pwSaving ? 'Güncelleniyor...' : 'Şifreyi Güncelle'}
          </button>
        </form>
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
      <div className="flex gap-1 mb-6 flex-wrap bg-gray-800 p-1 rounded-xl border border-gray-700 w-fit">
        {TABS.map(tab => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`flex items-center gap-1.5 px-3 py-2 rounded-lg text-sm transition-colors
              ${activeTab === tab.id
                ? 'bg-eco-green/10 text-eco-green border border-eco-green/30 font-medium'
                : 'text-gray-400 hover:text-gray-200'}`}>
            <span className="text-base">{tab.icon}</span>
            <span className="hidden sm:inline">{tab.label}</span>
          </button>
        ))}
      </div>

      {/* Sekme içeriği */}
      {activeTab === 'overview'   && <OverviewTab />}
      {activeTab === 'thresholds' && <ThresholdsTab />}
      {activeTab === 'users'      && <UsersTab />}
    </div>
  )
}
