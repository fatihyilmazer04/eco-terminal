import React, { useState, useEffect, useCallback } from 'react'
import {
  AreaChart, Area, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, Legend
} from 'recharts'
import { adminApi } from '../../api/adminApi'

// ── Sabit tanımlar ──────────────────────────────────────────────────────────

const RANGES = [
  { key: 'today',   label: 'Bugün' },
  { key: 'week',    label: 'Bu Hafta' },
  { key: 'month',   label: 'Bu Ay' },
  { key: 'custom',  label: 'Özel Tarih' },
]

const TABS = [
  { key: 'occupancy', label: 'Yoğunluk',  color: '#2ECC71', unit: '%',   icon: '👥' },
  { key: 'energy',    label: 'Enerji',     color: '#F39C12', unit: 'kWh', icon: '⚡' },
  { key: 'users',     label: 'Kullanıcı',  color: '#3B82F6', unit: '',    icon: '👤' },
  { key: 'ai',        label: 'AI Özet',    color: '#8B5CF6', unit: '',    icon: '🔮' },
]

function toDateInput(d) {
  return d.toISOString().slice(0, 10)
}

function getRangeDate(range) {
  const now = new Date()
  if (range === 'today')  return toDateInput(now)
  if (range === 'week') {
    const d = new Date(now); d.setDate(d.getDate() - 7); return toDateInput(d)
  }
  if (range === 'month') {
    const d = new Date(now); d.setDate(d.getDate() - 30); return toDateInput(d)
  }
  return toDateInput(now)
}

// ── CSV Export ───────────────────────────────────────────────────────────────

function exportCSV(data, filename, columns) {
  const header = columns.map(c => c.label).join(',')
  const rows   = data.map(row => columns.map(c => row[c.key] ?? '').join(','))
  const csv    = [header, ...rows].join('\n')
  const blob   = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' })
  const url    = URL.createObjectURL(blob)
  const a      = document.createElement('a')
  a.href = url; a.download = filename; a.click()
  URL.revokeObjectURL(url)
}

// ── İstatistik özet ──────────────────────────────────────────────────────────

function StatsSummary({ data, unit }) {
  if (!data.length) return null
  const values = data.map(d => d.value ?? 0)
  const avg    = (values.reduce((a, v) => a + v, 0) / values.length).toFixed(1)
  const max    = Math.max(...values).toFixed(1)
  const min    = Math.min(...values).toFixed(1)
  const total  = values.reduce((a, v) => a + v, 0).toFixed(1)

  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
      {[
        { label: 'Ortalama', value: `${avg}${unit}` },
        { label: 'Maksimum', value: `${max}${unit}` },
        { label: 'Minimum',  value: `${min}${unit}` },
        { label: 'Toplam',   value: `${total}${unit}` },
      ].map(s => (
        <div key={s.label} className="bg-gray-800 rounded-xl p-4 border border-gray-700">
          <p className="text-gray-400 text-xs mb-1">{s.label}</p>
          <p className="text-xl font-bold text-white">{s.value}</p>
        </div>
      ))}
    </div>
  )
}

// ── Kullanıcı & AI Tab placeholder içeriği ──────────────────────────────────

function UsersTab() {
  return (
    <div className="bg-gray-800 rounded-xl p-8 border border-gray-700 text-center">
      <p className="text-4xl mb-4">👤</p>
      <p className="text-white font-semibold mb-1">Kullanıcı Aktivite Raporu</p>
      <p className="text-gray-500 text-sm">Kayıtlı kullanıcı sayısı, aktif oturum ve rol bazlı dağılım.</p>
      <p className="text-gray-600 text-xs mt-4">Bu bölüm geliştirilmeye devam ediyor.</p>
    </div>
  )
}

function AITab() {
  return (
    <div className="bg-gray-800 rounded-xl p-8 border border-gray-700 text-center">
      <p className="text-4xl mb-4">🔮</p>
      <p className="text-white font-semibold mb-1">AI Tahmin Özeti</p>
      <p className="text-gray-500 text-sm">Model doğruluk istatistikleri, risk seviyesi dağılımı ve tahmin güven eğrileri.</p>
      <p className="text-gray-600 text-xs mt-4">Bu bölüm geliştirilmeye devam ediyor.</p>
    </div>
  )
}

// ── Ana Bileşen ───────────────────────────────────────────────────────────────

