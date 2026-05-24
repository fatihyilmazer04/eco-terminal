import React, { useState } from 'react'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, Cell, ReferenceLine,
  LineChart, Line, Legend
} from 'recharts'
import KpiCard from '../../components/KpiCard'
import PredictionCard from '../../components/PredictionCard'
import { useAIPredictions } from '../../hooks/useAIPredictions'
import { predictionApi } from '../../api/predictionApi'

const RISK_ORDER = { HIGH: 0, MEDIUM: 1, LOW: 2 }
const BAR_COLOR  = { HIGH: '#E74C3C', MEDIUM: '#F39C12', LOW: '#2ECC71' }
const RISK_BADGE = {
  HIGH:   'text-red-400 bg-red-500/10 border border-red-500/30',
  MEDIUM: 'text-amber-400 bg-amber-500/10 border border-amber-500/30',
  LOW:    'text-eco-green bg-eco-green/10 border border-eco-green/30',
}

function formatTime(isoString) {
  if (!isoString) return '--'
  return new Date(isoString).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' })
}

/** Çok horizonlu tahmin modalı */
function ForecastModal({ prediction, onClose }) {
  const [forecast, setForecast] = useState(null)
  const [loading, setLoading]   = useState(true)
  const [tab, setTab]           = useState('short') // 'short' | 'long'

  React.useEffect(() => {
    setLoading(true)
    predictionApi.getZoneForecast(prediction.zoneId)
      .then(r => setForecast(r.data.data))
      .catch(() => setForecast(null))
      .finally(() => setLoading(false))
  }, [prediction.zoneId])

  const activePoints = forecast
    ? (tab === 'short' ? forecast.shortTerm : forecast.longTerm)
    : []

  const chartData = activePoints.map(p => ({
    time:    p.time,
    doluluk: parseFloat((p.predictedLoad * 100).toFixed(1)),
    risk:    p.riskLevel,
    conf:    parseFloat((p.confidence * 100).toFixed(0)),
  }))

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="bg-gray-800 border border-gray-700 rounded-xl w-full max-w-xl shadow-2xl">
        {/* Başlık */}
        <div className="flex items-center justify-between p-5 border-b border-gray-700">
          <div>
            <h3 className="text-white font-bold text-lg">{prediction.zoneName} — Tahmin</h3>
            <p className="text-gray-400 text-xs mt-0.5">LSTM bazlı çok horizonlu yoğunluk tahmini</p>
          </div>
          <button onClick={onClose} className="text-gray-500 hover:text-gray-300 transition-colors">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="p-5 space-y-4">
          {/* Mevcut durum */}
          {forecast && (
            <div className="grid grid-cols-3 gap-3">
              <div className="bg-gray-700/50 rounded-lg p-3 text-center">
                <p className="text-xs text-gray-400 mb-1">Mevcut Yük</p>
                <p className="text-xl font-bold text-white">%{((forecast.currentLoad ?? 0) * 100).toFixed(0)}</p>
              </div>
              <div className="bg-gray-700/50 rounded-lg p-3 text-center">
                <p className="text-xs text-gray-400 mb-1">Risk</p>
                <span className={`text-xs px-2 py-0.5 rounded-full font-semibold ${RISK_BADGE[forecast.currentRisk] ?? ''}`}>
                  {forecast.currentRisk}
                </span>
              </div>
              <div className="bg-gray-700/50 rounded-lg p-3 text-center">
                <p className="text-xs text-gray-400 mb-1">Model Güveni</p>
                <p className="text-lg font-bold text-eco-green">{forecast.modelConfidence}</p>
              </div>
            </div>
          )}

          {/* Öneri banner */}
          {forecast?.recommendation && (
            <div className="px-3 py-2 rounded-lg bg-blue-500/10 border border-blue-500/20 text-blue-300 text-sm">
              💡 {forecast.recommendation}
            </div>
          )}

          {/* Tab seçimi */}
          <div className="flex gap-2">
            {[
              { key: 'short', label: 'Kısa Vade (30–120 dk)' },
              { key: 'long',  label: 'Uzun Vade (6–24 sa)' },
            ].map(t => (
              <button
                key={t.key}
                onClick={() => setTab(t.key)}
                className={`text-sm px-3 py-1.5 rounded-lg transition-colors ${
                  tab === t.key
                    ? 'bg-eco-green/20 text-eco-green border border-eco-green/40'
                    : 'bg-gray-700 text-gray-400 border border-gray-600 hover:border-gray-500'
                }`}
              >
                {t.label}
              </button>
            ))}
          </div>

          {/* Grafik */}
          {loading ? (
            <div className="h-40 bg-gray-700/40 rounded animate-pulse" />
          ) : !forecast ? (
            <div className="h-40 flex items-center justify-center text-gray-500 text-sm">
              Tahmin verisi alınamadı.
            </div>
          ) : (
            <div className="bg-gray-900/50 rounded-lg p-3">
              <ResponsiveContainer width="100%" height={160}>
                <BarChart data={chartData} margin={{ top: 5, right: 5, left: -20, bottom: 5 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
                  <XAxis dataKey="time" tick={{ fill: '#9CA3AF', fontSize: 11 }} tickLine={false} />
                  <YAxis tick={{ fill: '#9CA3AF', fontSize: 10 }} tickLine={false} axisLine={false}
                         domain={[0, 100]} tickFormatter={v => `${v}%`} />
                  <Tooltip
                    contentStyle={{ backgroundColor: '#1F2937', border: '1px solid #374151', borderRadius: '0.5rem', fontSize: '11px' }}
                    formatter={(v, name) => name === 'doluluk' ? [`%${v}`, 'Tahmini Doluluk'] : [v, name]}
                  />
                  <ReferenceLine y={85} stroke="#E74C3C" strokeDasharray="4 4" strokeWidth={1.5}
                    label={{ value: '%85 Kritik', fill: '#E74C3C', fontSize: 9, position: 'right' }} />
                  <Bar dataKey="doluluk" radius={[3, 3, 0, 0]}>
                    {chartData.map((entry, i) => (
                      <Cell key={i} fill={BAR_COLOR[entry.risk] ?? '#6B7280'} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>

              {/* Confidence tablo */}
              <div className="mt-3 grid grid-cols-3 gap-2">
                {activePoints.map((p, i) => (
                  <div key={i} className="text-center">
                    <p className="text-xs text-gray-500">{p.time}</p>
                    <p className="text-sm font-semibold" style={{ color: BAR_COLOR[p.riskLevel] }}>
                      %{(p.predictedLoad * 100).toFixed(0)}
                    </p>
                    <p className="text-xs text-gray-600">güven: %{(p.confidence * 100).toFixed(0)}</p>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default function AIPredictionsPage() {
  const { predictions, highRisk, loading, error, lastUpdated, refresh, refreshing } =
    useAIPredictions()
  const [forecastZone, setForecastZone] = useState(null)

  if (loading) return <PageSkeleton />

  if (error) return (
    <div className="flex-1 p-6">
      <div className="px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-400 text-sm">
        {error}
      </div>
    </div>
  )

  const sorted = [...predictions].sort(
    (a, b) => (RISK_ORDER[a.riskLevel] ?? 3) - (RISK_ORDER[b.riskLevel] ?? 3)
  )

  const highCount   = predictions.filter(p => p.riskLevel === 'HIGH').length
  const mediumCount = predictions.filter(p => p.riskLevel === 'MEDIUM').length
  const avgConfidence = predictions.length
    ? ((predictions.reduce((s, p) => s + (p.confidence ?? 0), 0) / predictions.length) * 100).toFixed(0)
    : 0

  const chartData = sorted.map(p => ({
    name:      p.zoneName,
    doluluk:   parseFloat(((p.predictedLoad ?? 0) * 100).toFixed(1)),
    riskLevel: p.riskLevel,
  }))

  // AI özet
  const avgLoad = predictions.length
    ? (predictions.reduce((s, p) => s + (p.predictedLoad ?? 0), 0) / predictions.length * 100).toFixed(0)
    : 0
  const trend = highCount > 2 ? 'KÖTÜLEŞIYOR' : highCount > 0 ? 'DİKKAT' : 'İYİ'
  const trendColor = highCount > 2 ? 'text-red-400' : highCount > 0 ? 'text-amber-400' : 'text-eco-green'

  return (
    <div className="flex-1 p-6 space-y-6 overflow-auto">
      {/* Forecast Modal */}
      {forecastZone && (
        <ForecastModal
          prediction={forecastZone}
          onClose={() => setForecastZone(null)}
        />
      )}

      {/* Başlık */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">AI Tahminleri</h1>
          <p className="text-gray-400 text-sm mt-0.5">LSTM modeli — 30 dakika sonrası yoğunluk tahmini</p>
        </div>
        <div className="flex items-center gap-3">
          {lastUpdated && (
            <span className="text-gray-500 text-xs">
              Son: {formatTime(lastUpdated.toISOString())}
            </span>
          )}
          <button
            onClick={refresh}
            disabled={refreshing}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-eco-green/10
                       border border-eco-green/30 text-eco-green text-sm font-medium
                       hover:bg-eco-green/20 transition-colors disabled:opacity-50"
          >
            {refreshing ? (
              <span className="w-3.5 h-3.5 border-2 border-eco-green border-t-transparent rounded-full animate-spin" />
            ) : (
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
            )}
            {refreshing ? 'Yenileniyor...' : 'AI Yenile'}
          </button>
        </div>
      </div>

      {/* AI Özet Kartı */}
      <div className="bg-gray-800 rounded-xl p-4 border border-gray-700">
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-white font-semibold">Genel AI Durumu</h2>
          <span className={`text-sm font-bold ${trendColor}`}>{trend}</span>
        </div>
        <div className="grid grid-cols-3 gap-4">
          <div className="text-center">
            <p className="text-2xl font-bold text-white">%{avgLoad}</p>
            <p className="text-xs text-gray-400">Ort. Tahmin Doluluk</p>
          </div>
          <div className="text-center">
            <p className={`text-2xl font-bold ${highCount > 0 ? 'text-red-400' : 'text-eco-green'}`}>{highCount}</p>
            <p className="text-xs text-gray-400">Yüksek Risk Bölge</p>
          </div>
          <div className="text-center">
            <p className="text-2xl font-bold text-blue-400">%{avgConfidence}</p>
            <p className="text-xs text-gray-400">Model Güveni</p>
          </div>
        </div>
        {highRisk.length > 0 && (
          <div className="mt-3 pt-3 border-t border-gray-700">
            <p className="text-xs text-red-400">
              ⚠ Kritik: {highRisk.map(p => p.zoneName).join(', ')}
            </p>
          </div>
        )}
      </div>

      {/* HIGH risk alert */}
      {highRisk.length > 0 && (
        <div className="flex items-start gap-3 px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/30">
          <span className="relative flex h-3 w-3 mt-0.5">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-red-400 opacity-75" />
            <span className="relative inline-flex rounded-full h-3 w-3 bg-red-500" />
          </span>
          <div>
            <p className="text-red-400 font-semibold text-sm">
              {highRisk.length} bölge YÜKSEK RİSK seviyesinde!
            </p>
            <p className="text-red-400/70 text-xs mt-0.5">
              {highRisk.map(p => p.zoneName).join(', ')} — acil müdahale önerilir
            </p>
          </div>
        </div>
      )}

      {/* KPI Kartları */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <KpiCard title="Toplam Bölge" value={predictions.length} subtitle="tahmin edilen" icon="🔮" color="blue" />
        <KpiCard title="Yüksek Risk" value={highCount} subtitle={highCount > 0 ? 'acil müdahale' : 'kritik bölge yok'}
                 icon="🚨" color={highCount > 0 ? 'red' : 'green'} />
        <KpiCard title="Orta Risk" value={mediumCount} subtitle="takip edilmeli"
                 icon="⚠️" color={mediumCount > 0 ? 'orange' : 'green'} />
        <KpiCard title="Ortalama Güven" value={`%${avgConfidence}`} subtitle="model güveni" icon="📈" color="blue" />
      </div>

      {/* BarChart */}
      <div className="bg-gray-800 rounded-xl p-4 border border-gray-700">
        <h2 className="text-white font-semibold mb-1">Bölge Bazlı Tahmin Edilen Doluluk</h2>
        <p className="text-gray-500 text-xs mb-4">Kırmızı çizgi: %85 kritik eşik</p>
        <ResponsiveContainer width="100%" height={220}>
          <BarChart data={chartData} margin={{ top: 5, right: 10, left: -15, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
            <XAxis dataKey="name" tick={{ fill: '#9CA3AF', fontSize: 10 }} tickLine={false} />
            <YAxis tick={{ fill: '#9CA3AF', fontSize: 10 }} tickLine={false} axisLine={false}
                   domain={[0, 100]} tickFormatter={v => `${v}%`} />
            <Tooltip
              contentStyle={{ backgroundColor: '#1F2937', border: '1px solid #374151', borderRadius: '0.5rem', fontSize: '11px' }}
              formatter={(v) => [`%${v}`, 'Tahmini Doluluk']}
              labelStyle={{ color: '#F9FAFB', marginBottom: 4 }}
            />
            <ReferenceLine y={85} stroke="#E74C3C" strokeDasharray="4 4" strokeWidth={2}
              label={{ value: 'Kritik %85', fill: '#E74C3C', fontSize: 10, position: 'right' }} />
            <Bar dataKey="doluluk" radius={[4, 4, 0, 0]}>
              {chartData.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={BAR_COLOR[entry.riskLevel] ?? '#6B7280'} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
        <div className="flex items-center gap-4 mt-2 justify-center text-xs text-gray-400">
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded bg-red-500 inline-block" /> Yüksek Risk</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded bg-orange-400 inline-block" /> Orta Risk</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded bg-eco-green inline-block" /> Düşük Risk</span>
        </div>
      </div>

      {/* Prediction Card Grid */}
      <div>
        <h2 className="text-white font-semibold mb-3">Bölge Tahmin Kartları — Detaylı Tahmin için Karta Tıklayın</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {sorted.map(p => (
            <div key={p.zoneId} className="cursor-pointer" onClick={() => setForecastZone(p)}>
              <PredictionCard prediction={p} />
            </div>
          ))}
        </div>
        {predictions.length === 0 && (
          <div className="text-center py-12 text-gray-500 text-sm">
            Henüz tahmin verisi yok. "AI Yenile" butonuna basın.
          </div>
        )}
      </div>
    </div>
  )
}

function PageSkeleton() {
  return (
    <div className="flex-1 p-6 space-y-6 animate-pulse">
      <div className="h-8 w-48 bg-gray-700 rounded" />
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        {[1,2,3,4].map(i => (
          <div key={i} className="bg-gray-800 rounded-xl p-4 border border-gray-700">
            <div className="h-3 w-20 bg-gray-700 rounded mb-3" />
            <div className="h-7 w-14 bg-gray-700 rounded" />
          </div>
        ))}
      </div>
      <div className="bg-gray-800 rounded-xl p-4 border border-gray-700 h-56" />
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {[1,2,3,4,5,6].map(i => (
          <div key={i} className="bg-gray-800 rounded-xl p-4 border border-gray-700 h-48" />
        ))}
      </div>
    </div>
  )
}
