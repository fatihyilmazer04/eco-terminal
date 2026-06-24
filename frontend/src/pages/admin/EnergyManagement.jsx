import React, { useState } from 'react'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, Cell
} from 'recharts'
import KpiCard from '../../components/KpiCard'
import { useEnergy } from '../../hooks/useEnergy'
import { energyApi } from '../../api/adminApi'
import toast from 'react-hot-toast'

const STATUS_COLORS = { WASTEFUL: '#E74C3C', EFFICIENT: '#2ECC71', NORMAL: '#9CA3AF' }
const STATUS_LABELS = { WASTEFUL: 'Savurgan', EFFICIENT: 'Verimli', NORMAL: 'Normal' }
const STATUS_BADGE  = {
  WASTEFUL: 'text-red-400 bg-red-500/10 border border-red-500/30',
  EFFICIENT:'text-eco-green bg-eco-green/10 border border-eco-green/30',
  NORMAL:   'text-gray-400 bg-gray-700/50 border border-gray-600',
}

const TEMP_MIN = 16
const TEMP_MAX = 30
const LUX_MIN  = 100
const LUX_MAX  = 1000

function getRecommendation(zone) {
  const d = zone.densityPct ?? 0
  const t = zone.temperature ?? zone.temp ?? 22
  const l = zone.lightingLevel ?? zone.lightingLux ?? 400

  if (d < 0.20) {
    if (l > 400) return 'Bölge boş, aydınlatmayı azalt (200 Lux)'
    if (t > 23)  return 'Bölge boş, sıcaklığı düşür (20°C)'
    return 'Bölge boş, enerji tasarrufu moduna al'
  }
  if (d < 0.50) {
    if (l > 600) return 'Aydınlatmayı kıs (400 Lux)'
    if (t > 25)  return 'Sıcaklığı düşür (22°C)'
    return 'Normal — önlem gerekmez'
  }
  if (d >= 0.85) {
    if (t < 21)  return 'Kalabalık bölge, sıcaklığı artır (23°C)'
    if (l < 400) return 'Kalabalık bölge, aydınlatmayı artır (600 Lux)'
    return 'Yoğun bölge — izlemede tut'
  }
  return 'Normal — önlem gerekmez'
}

/** Inline sıcaklık/aydınlatma kontrolü */
function InlineControl({ zone, onUpdate }) {
  const [temp,   setTemp]   = useState(zone.temp   ?? 22)
  const [lux,    setLux]    = useState(zone.lightingLux ?? 400)
  const [saving, setSaving] = useState(false)

  async function applyChange(newTemp, newLux) {
    setSaving(true)
    try {
      await energyApi.updateSettings(zone.zoneId, {
        targetTemp:        newTemp,
        targetLightingLux: newLux,
      })
      onUpdate(zone.zoneId, { temp: newTemp, lightingLux: newLux })
      toast.success(`${zone.zoneName} ayarları güncellendi`)
    } catch (err) {
      toast.error(err.response?.data?.message || 'Güncelleme başarısız')
      // Optimistic geri al
      setTemp(zone.temp ?? 22)
      setLux(zone.lightingLux ?? 400)
    } finally {
      setSaving(false)
    }
  }

  function adjustTemp(delta) {
    if (delta < 0 && temp <= TEMP_MIN) {
      toast.error(`Uyarı: Minimum sıcaklık ${TEMP_MIN}°C. Daha fazla düşürmek konfor ve sağlık açısından uygun değil.`)
      return
    }
    if (delta > 0 && temp >= TEMP_MAX) {
      toast.error(`Uyarı: Maksimum sıcaklık ${TEMP_MAX}°C. Bu sıcaklık yolcu konforu için uygun değil.`)
      return
    }
    const newTemp = parseFloat((temp + delta).toFixed(1))
    setTemp(newTemp)
    applyChange(newTemp, lux)
  }

  function adjustLux(delta) {
    if (delta < 0 && lux <= LUX_MIN) {
      toast.error(`Uyarı: Minimum aydınlatma ${LUX_MIN} Lux. Daha düşük değer güvenlik riski oluşturur.`)
      return
    }
    if (delta > 0 && lux >= LUX_MAX) {
      toast.error(`Uyarı: Maksimum aydınlatma ${LUX_MAX} Lux. Daha yüksek değer enerji israfına neden olur.`)
      return
    }
    const newLux = Math.max(LUX_MIN, Math.min(LUX_MAX, lux + delta))
    setLux(newLux)
    applyChange(temp, newLux)
  }

  return (
    <div className="flex items-center gap-3 text-xs" onClick={e => e.stopPropagation()}>
      {/* Sıcaklık */}
      <div className="flex items-center gap-1">
        <button
          disabled={saving || temp <= TEMP_MIN}
          onClick={() => adjustTemp(-0.5)}
          className="w-5 h-5 rounded bg-gray-600 hover:bg-gray-500 text-gray-300 flex items-center justify-center disabled:opacity-40"
        >-</button>
        <span className="text-gray-200 w-12 text-center">{temp.toFixed(1)}°C</span>
        <button
          disabled={saving || temp >= TEMP_MAX}
          onClick={() => adjustTemp(+0.5)}
          className="w-5 h-5 rounded bg-gray-600 hover:bg-gray-500 text-gray-300 flex items-center justify-center disabled:opacity-40"
        >+</button>
      </div>
      {/* Aydınlatma */}
      <div className="flex items-center gap-1">
        <button
          disabled={saving || lux <= LUX_MIN}
          onClick={() => adjustLux(-50)}
          className="w-5 h-5 rounded bg-gray-600 hover:bg-gray-500 text-gray-300 flex items-center justify-center disabled:opacity-40"
        >-</button>
        <span className="text-gray-200 w-14 text-center">{lux} lux</span>
        <button
          disabled={saving || lux >= LUX_MAX}
          onClick={() => adjustLux(+50)}
          className="w-5 h-5 rounded bg-gray-600 hover:bg-gray-500 text-gray-300 flex items-center justify-center disabled:opacity-40"
        >+</button>
      </div>
    </div>
  )
}