export default function ReportsPage() {
  const [tab,      setTab]     = useState('occupancy')
  const [range,    setRange]   = useState('today')
  const [date,     setDate]    = useState(toDateInput(new Date()))
  const [data,     setData]    = useState([])
  const [loading,  setLoading] = useState(false)
  const [error,    setError]   = useState(null)

  const activeTab  = TABS.find(t => t.key === tab)
  const showCustom = range === 'custom'
  const fetchDate  = range === 'custom' ? date : getRangeDate(range)

  const fetchReport = useCallback(async () => {
    if (tab === 'users' || tab === 'ai') return
    setLoading(true)
    setError(null)
    try {
      const fn = tab === 'occupancy' ? adminApi.getOccupancyReport : adminApi.getEnergyReport
      const res = await fn(fetchDate)
      const raw = res.data.data ?? []

      const byHour = Object.fromEntries(raw.map(p => [p.hour, p.value]))
      const formatted = Array.from({ length: 24 }, (_, h) => ({
        label: `${String(h).padStart(2, '0')}:00`,
        value: tab === 'occupancy'
          ? parseFloat(((byHour[h] ?? 0) * 100).toFixed(1))
          : parseFloat((byHour[h] ?? 0).toFixed(1)),
      }))
      setData(formatted)
    } catch (err) {
      setError(err.response?.data?.message || 'Rapor alınamadı')
    } finally {
      setLoading(false)
    }
  }, [tab, fetchDate])

  useEffect(() => { fetchReport() }, [fetchReport])

  function handleExport() {
    const filename = `eco-terminal-${tab}-${fetchDate}.csv`
    exportCSV(data, filename, [
      { key: 'label', label: 'Saat' },
      { key: 'value', label: activeTab.unit ? `Değer (${activeTab.unit})` : 'Değer' },
    ])
  }

  return (
    <div className="flex-1 p-6 space-y-6 overflow-auto">
      {/* Başlık */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-white">Raporlar</h1>
          <p className="text-gray-400 text-sm mt-0.5">Yoğunluk, enerji ve AI analiz raporları</p>
        </div>

        {/* Export butonu */}
        {(tab === 'occupancy' || tab === 'energy') && data.length > 0 && (
          <button
            onClick={handleExport}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-eco-green/10
                       border border-eco-green/30 text-eco-green text-sm hover:bg-eco-green/20 transition-colors"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
            CSV İndir
          </button>
        )}
      </div>

      {/* Zaman aralığı seçici */}
      <div className="flex flex-wrap gap-2 items-center">
        {RANGES.map(r => (
          <button
            key={r.key}
            onClick={() => setRange(r.key)}
            className={`px-3 py-1.5 rounded-lg text-sm transition-colors ${
              range === r.key
                ? 'bg-eco-green/20 text-eco-green border border-eco-green/40'
                : 'bg-gray-800 text-gray-400 border border-gray-700 hover:border-gray-600'
            }`}
          >
            {r.label}
          </button>
        ))}
        {showCustom && (
          <input
            type="date"
            value={date}
            max={toDateInput(new Date())}
            onChange={e => setDate(e.target.value)}
            className="bg-gray-800 border border-gray-700 text-gray-300 text-sm rounded-lg px-3 py-1.5
                       focus:outline-none focus:border-eco-green/50"
          />
        )}
      </div>

      {/* Tab seçimi */}
      <div className="flex gap-2 flex-wrap">
        {TABS.map(t => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              tab === t.key
                ? 'bg-eco-green/20 text-eco-green border border-eco-green/40'
                : 'bg-gray-800 text-gray-400 border border-gray-700 hover:border-gray-600'
            }`}
          >
            <span>{t.icon}</span>
            {t.label}
          </button>
        ))}
      </div>

      {error && (
        <div className="px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-400 text-sm">
          {error}
        </div>
      )}

      {/* İçerik */}
      {tab === 'users' ? <UsersTab /> :
       tab === 'ai'    ? <AITab    /> : (
        <>
          {/* AreaChart */}
          <div className="bg-gray-800 rounded-xl p-4 border border-gray-700">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h2 className="text-white font-semibold">{activeTab.label} Raporu</h2>
                <p className="text-gray-500 text-xs mt-0.5">
                  {fetchDate} — saatlik {tab === 'occupancy' ? 'ortalama doluluk (%)' : 'toplam enerji (kWh)'}
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
                  contentStyle={{ backgroundColor: '#1F2937', border: '1px solid #374151', borderRadius: '0.5rem', fontSize: '12px' }}
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
          <StatsSummary data={data} unit={activeTab.unit} />

          {/* Veri tablosu */}
          {data.length > 0 && (
            <div className="bg-gray-800 rounded-xl border border-gray-700 overflow-hidden">
              <div className="p-4 border-b border-gray-700">
                <h2 className="text-white font-semibold">Saatlik Veri Tablosu</h2>
              </div>
              <div className="overflow-x-auto max-h-64">
                <table className="w-full text-sm">
                  <thead className="sticky top-0 bg-gray-800">
                    <tr className="text-gray-400 text-left border-b border-gray-700">
                      <th className="px-4 py-2.5 font-medium">Saat</th>
                      <th className="px-4 py-2.5 font-medium text-right">
                        {activeTab.unit ? `Değer (${activeTab.unit})` : 'Değer'}
                      </th>
                      <th className="px-4 py-2.5 font-medium text-right">Görsel</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-700/50">
                    {data.map((row, i) => {
                      const maxVal = Math.max(...data.map(d => d.value ?? 0), 1)
                      const pct    = Math.round(((row.value ?? 0) / maxVal) * 100)
                      return (
                        <tr key={i} className="hover:bg-gray-700/30">
                          <td className="px-4 py-2 text-gray-300">{row.label}</td>
                          <td className="px-4 py-2 text-right text-white font-medium">
                            {row.value}{activeTab.unit}
                          </td>
                          <td className="px-4 py-2">
                            <div className="flex items-center gap-2 justify-end">
                              <div className="w-24 h-1.5 bg-gray-700 rounded-full overflow-hidden">
                                <div
                                  className="h-full rounded-full transition-all"
                                  style={{ width: `${pct}%`, backgroundColor: activeTab.color }}
                                />
                              </div>
                            </div>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
