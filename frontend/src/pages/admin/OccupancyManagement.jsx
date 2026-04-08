import React, { useState, useEffect, useCallback, useRef } from 'react'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell,
  LineChart, Line, ReferenceLine, Legend
} from 'recharts'
import { occupancyApi } from '../../api/occupancyApi'

const DENSITY_COLOR = {
  LOW:      '#2ECC71',
  MEDIUM:   '#F39C12',
  HIGH:     '#E67E22',
  CRITICAL: '#E74C3C',
}
const DENSITY_LABEL = { LOW: 'Düşük', MEDIUM: 'Orta', HIGH: 'Yüksek', CRITICAL: 'Kritik' }

export default function OccupancyManagement() {
  const [zones, setZones]           = useState([])
  const [trendZoneId, setTrendZoneId] = useState(null)
  const [trend, setTrend]           = useState([])
  const [loading, setLoading]       = useState(true)
  const [error, setError]           = useState(null)
  const isMounted = useRef(true)

  const fetchZones = useCallback(async () => {
    try {
      const res = await occupancyApi.getHeatmap()
      const data = res.data.data?.zones ?? []
      if (isMounted.current) {
        setZones(data)
        if (!trendZoneId && data.length > 0) setTrendZoneId(data[0].zoneId)
        setError(null)
      }
    } catch (err) {
      if (isMounted.current) setError(err.response?.data?.message || 'Veri alınamadı')
    } finally {
      if (isMounted.current) setLoading(false)
    }
  }, [trendZoneId])

  useEffect(() => {
    isMounted.current = true
    fetchZones()
    const id = setInterval(fetchZones, 15_000)
    return () => { isMounted.current = false; clearInterval(id) }
  }, [fetchZones])

  const selectedZone = zones.find(z => z.zoneId === trendZoneId)

  // Trend: anlık değerden tek nokta oluştur
  const trendData = selectedZone
    ? [{ time: 'Şimdi', pct: parseFloat(((selectedZone.densityPct ?? 0) * 100).toFixed(1)) }]
    : []

  const barData = zones.map(z => ({
    name:  z.zoneName.length > 9 ? z.zoneName.slice(0, 9) + '…' : z.zoneName,
    pct:   parseFloat(((z.densityPct ?? 0) * 100).toFixed(1)),
    level: z.densityLevel,
  }))

  if (loading) return (
    <div className="flex-1 p-6 animate-pulse space-y-6">
      <div className="h-8 w-52 bg-gray-700 rounded" />
      <div className="bg-gray-800 h-48 rounded-xl border border-gray-700" />
    </div>
  )

  return (
    <div className="flex-1 p-6 space-y-6 overflow-auto">
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

      {error && (
        <div className="px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-400 text-sm">{error}</div>
      )}

      {/* BarChart: anlık doluluklar */}
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

      {/* LineChart: seçili bölge trendi + ReferenceLine */}
      <div className="bg-gray-800 rounded-xl p-4 border border-gray-700">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-white font-semibold">Bölge Doluluk Detayı</h2>
          <select
            value={trendZoneId ?? ''}
            onChange={e => setTrendZoneId(Number(e.target.value))}
            className="bg-gray-700 border border-gray-600 text-gray-300 text-sm rounded-lg px-3 py-1.5
                       focus:outline-none focus:border-eco-green/50"
          >
            {zones.map(z => (
              <option key={z.zoneId} value={z.zoneId}>{z.zoneName}</option>
            ))}
          </select>
        </div>

        {selectedZone && (
          <div className="flex items-center gap-6 mb-4 text-sm">
            <div>
              <span className="text-gray-500 text-xs">Mevcut Doluluk</span>
              <p className="font-bold" style={{ color: DENSITY_COLOR[selectedZone.densityLevel] }}>
                %{((selectedZone.densityPct ?? 0) * 100).toFixed(0)}
              </p>
            </div>
            <div>
              <span className="text-gray-500 text-xs">Seviye</span>
              <p className="font-medium text-white">{DENSITY_LABEL[selectedZone.densityLevel]}</p>
            </div>
            <div>
              <span className="text-gray-500 text-xs">Kritik Eşik</span>
              <p className="font-medium text-red-400">
                %{((selectedZone.criticalThreshold ?? 0.85) * 100).toFixed(0)}
              </p>
            </div>
            <div>
              <span className="text-gray-500 text-xs">Kapasite</span>
              <p className="font-medium text-white">{selectedZone.currentCount} / {selectedZone.maxCapacity}</p>
            </div>
          </div>
        )}

        <ResponsiveContainer width="100%" height={160}>
          <LineChart data={trendData} margin={{ top: 5, right: 10, left: -10, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
            <XAxis dataKey="time" tick={{ fill: '#9CA3AF', fontSize: 11 }} tickLine={false} />
            <YAxis tick={{ fill: '#9CA3AF', fontSize: 11 }} tickLine={false} axisLine={false}
                   domain={[0, 100]} tickFormatter={v => `${v}%`} />
            <Tooltip
              contentStyle={{ backgroundColor: '#1F2937', border: '1px solid #374151', borderRadius: '0.5rem', fontSize: '12px' }}
              formatter={v => [`%${v}`, 'Doluluk']}
            />
            {/* Kritik eşik — kırmızı yatay çizgi */}
            {selectedZone && (
              <ReferenceLine
                y={(selectedZone.criticalThreshold ?? 0.85) * 100}
                stroke="#E74C3C"
                strokeDasharray="4 2"
                label={{ value: 'Kritik', fill: '#E74C3C', fontSize: 11, position: 'right' }}
              />
            )}
            <Line type="monotone" dataKey="pct" name="Doluluk"
              stroke={DENSITY_COLOR[selectedZone?.densityLevel] ?? '#2ECC71'}
              strokeWidth={2} dot={{ r: 4 }} activeDot={{ r: 6 }} />
          </LineChart>
        </ResponsiveContainer>
      </div>

      {/* Bölge Tablosu */}
      <div className="bg-gray-800 rounded-xl border border-gray-700 overflow-hidden">
        <div className="p-4 border-b border-gray-700">
          <h2 className="text-white font-semibold">Bölge Tablosu</h2>
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
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-700/50">
              {zones.map(z => (
                <tr
                  key={z.zoneId}
                  className="hover:bg-gray-700/30 transition-colors cursor-pointer"
                  onClick={() => setTrendZoneId(z.zoneId)}
                >
                  <td className="px-4 py-3 text-white font-medium">{z.zoneName}</td>
                  <td className="px-4 py-3 text-gray-400 text-xs">{z.type}</td>
                  <td className="px-4 py-3 text-right text-gray-300">{z.maxCapacity}</td>
                  <td className="px-4 py-3 text-right text-gray-300">{z.currentCount ?? 0}</td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex items-center justify-end gap-2">
                      <div className="w-16 h-1.5 bg-gray-700 rounded-full overflow-hidden">
                        <div className="h-full rounded-full"
                          style={{ width: `${Math.min((z.densityPct ?? 0) * 100, 100)}%`,
                                   backgroundColor: DENSITY_COLOR[z.densityLevel] ?? '#9CA3AF' }} />
                      </div>
                      <span className="text-white w-9 text-right">
                        {((z.densityPct ?? 0) * 100).toFixed(0)}%
                      </span>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <span className="text-xs font-medium px-2 py-0.5 rounded-full"
                      style={{ color: DENSITY_COLOR[z.densityLevel],
                               backgroundColor: `${DENSITY_COLOR[z.densityLevel]}20`,
                               border: `1px solid ${DENSITY_COLOR[z.densityLevel]}40` }}>
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
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
