import React, { useState, useEffect } from 'react'
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
} from 'recharts'
import { adminApi } from '../../api/adminApi'

const TABS = [
  { key: 'occupancy', label: 'Yoğunluk Raporu', color: '#2ECC71', unit: '%' },
  { key: 'energy',    label: 'Enerji Raporu',   color: '#F39C12', unit: 'kWh' },
]

function toDateInput(d) {
  return d.toISOString().slice(0, 10)
}

export default function ReportsPage() {
  const [tab, setTab]         = useState('occupancy')
  const [date, setDate]       = useState(toDateInput(new Date()))
  const [data, setData]       = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError]     = useState(null)

  const fetchReport = async () => {
    setLoading(true)
    setError(null)
    try {
      const fn = tab === 'occupancy' ? adminApi.getOccupancyReport : adminApi.getEnergyReport
      const res = await fn(date)
      const raw = res.data.data ?? []
      // Tüm 24 saati doldur — veri yoksa 0
      const byHour = Object.fromEntries(raw.map(p => [p.hour, p.value]))
      setData(
        Array.from({ length: 24 }, (_, h) => ({
          label: `${String(h).padStart(2, '0')}:00`,
          value: tab === 'occupancy'
            ? parseFloat(((byHour[h] ?? 0) * 100).toFixed(1))  // 0-1 → %
            : parseFloat((byHour[h] ?? 0).toFixed(1)),
        }))
      )
    } catch (err) {
      setError(err.response?.data?.message || 'Rapor alınamadı')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchReport() }, [tab, date])

  const activeTab = TABS.find(t => t.key === tab)

  return (
    <div className="flex-1 p-6 space-y-6 overflow-auto">
      {/* Başlık */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-white">Raporlar</h1>
          <p className="text-gray-400 text-sm mt-0.5">Günlük yoğunluk ve enerji analizi</p>
        </div>
        {/* Tarih seçici */}
        <input
          type="date"
          value={date}
          max={toDateInput(new Date())}
          onChange={e => setDate(e.target.value)}
          className="bg-gray-800 border border-gray-700 text-gray-300 text-sm rounded-lg px-3 py-1.5
                     focus:outline-none focus:border-eco-green/50"
        />
      </div>

      {/* Tab seçimi */}
      <div className="flex gap-2">
        {TABS.map(t => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              tab === t.key
                ? 'bg-eco-green/20 text-eco-green border border-eco-green/40'
                : 'bg-gray-800 text-gray-400 border border-gray-700 hover:border-gray-600'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {error && (
        <div className="px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-400 text-sm">
          {error}
        </div>
      )}

      {/* AreaChart */}
      <div className="bg-gray-800 rounded-xl p-4 border border-gray-700">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="text-white font-semibold">{activeTab.label}</h2>
            <p className="text-gray-500 text-xs mt-0.5">
              {date} — saatlik {tab === 'occupancy' ? 'ortalama doluluk (%)' : 'toplam enerji (kWh)'}
            </p>
          </div>
          {loading && (
            <div className="w-4 h-4 border-2 border-eco-green border-t-transparent rounded-full animate-spin" />
          )}
        </div>

        <ResponsiveContainer width="100%" height={280}>
          <AreaChart data={data} margin={{ top: 10, right: 10, left: -5, bottom: 5 }}>
            <defs>
              <linearGradient id="areaGrad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%"  stopColor={activeTab.color} stopOpacity={0.3} />
                <stop offset="95%" stopColor={activeTab.color} stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
            <XAxis
              dataKey="label"
              tick={{ fill: '#9CA3AF', fontSize: 10 }}
              tickLine={false}
              interval={2}
            />
            <YAxis
              tick={{ fill: '#9CA3AF', fontSize: 11 }}
              tickLine={false}
              axisLine={false}
              tickFormatter={v => `${v}${activeTab.unit}`}
            />
            <Tooltip
              contentStyle={{
                backgroundColor: '#1F2937',
                border: '1px solid #374151',
                borderRadius: '0.5rem',
                fontSize: '12px',
              }}
              labelStyle={{ color: '#F9FAFB', marginBottom: 4 }}
              formatter={v => [`${v} ${activeTab.unit}`, activeTab.label]}
            />
            <Area
              type="monotone"
              dataKey="value"
              stroke={activeTab.color}
              strokeWidth={2}
              fill="url(#areaGrad)"
              dot={false}
              activeDot={{ r: 4, fill: activeTab.color }}
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>

      {/* Özet istatistikler */}
      {data.length > 0 && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          {[
            { label: 'Ortalama', value: (data.reduce((a, d) => a + d.value, 0) / data.length).toFixed(1) },
            { label: 'Maksimum', value: Math.max(...data.map(d => d.value)).toFixed(1) },
            { label: 'Minimum', value: Math.min(...data.map(d => d.value)).toFixed(1) },
            { label: 'Toplam', value: data.reduce((a, d) => a + d.value, 0).toFixed(1) },
          ].map(s => (
            <div key={s.label} className="bg-gray-800 rounded-xl p-4 border border-gray-700">
              <p className="text-gray-400 text-xs mb-1">{s.label}</p>
              <p className="text-xl font-bold text-white">
                {s.value}
                <span className="text-sm text-gray-500 ml-1">{activeTab.unit}</span>
              </p>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
