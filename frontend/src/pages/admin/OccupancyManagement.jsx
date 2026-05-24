import React, { useState, useEffect, useCallback, useRef } from 'react'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell,
  LineChart, Line,
} from 'recharts'
import { occupancyApi } from '../../api/occupancyApi'
import axiosInstance from '../../api/axiosInstance'
import toast from 'react-hot-toast'

const DENSITY_COLOR = {
  LOW:      '#2ECC71',
  MEDIUM:   '#F39C12',
  HIGH:     '#E67E22',
  CRITICAL: '#E74C3C',
}
const DENSITY_LABEL = { LOW: 'Düşük', MEDIUM: 'Orta', HIGH: 'Yüksek', CRITICAL: 'Kritik' }

function rowBg(level) {
  if (level === 'CRITICAL') return 'bg-red-500/10 hover:bg-red-500/15'
  if (level === 'HIGH')     return 'bg-orange-500/8 hover:bg-orange-500/12'
  if (level === 'MEDIUM')   return 'bg-yellow-500/5 hover:bg-yellow-500/10'
  return 'hover:bg-gray-700/30'
}

function formatTime(isoString) {
  try {
    const d = new Date(isoString)
    return `${String(d.getUTCHours()).padStart(2, '0')}:${String(d.getUTCMinutes()).padStart(2, '0')}`
  } catch {
    return isoString
  }
}

