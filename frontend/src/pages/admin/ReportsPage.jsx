import React, { useState, useEffect, useCallback, useRef } from 'react'
import {
  BarChart, Bar, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer,
} from 'recharts'
import { adminApi } from '../../api/adminApi'

// ── Zaman aralığı tanımları ──────────────────────────────────────────────────

const RANGES = [
  { key: 'LAST_MONTH', label: 'Geçen Ay' },
  { key: 'LAST_30',    label: 'Son 30 Gün' },
]

const TABS = [
  { key: 'occupancy', label: 'Yoğunluk', color: '#2ECC71', unit: '%',   icon: '👥' },
  { key: 'energy',    label: 'Enerji',    color: '#F39C12', unit: 'kWh', icon: '⚡' },
  { key: 'users',     label: 'Kullanıcı', color: '#3B82F6', unit: '',    icon: '👤' },
]

/** range key → { startDate, endDate } YYYY-MM-DD strings */
function rangeToDates(rangeKey) {
  const now   = new Date()
  const today = now.toISOString().slice(0, 10)

  if (rangeKey === 'LAST_MONTH') {
    const start = new Date(now.getFullYear(), now.getMonth() - 1, 1)
    const end   = new Date(now.getFullYear(), now.getMonth(), 1)
    return {
      startDate: start.toISOString().slice(0, 10),
      endDate:   end.toISOString().slice(0, 10),
    }
  }
  // LAST_30 default
  const start = new Date(now); start.setDate(start.getDate() - 30)
  return { startDate: start.toISOString().slice(0, 10), endDate: today }
}

// ── Yardımcı bileşenler ──────────────────────────────────────────────────────

function InsightCard({ icon, label, value, sub, subColor = 'text-gray-400', highlight = false }) {
  return (
    <div className={`rounded-xl p-4 border ${highlight ? 'bg-eco-green/5 border-eco-green/30' : 'bg-gray-800 border-gray-700'}`}>
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="text-gray-400 text-xs mb-1">{label}</p>
          <p className={`text-xl font-bold ${highlight ? 'text-eco-green' : 'text-white'}`}>{value}</p>
          {sub && <p className={`text-xs mt-1 ${subColor}`}>{sub}</p>}
        </div>
        <span className="text-2xl select-none">{icon}</span>
      </div>
    </div>
  )
}

function DeltaBadge({ current, prev, unit, higherIsBad = false }) {
  if (prev <= 0) return <span className="text-gray-500 text-xs">Karşılaştırma yok</span>
  const delta = current - prev
  const pct   = ((delta / prev) * 100).toFixed(1)
  const up    = delta >= 0
  const good  = higherIsBad ? !up : up
  const color = good ? 'text-eco-green' : 'text-red-400'
  const arrow = up ? '▲' : '▼'
  return (
    <span className={`text-xs font-medium ${color}`}>
      {arrow} {Math.abs(pct)}% önceki döneme göre
    </span>
  )
}

function Spinner() {
  return (
    <div className="w-4 h-4 border-2 border-eco-green border-t-transparent rounded-full animate-spin" />
  )
}

function ErrorBanner({ msg }) {
  return (
    <div className="px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-400 text-sm">
      {msg}
    </div>
  )
}

// ── CSV export ────────────────────────────────────────────────────────────────

function exportCSV(data, filename) {
  const header = 'Saat,Değer'
  const rows   = data.map(d => `${d.label},${d.value}`)
  const csv    = [header, ...rows].join('\n')
  const blob   = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' })
  const url    = URL.createObjectURL(blob)
  const a      = document.createElement('a')
  a.href = url; a.download = filename; a.click()
  URL.revokeObjectURL(url)
}

// ── PDF ──────────────────────────────────────────────────────────────────────

function PdfButton({ onClick, loading }) {
  return (
    <button
      onClick={onClick}
      disabled={loading}
      className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium bg-gray-700 text-gray-200 hover:bg-gray-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors border border-gray-600"
    >
      {loading ? <Spinner /> : <span className="select-none">📄</span>}
      <span>{loading ? 'PDF hazırlanıyor…' : 'PDF Rapor İndir'}</span>
    </button>
  )
}

const RANGE_LABELS = {
  LAST_MONTH: 'Geçen Ay',
  LAST_30:    'Son 30 Gün',
}

const TAB_KEYS = {
  occupancy: 'yogunluk',
  energy:    'enerji',
  users:     'kullanici',
}