export default function EnergyManagement() {
  const { usage: rawUsage, savings, loadingUsage, error } = useEnergy()
  const [usage, setUsage] = useState(null) // optimistic override

  const effectiveUsage = usage ?? rawUsage

  function handleUpdate(zoneId, patch) {
    setUsage(prev => {
      const base = prev ?? rawUsage
      return base.map(z => z.zoneId === zoneId ? { ...z, ...patch } : z)
    })
  }

  if (loadingUsage) return <EnergySkeleton />

  const wastefulZones = effectiveUsage.filter(z => z.efficiencyStatus === 'WASTEFUL')
  const totalKwh      = effectiveUsage.reduce((a, z) => a + (z.energyKwh ?? 0), 0)
  const savingPct     = savings.length > 0
    ? Math.round(savings.reduce((a, s) => a + (s.potentialSavingPct ?? 0), 0) / savings.length)
    : 0

  const barData = effectiveUsage.map(z => ({
    name:   z.zoneName.length > 8 ? z.zoneName.slice(0, 8) + '…' : z.zoneName,
    kwh:    parseFloat((z.energyKwh ?? 0).toFixed(1)),
    status: z.efficiencyStatus,
  }))

  return (
    <div className="flex-1 p-6 space-y-6 overflow-auto">
      {/* Başlık */}
      <div>
        <h1 className="text-2xl font-bold text-white">Enerji Yönetimi</h1>
        <p className="text-gray-400 text-sm mt-0.5">Bölgesel enerji tüketimi, verimlilik analizi ve akıllı kontrol</p>
      </div>

      {error && (
        <div className="px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-400 text-sm">{error}</div>
      )}

      {/* 3 KPI */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <KpiCard title="Toplam Enerji" value={`${totalKwh.toFixed(1)} kWh`}
                 subtitle="tüm bölgeler toplamı" icon="⚡" color="orange" />
        <KpiCard title="Savurgan Bölge" value={wastefulZones.length}
                 subtitle="doluluk düşük, enerji yüksek" icon="🔴"
                 color={wastefulZones.length > 0 ? 'red' : 'green'} />
        <KpiCard title="Tasarruf Potansiyeli" value={`~%${savingPct}`}
                 subtitle={`${savings.length} öneri mevcut`} icon="💡"
                 color={savings.length > 0 ? 'yellow' : 'green'} />
      </div>

      {/* BarChart */}
      <div className="bg-gray-800 rounded-xl p-4 border border-gray-700">
        <h2 className="text-white font-semibold mb-4">Bölge Bazlı Enerji Karşılaştırması (kWh)</h2>
        <ResponsiveContainer width="100%" height={240}>
          <BarChart data={barData} margin={{ top: 5, right: 10, left: -10, bottom: 40 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
            <XAxis dataKey="name" tick={{ fill: '#9CA3AF', fontSize: 10, angle: -35, textAnchor: 'end' }} tickLine={false} interval={0} />
            <YAxis tick={{ fill: '#9CA3AF', fontSize: 11 }} tickLine={false} axisLine={false} />
            <Tooltip
              contentStyle={{ backgroundColor: '#1F2937', border: '1px solid #374151', borderRadius: '0.5rem', fontSize: '12px' }}
              labelStyle={{ color: '#F9FAFB' }}
              formatter={v => [`${v} kWh`, 'Enerji']}
            />
            <Bar dataKey="kwh" radius={[4, 4, 0, 0]}>
              {barData.map((entry, i) => (
                <Cell key={i} fill={STATUS_COLORS[entry.status] ?? '#9CA3AF'} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
        <div className="flex gap-4 mt-2">
          {Object.entries(STATUS_COLORS).map(([s, c]) => (
            <div key={s} className="flex items-center gap-1.5">
              <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: c }} />
              <span className="text-xs text-gray-400">{STATUS_LABELS[s]}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Bölge Detay Tablosu */}
      <div className="bg-gray-800 rounded-xl border border-gray-700 overflow-hidden">
        <div className="p-4 border-b border-gray-700 flex items-center justify-between">
          <h2 className="text-white font-semibold">Bölge Detay Tablosu</h2>
          <p className="text-xs text-gray-500">±0.5°C sıcaklık · ±50 lux aydınlatma ayarı</p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-gray-400 text-left border-b border-gray-700">
                <th className="px-4 py-2.5 font-medium">Bölge</th>
                <th className="px-4 py-2.5 font-medium text-right">Doluluk</th>
                <th className="px-4 py-2.5 font-medium text-right">kWh</th>
                <th className="px-4 py-2.5 font-medium text-right">Durum</th>
                <th className="px-4 py-2.5 font-medium">Sıcaklık / Aydınlatma Ayarı</th>
                <th className="px-4 py-2.5 font-medium">Öneri</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-700/50">
              {effectiveUsage.map(z => {
                const suggestion = savings.find(s => s.zoneId === z.zoneId)
                const isWasteful = z.efficiencyStatus === 'WASTEFUL'
                return (
                  <tr
                    key={z.zoneId}
                    className={`transition-colors ${
                      isWasteful ? 'bg-yellow-500/5 hover:bg-yellow-500/10' : 'hover:bg-gray-700/30'
                    }`}
                  >
                    <td className="px-4 py-3 text-white font-medium">{z.zoneName}</td>
                    <td className="px-4 py-3 text-right text-gray-300">
                      %{((z.densityPct ?? 0) * 100).toFixed(0)}
                    </td>
                    <td className="px-4 py-3 text-right text-gray-300">
                      {(z.energyKwh ?? 0).toFixed(1)}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <span className={`text-xs px-2 py-0.5 rounded-full ${STATUS_BADGE[z.efficiencyStatus]}`}>
                        {STATUS_LABELS[z.efficiencyStatus] ?? z.efficiencyStatus}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <InlineControl zone={z} onUpdate={handleUpdate} />
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-400 max-w-xs truncate">
                      {getRecommendation(z)}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* Tasarruf Önerileri */}
      {savings.length > 0 && (
        <div>
          <h2 className="text-white font-semibold mb-3">Tasarruf Önerileri</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {savings.map(s => (
              <div key={s.zoneId} className="bg-gray-800 rounded-xl p-4 border border-yellow-500/20">
                <div className="flex items-start justify-between mb-2">
                  <p className="text-yellow-300 font-medium text-sm">{s.zoneName}</p>
                  <span className="text-xs text-yellow-500 bg-yellow-500/10 px-2 py-0.5 rounded-full">
                    ~%{s.potentialSavingPct} tasarruf
                  </span>
                </div>
                <p className="text-gray-400 text-xs mb-2">
                  Doluluk: %{((s.currentDensity ?? 0) * 100).toFixed(0)} &nbsp;·&nbsp;
                  Enerji: {(s.currentEnergyKwh ?? 0).toFixed(1)} kWh
                </p>
                <p className="text-yellow-400 text-sm">{s.suggestion}</p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function EnergySkeleton() {
  return (
    <div className="flex-1 p-6 space-y-6 animate-pulse">
      <div className="h-8 w-48 bg-gray-700 rounded" />
      <div className="grid grid-cols-3 gap-4">
        {[1,2,3].map(i => <div key={i} className="bg-gray-800 rounded-xl h-20 border border-gray-700" />)}
      </div>
      <div className="bg-gray-800 rounded-xl h-56 border border-gray-700" />
    </div>
  )
}
