import React, { useState } from 'react'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, Cell, LineChart, Line, Legend
} from 'recharts'
import KpiCard from '../../components/KpiCard'
import { useEnergy, useEnergyTrend } from '../../hooks/useEnergy'

const STATUS_COLORS = { WASTEFUL: '#E74C3C', EFFICIENT: '#2ECC71', NORMAL: '#9CA3AF' }
const STATUS_LABELS = { WASTEFUL: 'Savurgan', EFFICIENT: 'Verimli', NORMAL: 'Normal' }
const STATUS_BADGE  = {
  WASTEFUL: 'text-red-400 bg-red-500/10 border border-red-500/30',
  EFFICIENT:'text-eco-green bg-eco-green/10 border border-eco-green/30',
  NORMAL:   'text-gray-400 bg-gray-700/50 border border-gray-600',
}

export default function EnergyManagement() {
  const { usage, savings, loadingUsage, error } = useEnergy()
  const [selectedZoneId, setSelectedZoneId]     = useState(null)
  const [trendHours, setTrendHours]             = useState(6)

  const zoneId = selectedZoneId ?? usage[0]?.zoneId
  const { data: trend } = useEnergyTrend(zoneId, trendHours)

  if (loadingUsage) return <EnergySkeleton />

  const wastefulZones = usage.filter(z => z.efficiencyStatus === 'WASTEFUL')
  const totalKwh      = usage.reduce((a, z) => a + (z.energyKwh ?? 0), 0)
  const savingPct     = savings.length > 0
    ? Math.round(savings.reduce((a, s) => a + (s.potentialSavingPct ?? 0), 0) / savings.length)
    : 0

  const barData = usage.map(z => ({
    name:   z.zoneName.length > 8 ? z.zoneName.slice(0, 8) + '…' : z.zoneName,
    kwh:    parseFloat((z.energyKwh ?? 0).toFixed(1)),
    status: z.efficiencyStatus,
  }))

  const trendFormatted = trend.map(p => ({
    time: new Date(p.timestamp).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' }),
    kwh:  parseFloat((p.energyKwh ?? 0).toFixed(2)),
    temp: parseFloat((p.temp ?? 0).toFixed(1)),
  }))

  return (
    <div className="flex-1 p-6 space-y-6 overflow-auto">
      {/* Başlık */}
      <div>
        <h1 className="text-2xl font-bold text-white">Enerji Yönetimi</h1>
        <p className="text-gray-400 text-sm mt-0.5">Bölgesel enerji tüketimi ve verimlilik analizi</p>
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

      {/* BarChart: bölge bazlı enerji */}
      <div className="bg-gray-800 rounded-xl p-4 border border-gray-700">
        <h2 className="text-white font-semibold mb-4">Bölge Bazlı Enerji Karşılaştırması (kWh)</h2>
        <ResponsiveContainer width="100%" height={200}>
          <BarChart data={barData} margin={{ top: 5, right: 10, left: -10, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
            <XAxis dataKey="name" tick={{ fill: '#9CA3AF', fontSize: 11 }} tickLine={false} />
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
        {/* Renk açıklaması */}
        <div className="flex gap-4 mt-2">
          {Object.entries(STATUS_COLORS).map(([s, c]) => (
            <div key={s} className="flex items-center gap-1.5">
              <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: c }} />
              <span className="text-xs text-gray-400">{STATUS_LABELS[s]}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Tablo */}
      <div className="bg-gray-800 rounded-xl border border-gray-700 overflow-hidden">
        <div className="p-4 border-b border-gray-700">
          <h2 className="text-white font-semibold">Bölge Detay Tablosu</h2>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-gray-400 text-left border-b border-gray-700">
                <th className="px-4 py-2.5 font-medium">Bölge</th>
                <th className="px-4 py-2.5 font-medium text-right">Doluluk</th>
                <th className="px-4 py-2.5 font-medium text-right">kWh</th>
                <th className="px-4 py-2.5 font-medium text-right">Sıcaklık</th>
                <th className="px-4 py-2.5 font-medium text-right">Aydınlatma</th>
                <th className="px-4 py-2.5 font-medium text-right">Durum</th>
                <th className="px-4 py-2.5 font-medium">Öneri</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-700/50">
              {usage.map(z => {
                const suggestion = savings.find(s => s.zoneId === z.zoneId)
                const isWasteful = z.efficiencyStatus === 'WASTEFUL'
                return (
                  <tr
                    key={z.zoneId}
                    className={`transition-colors cursor-pointer ${
                      isWasteful ? 'bg-yellow-500/5 hover:bg-yellow-500/10' : 'hover:bg-gray-700/30'
                    } ${selectedZoneId === z.zoneId ? 'ring-1 ring-inset ring-eco-green/30' : ''}`}
                    onClick={() => setSelectedZoneId(z.zoneId)}
                  >
                    <td className="px-4 py-3 text-white font-medium">{z.zoneName}</td>
                    <td className="px-4 py-3 text-right text-gray-300">
                      %{((z.densityPct ?? 0) * 100).toFixed(0)}
                    </td>
                    <td className="px-4 py-3 text-right text-gray-300">
                      {(z.energyKwh ?? 0).toFixed(1)}
                    </td>
                    <td className="px-4 py-3 text-right text-gray-300">
                      {z.temp != null ? `${z.temp.toFixed(1)}°C` : '—'}
                    </td>
                    <td className="px-4 py-3 text-right text-gray-300">
                      {z.lightingLux != null ? `${z.lightingLux} lux` : '—'}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <span className={`text-xs px-2 py-0.5 rounded-full ${STATUS_BADGE[z.efficiencyStatus]}`}>
                        {STATUS_LABELS[z.efficiencyStatus] ?? z.efficiencyStatus}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-400 max-w-xs truncate">
                      {suggestion?.suggestion ?? '—'}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* Trend Grafiği */}
      <div className="bg-gray-800 rounded-xl p-4 border border-gray-700">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="text-white font-semibold">
              {usage.find(z => z.zoneId === zoneId)?.zoneName ?? '—'} — Enerji Trendi
            </h2>
            <p className="text-gray-500 text-xs mt-0.5">Satıra tıklayarak bölge seçin</p>
          </div>
          <div className="flex gap-2">
            {[3, 6, 12].map(h => (
              <button key={h} onClick={() => setTrendHours(h)}
                className={`text-xs px-2.5 py-1 rounded-lg transition-colors ${
                  trendHours === h
                    ? 'bg-eco-green/20 text-eco-green border border-eco-green/40'
                    : 'bg-gray-700 text-gray-400 border border-gray-600 hover:border-gray-500'
                }`}>{h}s</button>
            ))}
          </div>
        </div>
        {trendFormatted.length === 0 ? (
          <p className="text-center text-gray-500 text-sm py-8">Trend verisi bulunamadı.</p>
        ) : (
          <ResponsiveContainer width="100%" height={200}>
            <LineChart data={trendFormatted} margin={{ top: 5, right: 10, left: -10, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis dataKey="time" tick={{ fill: '#9CA3AF', fontSize: 10 }} tickLine={false} interval="preserveStartEnd" />
              <YAxis yAxisId="kwh" tick={{ fill: '#9CA3AF', fontSize: 10 }} tickLine={false} axisLine={false} />
              <YAxis yAxisId="temp" orientation="right" tick={{ fill: '#9CA3AF', fontSize: 10 }} tickLine={false} axisLine={false} />
              <Tooltip
                contentStyle={{ backgroundColor: '#1F2937', border: '1px solid #374151', borderRadius: '0.5rem', fontSize: '11px' }}
                labelStyle={{ color: '#F9FAFB', marginBottom: 4 }}
              />
              <Legend wrapperStyle={{ fontSize: '11px', color: '#9CA3AF' }} />
              <Line yAxisId="kwh" type="monotone" dataKey="kwh" name="Enerji (kWh)"
                stroke="#F39C12" strokeWidth={2} dot={false} activeDot={{ r: 3 }} />
              <Line yAxisId="temp" type="monotone" dataKey="temp" name="Sıcaklık (°C)"
                stroke="#3B82F6" strokeWidth={2} dot={false} activeDot={{ r: 3 }} />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>

      {/* Tasarruf Önerileri Kartları */}
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