/**
 * Her sekme bileşeninde kullanılan PDF indirme hook'u.
 * contentRef: yakalanacak sekme içerik div'ine bağlanır.
 */
function usePdfDownload({ tabId, tabLabel, range }) {
  const [pdfLoading, setPdfLoading] = useState(false)
  const contentRef = useRef(null)

  const handlePdf = useCallback(async () => {
    if (!contentRef.current) return
    const { exportTabPdf } = await import('../../utils/pdfExport.js')
    const { startDate, endDate } = rangeToDates(range)
    const fmt = d =>
      new Date(d + 'T00:00:00').toLocaleDateString('tr-TR', {
        day: 'numeric', month: 'short', year: 'numeric',
      })
    await exportTabPdf({
      contentRef,
      tabLabel,
      rangeLabel: RANGE_LABELS[range] || range,
      dateRange:  `${fmt(startDate)} – ${fmt(endDate)}`,
      filename:   `eco-terminal-${TAB_KEYS[tabId]}-raporu-${new Date().toISOString().slice(0, 10)}.pdf`,
      onStart: () => setPdfLoading(true),
      onEnd:   () => setPdfLoading(false),
    })
  }, [tabId, tabLabel, range])

  return { pdfLoading, handlePdf, contentRef }
}

// ── Kullanıcı Sekmesi ─────────────────────────────────────────────────────────

/** "YYYY-MM" → Türkçe kısa ay adı */
function fmtMonth(ym) {
  if (!ym) return '?'
  const [year, month] = ym.split('-')
  const names = ['Oca', 'Şub', 'Mar', 'Nis', 'May', 'Haz', 'Tem', 'Ağu', 'Eyl', 'Eki', 'Kas', 'Ara']
  return `${names[parseInt(month, 10) - 1]} '${year.slice(2)}`
}