/** 24 Saatlik Doluluk Geçmişi Paneli */
function ZoneHistoryPanel({ zone, onClose }) {
  const [data, setData]       = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    axiosInstance.get(`/api/heatmap/history?zone_id=${zone.zoneId}&hours=24`)
      .then(res => {
        const raw = res.data.data ?? []
        setData(raw.map((p, index) => ({
          label: `${String(index).padStart(2, '0')}:00`,
          pct:   parseFloat(((p.densityPct ?? 0) * 100).toFixed(1)),
          count: p.peopleCount ?? 0,
        })))
      })
      .catch(() => setError('Veri yüklenemedi'))
      .finally(() => setLoading(false))
  }, [zone.zoneId])

  const avg     = data.length ? (data.reduce((s, p) => s + p.pct, 0) / data.length).toFixed(1) : null
  const peak    = data.length ? data.reduce((a, b) => a.pct >= b.pct ? a : b) : null
  const current = data.length ? data[data.length - 1].pct : null

  const CustomTooltip = ({ active, payload, label }) => {
    if (!active || !payload?.length) return null
    return (
      <div className="bg-gray-900 border border-gray-700 rounded-lg px-3 py-2 text-xs">
        <p className="text-gray-300 mb-0.5">{label}</p>
        <p className="text-eco-green font-semibold">%{payload[0].value} doluluk</p>
        <p className="text-gray-400">{payload[0].payload.count} kişi</p>
      </div>
    )
  }

  return (
    <div className="bg-gray-800 rounded-xl p-4 border border-eco-green/30 shadow-lg">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-white font-semibold">
          {zone.zoneName} — Son 24 Saatlik Doluluk
        </h2>
        <button onClick={onClose} className="text-gray-500 hover:text-gray-300 transition-colors">
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-40">
          <div className="w-8 h-8 border-2 border-eco-green border-t-transparent rounded-full animate-spin" />
        </div>
      ) : error ? (
        <div className="flex items-center justify-center h-40 text-red-400 text-sm">{error}</div>
      ) : data.length === 0 ? (
        <div className="flex items-center justify-center h-40 text-gray-500 text-sm">Veri bulunamadı</div>
      ) : (
        <>
          <ResponsiveContainer width="100%" height={180}>
            <LineChart data={data} margin={{ top: 5, right: 10, left: -10, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis
                dataKey="label"
                tick={{ fill: '#9CA3AF', fontSize: 10 }}
                tickLine={false}
                interval="preserveStartEnd"
              />
              <YAxis
                tick={{ fill: '#9CA3AF', fontSize: 11 }}
                tickLine={false}
                axisLine={false}
                domain={[0, 100]}
                tickFormatter={v => `${v}%`}
              />
              <Tooltip content={<CustomTooltip />} />
              <Line
                type="monotone"
                dataKey="pct"
                stroke="#2ECC71"
                strokeWidth={2}
                dot={false}
                activeDot={{ r: 5 }}
              />
            </LineChart>
          </ResponsiveContainer>

          <div className="flex gap-6 mt-3 text-sm border-t border-gray-700 pt-3">
            {avg !== null && (
              <div>
                <span className="text-gray-500 text-xs block">Ortalama</span>
                <span className="text-white font-medium">%{avg}</span>
              </div>
            )}
            {peak && (
              <div>
                <span className="text-gray-500 text-xs block">Peak</span>
                <span className="text-orange-400 font-medium">%{peak.pct} ({peak.label})</span>
              </div>
            )}
            {current !== null && (
              <div>
                <span className="text-gray-500 text-xs block">Şu an</span>
                <span className="font-medium" style={{ color: DENSITY_COLOR[zone.densityLevel] }}>
                  %{current}
                </span>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  )
}

/** Yönlendirme Modalı */
function RedirectModal({ zone, zones, onClose }) {
  const [toZoneId, setToZoneId] = useState('')
  const [message, setMessage]   = useState(`${zone.zoneName} yoğun — lütfen alternatif bölgeye geçin.`)
  const [sending, setSending]   = useState(false)

  // Aynı zone_type'a sahip, kaynak dışındaki tüm bölgeler
  const alternatives = zones.filter(z =>
    z.zoneType === zone.zoneType &&
    z.zoneId !== zone.zoneId
  )

  async function handleSend() {
    if (!toZoneId) { toast.error('Hedef bölge seçin'); return }
    setSending(true)
    try {
      await occupancyApi.redirect({ fromZoneId: zone.zoneId, toZoneId: Number(toZoneId), message })
      toast.success(`Yönlendirme gönderildi → ${zones.find(z => z.zoneId === Number(toZoneId))?.zoneName}`)
      onClose()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Yönlendirme başarısız')
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
      <div className="bg-gray-800 border border-gray-700 rounded-xl p-6 w-full max-w-md shadow-2xl">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-white font-bold">Yolcu Yönlendir</h3>
          <button onClick={onClose} className="text-gray-500 hover:text-gray-300">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Kaynak bölge */}
        <div className="mb-4 p-3 rounded-lg bg-red-500/10 border border-red-500/30">
          <p className="text-xs text-gray-400 mb-0.5">Yoğun Bölge (kaynak)</p>
          <p className="text-white font-semibold">{zone.zoneName}</p>
          <p className="text-red-400 text-xs mt-0.5">
            %{((zone.densityPct ?? 0) * 100).toFixed(0)} doluluk · {zone.currentCount ?? 0} kişi
          </p>
        </div>

        {/* Hedef bölge */}
        <div className="mb-4">
          <label className="text-xs text-gray-400 mb-1 block">Hedef Bölge</label>
          {alternatives.length === 0 ? (
            <p className="text-yellow-400 text-sm">Bu alan türünde alternatif bölge bulunmuyor.</p>
          ) : (
            <select
              value={toZoneId}
              onChange={e => setToZoneId(e.target.value)}
              className="w-full bg-gray-700 border border-gray-600 text-gray-200 text-sm rounded-lg px-3 py-2
                         focus:outline-none focus:border-eco-green/50"
            >
              <option value="">Seçin...</option>
              {alternatives.map(z => (
                <option key={z.zoneId} value={z.zoneId}>
                  {z.zoneName} (%{((z.densityPct ?? 0) * 100).toFixed(0)} dolu)
                </option>
              ))}
            </select>
          )}
        </div>

        {/* Mesaj */}
        <div className="mb-5">
          <label className="text-xs text-gray-400 mb-1 block">Yolcu Mesajı</label>
          <textarea
            value={message}
            onChange={e => setMessage(e.target.value)}
            rows={3}
            className="w-full bg-gray-700 border border-gray-600 text-gray-200 text-sm rounded-lg px-3 py-2
                       focus:outline-none focus:border-eco-green/50 resize-none"
          />
        </div>

        <div className="flex gap-3 justify-end">
          <button onClick={onClose}
            className="px-4 py-2 rounded-lg bg-gray-700 text-gray-300 text-sm hover:bg-gray-600 transition-colors">
            İptal
          </button>
          <button
            onClick={handleSend}
            disabled={sending || alternatives.length === 0}
            className="px-4 py-2 rounded-lg bg-eco-green text-gray-900 text-sm font-semibold
                       hover:bg-eco-green/90 transition-colors disabled:opacity-50"
          >
            {sending ? 'Gönderiliyor...' : 'Yönlendir'}
          </button>
        </div>
      </div>
    </div>
  )
}

export default function OccupancyManagement() {
  const [zones, setZones]               = useState([])
  const [selectedZoneId, setSelectedZoneId] = useState(null)
  const [loading, setLoading]           = useState(true)
  const [error, setError]               = useState(null)
  const [redirectZone, setRedirectZone] = useState(null)
  const isMounted = useRef(true)

  const fetchZones = useCallback(async () => {
    try {
      const res = await occupancyApi.getHeatmap()
      const data = res.data.data?.zones ?? []
      if (isMounted.current) {
        setZones(data)
        setError(null)
      }
    } catch (err) {
      if (isMounted.current) setError(err.response?.data?.message || 'Veri alınamadı')
    } finally {
      if (isMounted.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    isMounted.current = true
    fetchZones()
    const id = setInterval(fetchZones, 15_000)
    return () => { isMounted.current = false; clearInterval(id) }
  }, [fetchZones])

  function toggleZone(zoneId) {
    setSelectedZoneId(prev => prev === zoneId ? null : zoneId)
  }

  const selectedZone = zones.find(z => z.zoneId === selectedZoneId)

  const barData = zones.map(z => ({
    name:  z.zoneName.length > 9 ? z.zoneName.slice(0, 9) + '…' : z.zoneName,
    pct:   parseFloat(((z.densityPct ?? 0) * 100).toFixed(1)),
    level: z.densityLevel,
  }))

  const criticalCount = zones.filter(z => z.densityLevel === 'CRITICAL').length
  const highCount     = zones.filter(z => z.densityLevel === 'HIGH').length
  const totalPeople   = zones.reduce((a, z) => a + (z.currentCount ?? 0), 0)

  if (loading) return (
    <div className="flex-1 p-6 animate-pulse space-y-6">
      <div className="h-8 w-52 bg-gray-700 rounded" />
      <div className="bg-gray-800 h-48 rounded-xl border border-gray-700" />
    </div>
  )

  return (
    <div className="flex-1 p-6 space-y-6 overflow-auto">
      {/* Redirect Modal */}
      {redirectZone && (
        <RedirectModal
          zone={redirectZone}
          zones={zones}
          onClose={() => setRedirectZone(null)}
        />
      )}

      {/* Başlık */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Yoğunluk Yönetimi</h1>
          <p className="text-gray-400 text-sm mt-0.5">Anlık bölge dolulukları — 15 sn otomatik güncelleme</p>
        </div>
        <button onClick={fetchZones}
          className="p-2 rounded-lg bg-gray-800 border border-gray-700 text-gray-400
                     hover:border-eco-green/50 transition-colors">
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
        </button>
      </div>

      {/* Mini KPI'lar */}
      <div className="grid grid-cols-3 gap-3">
        <div className="bg-gray-800 rounded-xl p-3 border border-gray-700 text-center">
          <p className="text-2xl font-bold text-white">{totalPeople}</p>
          <p className="text-xs text-gray-400">Toplam Yolcu</p>
        </div>
        <div className={`rounded-xl p-3 border text-center ${criticalCount > 0 ? 'bg-red-500/10 border-red-500/30' : 'bg-gray-800 border-gray-700'}`}>
          <p className={`text-2xl font-bold ${criticalCount > 0 ? 'text-red-400' : 'text-white'}`}>{criticalCount}</p>
          <p className="text-xs text-gray-400">Kritik Bölge</p>
        </div>
        <div className={`rounded-xl p-3 border text-center ${highCount > 0 ? 'bg-orange-500/10 border-orange-500/30' : 'bg-gray-800 border-gray-700'}`}>
          <p className={`text-2xl font-bold ${highCount > 0 ? 'text-orange-400' : 'text-white'}`}>{highCount}</p>
          <p className="text-xs text-gray-400">Yüksek Bölge</p>
        </div>
      </div>

      {error && (
        <div className="px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-400 text-sm">{error}</div>
      )}

      {/* BarChart — Anlık Bölge Dolulukları */}
      <div className="bg-gray-800 rounded-xl p-4 border border-gray-700">
        <h2 className="text-white font-semibold mb-4">Anlık Bölge Dolulukları (%)</h2>
        <ResponsiveContainer width="100%" height={220}>
          <BarChart data={barData} margin={{ top: 5, right: 10, left: -10, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
            <XAxis dataKey="name" tick={{ fill: '#9CA3AF', fontSize: 11 }} tickLine={false} />
            <YAxis tick={{ fill: '#9CA3AF', fontSize: 11 }} tickLine={false} axisLine={false}
                   domain={[0, 100]} tickFormatter={v => `${v}%`} />
            <Tooltip
              contentStyle={{ backgroundColor: '#1F2937', border: '1px solid #374151', borderRadius: '0.5rem', fontSize: '12px' }}
              labelStyle={{ color: '#F9FAFB' }}
              formatter={v => [`%${v}`, 'Doluluk']}
            />
            <Bar dataKey="pct" radius={[4, 4, 0, 0]}>
              {barData.map((entry, i) => (
                <Cell key={i} fill={DENSITY_COLOR[entry.level] ?? '#9CA3AF'} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
        <div className="flex gap-4 mt-2 flex-wrap">
          {Object.entries(DENSITY_COLOR).map(([l, c]) => (
            <div key={l} className="flex items-center gap-1.5">
              <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: c }} />
              <span className="text-xs text-gray-400">{DENSITY_LABEL[l]}</span>
            </div>
          ))}
        </div>
      </div>

      {/* 24 Saatlik Geçmiş Paneli (seçili zone varsa açılır) */}
      {selectedZone && (
        <ZoneHistoryPanel
          zone={selectedZone}
          onClose={() => setSelectedZoneId(null)}
        />
      )}

      {/* Bölge Tablosu */}
      <div className="bg-gray-800 rounded-xl border border-gray-700 overflow-hidden">
        <div className="p-4 border-b border-gray-700 flex items-center justify-between">
          <h2 className="text-white font-semibold">Bölge Tablosu</h2>
          <p className="text-xs text-gray-500">
            Satıra tıklayın → 24s geçmiş grafiği · "Yönlendir" — yolcu yönlendirme
          </p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-gray-400 text-left border-b border-gray-700">
                <th className="px-4 py-2.5 font-medium">Bölge</th>
                <th className="px-4 py-2.5 font-medium">Tür</th>
                <th className="px-4 py-2.5 font-medium text-right">Kapasite</th>
                <th className="px-4 py-2.5 font-medium text-right">Mevcut</th>
                <th className="px-4 py-2.5 font-medium text-right">Doluluk%</th>
                <th className="px-4 py-2.5 font-medium text-right">Seviye</th>
                <th className="px-4 py-2.5 font-medium text-right">Durum</th>
                <th className="px-4 py-2.5 font-medium text-right">İşlem</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-700/50">
              {zones.map(z => (
                <tr
                  key={z.zoneId}
                  className={`transition-colors cursor-pointer ${rowBg(z.densityLevel)}
                    ${selectedZoneId === z.zoneId ? 'ring-1 ring-inset ring-eco-green/30' : ''}`}
                  onClick={() => toggleZone(z.zoneId)}
                >
                  <td className="px-4 py-3 text-white font-medium">{z.zoneName}</td>
                  <td className="px-4 py-3 text-gray-400 text-xs">{z.zoneType ?? z.type}</td>
                  <td className="px-4 py-3 text-right text-gray-300">{z.maxCapacity}</td>
                  <td className="px-4 py-3 text-right text-gray-300">{z.currentCount ?? 0}</td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex items-center justify-end gap-2">
                      <div className="w-16 h-1.5 bg-gray-700 rounded-full overflow-hidden">
                        <div className="h-full rounded-full"
                          style={{
                            width: `${Math.min((z.densityPct ?? 0) * 100, 100)}%`,
                            backgroundColor: DENSITY_COLOR[z.densityLevel] ?? '#9CA3AF',
                          }} />
                      </div>
                      <span className="text-white w-9 text-right">
                        {((z.densityPct ?? 0) * 100).toFixed(0)}%
                      </span>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <span className="text-xs font-medium px-2 py-0.5 rounded-full"
                      style={{
                        color: DENSITY_COLOR[z.densityLevel],
                        backgroundColor: `${DENSITY_COLOR[z.densityLevel]}20`,
                        border: `1px solid ${DENSITY_COLOR[z.densityLevel]}40`,
                      }}>
                      {DENSITY_LABEL[z.densityLevel] ?? z.densityLevel}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right">
                    {(z.densityPct ?? 0) >= (z.criticalThreshold ?? 0.85) ? (
                      <span className="text-xs text-red-400">Kritik Eşik Üstü</span>
                    ) : (z.densityPct ?? 0) >= 0.60 ? (
                      <span className="text-xs text-yellow-400">Yoğun</span>
                    ) : (
                      <span className="text-xs text-eco-green">Normal</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right" onClick={e => e.stopPropagation()}>
                    {(z.densityPct ?? 0) >= 0.60 ? (
                      <button
                        onClick={() => setRedirectZone(z)}
                        className="text-xs px-2.5 py-1 rounded-lg bg-eco-green/10 border border-eco-green/30
                                   text-eco-green hover:bg-eco-green/20 transition-colors"
                      >
                        Yönlendir
                      </button>
                    ) : (
                      <span className="text-xs text-gray-600">—</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