function UsersTab({ range }) {
  const [data, setData]     = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError]   = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const { startDate, endDate } = rangeToDates(range)
      const res = await adminApi.getUserReportSummary(startDate, endDate)
      setData(res.data.data)
    } catch (err) {
      setError(err.response?.data?.message || 'Kullanıcı raporu alınamadı')
    } finally {
      setLoading(false)
    }
  }, [range])

  useEffect(() => { load() }, [load])

  // Grafik verisi: { label, count }
  const chartData = (data?.newUsersByMonth ?? []).map(m => ({
    label: fmtMonth(m.month),
    count: m.count,
  }))

  const ls = data?.loyaltyStats ?? {}
  const { pdfLoading, handlePdf, contentRef } = usePdfDownload({ tabId: 'users', tabLabel: 'Kullanıcı', range })

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <PdfButton onClick={handlePdf} loading={pdfLoading} />
      </div>
      <div ref={contentRef} className="space-y-4">
      {error && <ErrorBanner msg={error} />}

      {/* 4 Kart */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">

        {/* Toplam Kullanıcı */}
        <InsightCard
          icon="👥"
          label="Toplam Kullanıcı"
          value={data ? `${data.totalUsers}` : '…'}
          sub={data
            ? `${data.adminCount} Admin · ${data.passengerCount} Yolcu`
            : null}
          highlight
        />

        {/* Yeni Kayıt */}
        <InsightCard
          icon="🆕"
          label="Yeni Kayıt (Dönem)"
          value={data ? `${data.newUsersInPeriod} kullanıcı` : '…'}
          sub={data
            ? <DeltaBadge current={data.newUsersInPeriod} prev={data.prevNewUsersInPeriod} />
            : null}
        />

        {/* Email Doğrulama */}
        <div className="rounded-xl p-4 border bg-gray-800 border-gray-700">
          <div className="flex items-start justify-between gap-2">
            <div className="flex-1">
              <p className="text-gray-400 text-xs mb-1">Email Doğrulama</p>
              <p className="text-xl font-bold text-white">
                {data ? `%${data.emailVerifiedRate.toFixed(0)}` : '…'}
              </p>
              {data && (
                <div className="mt-2 w-full h-1.5 bg-gray-700 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-eco-green rounded-full transition-all duration-500"
                    style={{ width: `${Math.min(data.emailVerifiedRate, 100)}%` }}
                  />
                </div>
              )}
              <p className="text-gray-400 text-xs mt-1">doğrulandı</p>
            </div>
            <span className="text-2xl select-none">✉️</span>
          </div>
        </div>

        {/* Eco Puan Aktivitesi */}
        <InsightCard
          icon="🌿"
          label="Eco Puan Aktivitesi"
          value={data ? `${(ls.totalEarned ?? 0).toLocaleString()} puan` : '…'}
          sub={data
            ? `${ls.earnCount ?? 0} EARN · ${ls.spendCount ?? 0} SPEND`
            : null}
        />
      </div>

      {/* Aylık Kayıt Grafiği */}
      <div className="bg-gray-800 rounded-xl p-4 border border-gray-700">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="text-white font-semibold">Aylık Yeni Kayıt</h2>
            <p className="text-gray-500 text-xs mt-0.5">Son 6 ay — aylık yeni kullanıcı sayısı</p>
          </div>
          {loading && <Spinner />}
        </div>

        {chartData.length > 0 ? (
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={chartData} margin={{ top: 10, right: 10, left: -10, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" vertical={false} />
              <XAxis dataKey="label" tick={{ fill: '#9CA3AF', fontSize: 11 }} tickLine={false} />
              <YAxis allowDecimals={false} tick={{ fill: '#9CA3AF', fontSize: 11 }} tickLine={false} axisLine={false} />
              <Tooltip
                contentStyle={{ backgroundColor: '#1F2937', border: '1px solid #374151', borderRadius: '0.5rem', fontSize: '12px' }}
                labelStyle={{ color: '#F9FAFB', marginBottom: 4 }}
                formatter={v => [v, 'Yeni Kayıt']}
              />
              <Bar dataKey="count" radius={[4, 4, 0, 0]}>
                {chartData.map((_, i) => (
                  <Cell key={i} fill={i === chartData.length - 1 ? '#2ECC71' : '#3B82F6'} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        ) : (
          <div className="h-[220px] flex items-center justify-center text-gray-600 text-sm">
            {loading ? 'Yükleniyor…' : 'Kayıt verisi bulunamadı'}
          </div>
        )}
      </div>

      {/* Alt 2 Panel */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">

        {/* En Aktif Yolcular */}
        <div className="bg-gray-800 rounded-xl p-4 border border-gray-700">
          <h3 className="text-white font-semibold text-sm mb-3">En Aktif Yolcular</h3>
          {(data?.topPointEarners ?? []).length > 0 ? (
            <div className="space-y-2">
              {data.topPointEarners.map((e, i) => {
                const medals = ['🥇', '🥈', '🥉', '4️⃣', '5️⃣']
                const maxPts = data.topPointEarners[0].totalPoints || 1
                return (
                  <div key={i} className="flex items-center gap-3">
                    <span className="text-base select-none">{medals[i]}</span>
                    <span className="text-gray-300 text-sm flex-1 truncate">{e.displayName}</span>
                    <div className="flex items-center gap-2">
                      <div className="w-20 h-1.5 bg-gray-700 rounded-full overflow-hidden">
                        <div
                          className="h-full bg-eco-green rounded-full"
                          style={{ width: `${(e.totalPoints / maxPts) * 100}%` }}
                        />
                      </div>
                      <span className="text-eco-green text-xs font-medium w-16 text-right">
                        {e.totalPoints.toLocaleString()} pt
                      </span>
                    </div>
                  </div>
                )
              })}
            </div>
          ) : (
            <p className="text-gray-600 text-sm">Puan verisi bulunamadı</p>
          )}
        </div>

        {/* İçgörü */}
        <div className="bg-gray-800 rounded-xl p-4 border border-gray-700 flex flex-col justify-between">
          <div>
            <p className="text-blue-400/80 text-xs font-semibold uppercase tracking-wide mb-2">İçgörü</p>
            {data?.comparisonText ? (
              <p className="text-gray-300 text-sm leading-relaxed">{data.comparisonText}</p>
            ) : (
              <p className="text-gray-600 text-sm">{loading ? 'Yükleniyor…' : 'Veri yok'}</p>
            )}
          </div>
          {data && (
            <div className="mt-4 pt-3 border-t border-gray-700 grid grid-cols-2 gap-2 text-center">
              <div>
                <p className="text-eco-green font-bold text-lg">{(ls.totalEarned ?? 0).toLocaleString()}</p>
                <p className="text-gray-500 text-xs">puan kazanıldı</p>
              </div>
              <div>
                <p className="text-orange-400 font-bold text-lg">{(ls.totalSpent ?? 0).toLocaleString()}</p>
                <p className="text-gray-500 text-xs">puan harcandı</p>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Ek Aktivite İstatistikleri */}
      {data && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">

          {/* Aktiflik Oranı */}
          <div className="rounded-xl p-4 border bg-gray-800 border-gray-700">
            <div className="flex items-start justify-between gap-2">
              <div className="flex-1">
                <p className="text-gray-400 text-xs mb-1">Aktiflik Oranı</p>
                <p className="text-xl font-bold text-white">
                  %{(data.activeEarnerRate ?? 0).toFixed(1)}
                </p>
                <div className="mt-2 w-full h-1.5 bg-gray-700 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-blue-500 rounded-full transition-all duration-500"
                    style={{ width: `${Math.min(data.activeEarnerRate ?? 0, 100)}%` }}
                  />
                </div>
                <p className="text-gray-400 text-xs mt-1">
                  {data.activeEarnerCount} / {data.totalUsers} kullanıcı puan kazandı
                </p>
              </div>
              <span className="text-2xl select-none">📊</span>
            </div>
          </div>

          {/* Eko Puan Devam Oranı */}
          <div className="rounded-xl p-4 border bg-gray-800 border-gray-700">
            <div className="flex items-start justify-between gap-2">
              <div className="flex-1">
                <p className="text-gray-400 text-xs mb-1">Eko Puan Devam Oranı</p>
                <p className="text-xl font-bold text-white">
                  %{(data.repeatEarnerRate ?? 0).toFixed(1)}
                </p>
                <div className="mt-2 w-full h-1.5 bg-gray-700 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-eco-green rounded-full transition-all duration-500"
                    style={{ width: `${Math.min(data.repeatEarnerRate ?? 0, 100)}%` }}
                  />
                </div>
                <p className="text-gray-400 text-xs mt-1">
                  {data.repeatEarnerCount} cüzdan birden fazla işlem yaptı
                </p>
                <p className="text-yellow-400/70 text-xs mt-0.5">küçük örneklem</p>
              </div>
              <span className="text-2xl select-none">🔄</span>
            </div>
          </div>

          {/* Rota Tamamlama */}
          <InsightCard
            icon="🗺️"
            label="Rota Tamamlama"
            value={data.routeCompletions != null ? `${data.routeCompletions} tamamlandı` : '…'}
            sub={data.routeCompletions === 1
              ? 'route_suggestion tablosundaki kayıt (gerçek veri)'
              : data.routeCompletions === 0
                ? 'henüz tamamlanan rota yok'
                : `${data.routeCompletions} rota tamamlandı`}
            subColor={data.routeCompletions > 0 ? 'text-eco-green' : 'text-gray-500'}
          />
        </div>
      )}
      </div>
    </div>
  )
}

// ── AI Özet Sekmesi ───────────────────────────────────────────────────────────

/** "YYYY-MM-DD" → "dd Oca" kısa format */
function fmtDay(d) {
  if (!d) return '?'
  const dt = new Date(d + 'T00:00:00Z')
  const months = ['Oca','Şub','Mar','Nis','May','Haz','Tem','Ağu','Eyl','Eki','Kas','Ara']
  return `${dt.getUTCDate()} ${months[dt.getUTCMonth()]}`
}

// ── Yoğunluk Sekmesi ─────────────────────────────────────────────────────────

function OccupancyTab({ range }) {
  const [summary, setSummary]     = useState(null)
  const [loading, setLoading]     = useState(false)
  const [error, setError]         = useState(null)
  const [sortKey, setSortKey]     = useState('avgPct')
  const { pdfLoading, handlePdf, contentRef } = usePdfDownload({ tabId: 'occupancy', tabLabel: 'Yoğunluk', range })

  const fetch = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const sumRes = await adminApi.getOccupancySummary(range)
      setSummary(sumRes.data.data)
    } catch (err) {
      setError(err.response?.data?.message || 'Yoğunluk raporu alınamadı')
    } finally {
      setLoading(false)
    }
  }, [range])

  useEffect(() => { fetch() }, [fetch])

  const peakLabel = summary
    ? `${String(summary.peakHour).padStart(2, '0')}:00 – ${String(summary.peakHour + 1).padStart(2, '0')}:00`
    : '—'

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <PdfButton onClick={handlePdf} loading={pdfLoading} />
      </div>
      <div ref={contentRef} className="space-y-4">
      {error && <ErrorBanner msg={error} />}

      {/* Insight Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <InsightCard
          icon="👥"
          label="Ort. Doluluk"
          value={summary ? `%${summary.avgDensity.toFixed(1)}` : '…'}
          sub={summary
            ? <DeltaBadge current={summary.avgDensity} prev={summary.prevAvgDensity} unit="%" higherIsBad />
            : null}
          highlight
        />
        <InsightCard
          icon="🏆"
          label="En Yoğun Bölge"
          value={summary?.topZones?.[0]?.zoneName ?? '—'}
          sub={summary?.topZones?.[0] ? `%${summary.topZones[0].value.toFixed(1)} ort.` : null}
        />
        <InsightCard
          icon="⏰"
          label="Peak Saat"
          value={peakLabel}
          sub={summary ? `%${summary.peakHourDensity.toFixed(1)} doluluk` : null}
        />
        <InsightCard
          icon="🚨"
          label="Kritik Okuma"
          value={summary ? summary.criticalReadings.toLocaleString() : '…'}
          sub="Doluluk ≥ %85 olan ölçüm"
          subColor={summary?.criticalReadings > 0 ? 'text-red-400' : 'text-gray-400'}
        />
      </div>

      {/* Gün Bazlı Doluluk Trendi */}
      {(summary?.dailyTrend?.length ?? 0) > 0 && (
        <div className="bg-gray-800 rounded-xl p-4 border border-gray-700">
          <h3 className="text-white font-semibold text-sm mb-1">Gün Bazlı Doluluk Trendi</h3>
          <p className="text-gray-500 text-xs mb-3">Dönem içi günlük ortalama doluluk (%)</p>
          <ResponsiveContainer width="100%" height={180}>
            <BarChart
              data={summary.dailyTrend.map(d => ({
                label: fmtDay(d.date),
                avg:   parseFloat(d.avgPct.toFixed(1)),
              }))}
              margin={{ top: 5, right: 10, left: -20, bottom: 5 }}
            >
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" vertical={false} />
              <XAxis
                dataKey="label"
                tick={{ fill: '#9CA3AF', fontSize: 9 }}
                tickLine={false}
                interval={Math.max(0, Math.floor(summary.dailyTrend.length / 7) - 1)}
              />
              <YAxis tick={{ fill: '#9CA3AF', fontSize: 11 }} tickLine={false} axisLine={false} tickFormatter={v => `%${v}`} />
              <Tooltip
                contentStyle={{ backgroundColor: '#1F2937', border: '1px solid #374151', borderRadius: '0.5rem', fontSize: '12px' }}
                formatter={v => [`%${v}`, 'Ort. Doluluk']}
              />
              <Bar dataKey="avg" radius={[3, 3, 0, 0]}>
                {summary.dailyTrend.map((d, i) => (
                  <Cell key={i} fill={d.avgPct >= 85 ? '#EF4444' : d.avgPct >= 60 ? '#F59E0B' : '#2ECC71'} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Saatlik Pik Analizi + Zone Detay (yan yana) */}
      {(summary?.peakHours?.length ?? 0) > 0 && (
        <div className="bg-gray-800 rounded-xl p-4 border border-gray-700">
          <h3 className="text-white font-semibold text-sm mb-3">Saatlik Pik Analizi (Top 5)</h3>
          <div className="space-y-2.5">
            {summary.peakHours.slice(0, 5).map((p, i) => {
              const medals = ['🥇', '🥈', '🥉', '4️⃣', '5️⃣']
              const pct = parseFloat(p.avgPct.toFixed(1))
              return (
                <div key={i} className="flex items-center gap-3">
                  <span className="text-base select-none w-6 shrink-0">{medals[i]}</span>
                  <span className="text-gray-300 text-sm w-14 shrink-0">
                    {String(p.hour).padStart(2, '0')}:00
                  </span>
                  <div className="flex-1 flex items-center gap-2">
                    <div className="flex-1 h-2 bg-gray-700 rounded-full overflow-hidden">
                      <div
                        className={`h-full rounded-full transition-all duration-500 ${
                          pct >= 85 ? 'bg-red-500' : pct >= 60 ? 'bg-yellow-400' : 'bg-eco-green'
                        }`}
                        style={{ width: `${Math.min(pct, 100)}%` }}
                      />
                    </div>
                    <span className="text-eco-green text-xs font-medium w-10 text-right shrink-0">%{pct}</span>
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      )}

      {/* Tüm Zone Detay Tablosu — sıralanabilir */}
      {(summary?.zoneBreakdown?.length ?? 0) > 0 && (
        <div className="bg-gray-800 rounded-xl p-4 border border-gray-700">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-white font-semibold text-sm">Tüm Zone Detayları</h3>
            <span className="text-gray-500 text-xs">sütuna tıkla → sırala</span>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-xs text-left">
              <thead>
                <tr className="text-gray-500 border-b border-gray-700">
                  {[
                    { key: 'zoneName',      label: 'Zone'    },
                    { key: 'avgPct',        label: 'Ort. %'  },
                    { key: 'maxPct',        label: 'Max %'   },
                    { key: 'minPct',        label: 'Min %'   },
                    { key: 'criticalCount', label: 'Kritik'  },
                    { key: 'readings',      label: 'Okuma'   },
                  ].map(col => (
                    <th
                      key={col.key}
                      onClick={() => setSortKey(col.key)}
                      className={`pb-2 pr-4 cursor-pointer hover:text-gray-300 transition-colors select-none whitespace-nowrap ${
                        sortKey === col.key ? 'text-eco-green' : ''
                      }`}
                    >
                      {col.label}{sortKey === col.key ? ' ▼' : ''}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {[...summary.zoneBreakdown]
                  .sort((a, b) =>
                    sortKey === 'zoneName'
                      ? a.zoneName.localeCompare(b.zoneName)
                      : (b[sortKey] ?? 0) - (a[sortKey] ?? 0)
                  )
                  .map((z, i) => {
                    const avgPct  = parseFloat(z.avgPct.toFixed(1))
                    const maxPct  = parseFloat(z.maxPct.toFixed(1))
                    const minPct  = parseFloat(z.minPct.toFixed(1))
                    const avgColor = avgPct >= 85 ? '#EF4444' : avgPct >= 60 ? '#F59E0B' : '#2ECC71'
                    return (
                      <tr key={i} className="border-b border-gray-700/40 hover:bg-gray-700/20 transition-colors">
                        <td className="py-1.5 pr-4 text-gray-300 font-medium truncate max-w-[130px]">{z.zoneName}</td>
                        <td className="py-1.5 pr-4 font-semibold" style={{ color: avgColor }}>%{avgPct}</td>
                        <td className="py-1.5 pr-4 text-gray-400">%{maxPct}</td>
                        <td className="py-1.5 pr-4 text-gray-400">%{minPct}</td>
                        <td className={`py-1.5 pr-4 font-semibold ${z.criticalCount > 0 ? 'text-red-400' : 'text-gray-600'}`}>
                          {z.criticalCount}
                        </td>
                        <td className="py-1.5 pr-4 text-gray-500">{z.readings}</td>
                      </tr>
                    )
                  })}
              </tbody>
            </table>
          </div>
        </div>
      )}
      </div>
    </div>
  )
}

// ── Enerji Sekmesi ────────────────────────────────────────────────────────────

function EnergyTab({ range }) {
  const [summary, setSummary]     = useState(null)
  const [loading, setLoading]     = useState(false)
  const [error, setError]         = useState(null)
  const [sortKey, setSortKey]     = useState('avgKwh')
  const { pdfLoading, handlePdf, contentRef } = usePdfDownload({ tabId: 'energy', tabLabel: 'Enerji', range })

  const fetch = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const sumRes = await adminApi.getEnergySummary(range)
      setSummary(sumRes.data.data)
    } catch (err) {
      setError(err.response?.data?.message || 'Enerji raporu alınamadı')
    } finally {
      setLoading(false)
    }
  }, [range])

  useEffect(() => { fetch() }, [fetch])

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <PdfButton onClick={handlePdf} loading={pdfLoading} />
      </div>
      <div ref={contentRef} className="space-y-4">
      {error && <ErrorBanner msg={error} />}

      {/* 4 KPI Kartı */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <InsightCard
          icon="⚡"
          label="Toplam Tüketim"
          value={summary ? `${summary.totalKwh.toFixed(1)} kWh` : '…'}
          sub={summary
            ? <DeltaBadge current={summary.totalKwh} prev={summary.prevTotalKwh} unit="kWh" higherIsBad />
            : null}
          highlight
        />
        <InsightCard
          icon="🏭"
          label="En Çok Tüketen"
          value={summary?.topZoneName ?? '—'}
          sub={summary ? `${summary.topZoneKwh.toFixed(1)} kWh` : null}
        />
        <InsightCard
          icon="🌡️"
          label="Ort. Sıcaklık / Lux"
          value={summary ? `${summary.avgTemp.toFixed(1)}°C` : '…'}
          sub={summary ? `${summary.avgLux.toFixed(0)} lux` : null}
        />
        <InsightCard
          icon="🔧"
          label="Enerji Ayarı"
          value={summary ? summary.settingChanges.toString() : '…'}
          sub="Dönemde yapılan değişiklik"
        />
      </div>

      {/* No data notice */}
      {summary && !summary.hasEnergyData && (
        <div className="px-4 py-3 rounded-xl bg-yellow-500/10 border border-yellow-500/30 text-yellow-400 text-sm">
          Seçili dönem için yeterli enerji ölçümü bulunamadı. Sensör verisi eksik olabilir.
        </div>
      )}

      {/* Gün Bazlı Enerji Trendi */}
      {(summary?.dailyTrend?.length ?? 0) > 0 && (
        <div className="bg-gray-800 rounded-xl p-4 border border-gray-700">
          <h3 className="text-white font-semibold text-sm mb-1">Gün Bazlı Enerji Trendi</h3>
          <p className="text-gray-500 text-xs mb-3">Dönem içi günlük ortalama enerji tüketimi (kWh)</p>
          <ResponsiveContainer width="100%" height={180}>
            <BarChart
              data={summary.dailyTrend.map(d => ({
                label: fmtDay(d.date),
                avg:   parseFloat(d.avgKwh.toFixed(1)),
              }))}
              margin={{ top: 5, right: 10, left: -20, bottom: 5 }}
            >
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" vertical={false} />
              <XAxis
                dataKey="label"
                tick={{ fill: '#9CA3AF', fontSize: 9 }}
                tickLine={false}
                interval={Math.max(0, Math.floor(summary.dailyTrend.length / 7) - 1)}
              />
              <YAxis tick={{ fill: '#9CA3AF', fontSize: 11 }} tickLine={false} axisLine={false} tickFormatter={v => `${v}`} />
              <Tooltip
                contentStyle={{ backgroundColor: '#1F2937', border: '1px solid #374151', borderRadius: '0.5rem', fontSize: '12px' }}
                formatter={v => [`${v} kWh`, 'Ort. Tüketim']}
              />
              <Bar dataKey="avg" radius={[3, 3, 0, 0]}>
                {summary.dailyTrend.map((d, i) => (
                  <Cell key={i} fill={d.avgKwh >= 20 ? '#EF4444' : d.avgKwh >= 12 ? '#F59E0B' : '#2ECC71'} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Saatlik Enerji Tüketimi Top 5 */}
      {(summary?.topPeakHours?.length ?? 0) > 0 && (
        <div className="bg-gray-800 rounded-xl p-4 border border-gray-700">
          <h3 className="text-white font-semibold text-sm mb-3">Saatlik Enerji Tüketimi (Top 5)</h3>
          <div className="space-y-2.5">
            {summary.topPeakHours.slice(0, 5).map((p, i) => {
              const medals  = ['🥇', '🥈', '🥉', '4️⃣', '5️⃣']
              const maxKwh  = summary.topPeakHours[0]?.avgKwh || 1
              const kwh     = parseFloat(p.avgKwh.toFixed(1))
              const barW    = (p.avgKwh / maxKwh) * 100
              return (
                <div key={i} className="flex items-center gap-3">
                  <span className="text-base select-none w-6 shrink-0">{medals[i]}</span>
                  <span className="text-gray-300 text-sm w-14 shrink-0">
                    {String(p.hour).padStart(2, '0')}:00
                  </span>
                  <div className="flex-1 flex items-center gap-2">
                    <div className="flex-1 h-2 bg-gray-700 rounded-full overflow-hidden">
                      <div
                        className={`h-full rounded-full transition-all duration-500 ${
                          kwh >= 20 ? 'bg-red-500' : kwh >= 12 ? 'bg-yellow-400' : 'bg-eco-green'
                        }`}
                        style={{ width: `${Math.min(barW, 100)}%` }}
                      />
                    </div>
                    <span className="text-yellow-400 text-xs font-medium w-16 text-right shrink-0">
                      {kwh} kWh
                    </span>
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      )}

      {/* Tüm Zone Enerji Detayları — sıralanabilir */}
      {(summary?.zoneBreakdown?.length ?? 0) > 0 && (
        <div className="bg-gray-800 rounded-xl p-4 border border-gray-700">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-white font-semibold text-sm">Tüm Zone Enerji Detayları</h3>
            <span className="text-gray-500 text-xs">sütuna tıkla → sırala</span>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-xs text-left">
              <thead>
                <tr className="text-gray-500 border-b border-gray-700">
                  {[
                    { key: 'zoneName',      label: 'Zone'     },
                    { key: 'avgKwh',        label: 'Ort. kWh' },
                    { key: 'maxKwh',        label: 'Maks kWh' },
                    { key: 'minKwh',        label: 'Min kWh'  },
                    { key: 'criticalCount', label: 'Kritik'   },
                    { key: 'readings',      label: 'Okuma'    },
                  ].map(col => (
                    <th
                      key={col.key}
                      onClick={() => setSortKey(col.key)}
                      className={`pb-2 pr-4 cursor-pointer hover:text-gray-300 transition-colors select-none whitespace-nowrap ${
                        sortKey === col.key ? 'text-yellow-400' : ''
                      }`}
                    >
                      {col.label}{sortKey === col.key ? ' ▼' : ''}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {[...summary.zoneBreakdown]
                  .sort((a, b) =>
                    sortKey === 'zoneName'
                      ? a.zoneName.localeCompare(b.zoneName)
                      : (b[sortKey] ?? 0) - (a[sortKey] ?? 0)
                  )
                  .map((z, i) => {
                    const avgKwh  = parseFloat(z.avgKwh.toFixed(1))
                    const avgColor = avgKwh >= 20 ? '#EF4444' : avgKwh >= 12 ? '#F59E0B' : '#2ECC71'
                    return (
                      <tr key={i} className="border-b border-gray-700/40 hover:bg-gray-700/20 transition-colors">
                        <td className="py-1.5 pr-4 text-gray-300 font-medium truncate max-w-[130px]">{z.zoneName}</td>
                        <td className="py-1.5 pr-4 font-semibold" style={{ color: avgColor }}>{avgKwh} kWh</td>
                        <td className="py-1.5 pr-4 text-gray-400">{parseFloat(z.maxKwh.toFixed(1))} kWh</td>
                        <td className="py-1.5 pr-4 text-gray-400">{parseFloat(z.minKwh.toFixed(1))} kWh</td>
                        <td className={`py-1.5 pr-4 font-semibold ${z.criticalCount > 0 ? 'text-red-400' : 'text-gray-600'}`}>
                          {z.criticalCount}
                        </td>
                        <td className="py-1.5 pr-4 text-gray-500">{z.readings}</td>
                      </tr>
                    )
                  })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Tasarruf Potansiyeli */}
      {(summary?.savingsOpportunities?.length ?? 0) > 0 && (
        <div className="bg-eco-green/5 border border-eco-green/20 rounded-xl p-4">
          <p className="text-eco-green/80 text-xs font-semibold uppercase tracking-wide mb-2">💡 Tasarruf Potansiyeli</p>
          <p className="text-gray-400 text-xs mb-3">Yüksek enerji tüketen ama düşük doluluklu zone'lar — optimizasyon adayları:</p>
          <div className="space-y-2">
            {summary.savingsOpportunities.map((s, i) => (
              <div key={i} className="flex items-center justify-between bg-gray-900/50 rounded-lg px-3 py-2">
                <span className="text-gray-300 text-sm font-medium">{s.zoneName}</span>
                <div className="flex items-center gap-4 text-xs">
                  <span className="text-yellow-400">{s.avgKwh.toFixed(1)} kWh/okuma</span>
                  <span className="text-blue-400">%{s.avgDensityPct.toFixed(1)} doluluk</span>
                  <span className="text-eco-green font-semibold">tasarruf adayı</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
      </div>
    </div>
  )
}

// ── Ana bileşen ───────────────────────────────────────────────────────────────

export default function ReportsPage() {
  const [tab,   setTab]   = useState('occupancy')
  const [range, setRange] = useState('LAST_30')

  return (
    <div className="flex-1 p-6 space-y-6 overflow-auto">
      {/* Başlık */}
      <div>
        <h1 className="text-2xl font-bold text-white">Raporlar</h1>
        <p className="text-gray-400 text-sm mt-0.5">Yoğunluk, enerji ve AI analiz raporları</p>
      </div>

      {/* Zaman aralığı seçici — Kullanıcı sekmesinde gizle */}
      {tab !== 'users' && (
      <div className="flex flex-wrap gap-2">
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
      </div>
      )}

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

      {/* İçerik */}
      {tab === 'occupancy' && <OccupancyTab key={`occ-${range}`} range={range} />}
      {tab === 'energy'    && <EnergyTab    key={`eng-${range}`} range={range} />}
      {tab === 'users'     && <UsersTab     key={`usr-${range}`} range={range} />}
    </div>
  )
}
