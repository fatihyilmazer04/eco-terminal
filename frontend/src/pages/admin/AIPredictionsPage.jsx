import React from 'react'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, Cell, ReferenceLine, Legend
} from 'recharts'
import KpiCard from '../../components/KpiCard'
import PredictionCard from '../../components/PredictionCard'
import { useAIPredictions } from '../../hooks/useAIPredictions'

const RISK_ORDER = { HIGH: 0, MEDIUM: 1, LOW: 2 }
const BAR_COLOR  = { HIGH: '#E74C3C', MEDIUM: '#F39C12', LOW: '#2ECC71' }

function formatTime(isoString) {
  if (!isoString) return '--'
  return new Date(isoString).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' })
}

export default function AIPredictionsPage() {
  const { predictions, highRisk, loading, error, lastUpdated, refresh, refreshing } =
    useAIPredictions()

  if (loading) return <PageSkeleton />

  if (error) return (
    <div className="flex-1 p-6">
      <div className="px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-400 text-sm">
        {error}
      </div>
    </div>
  )

  // Riske göre sıralı liste
  const sorted = [...predictions].sort(
    (a, b) => (RISK_ORDER[a.riskLevel] ?? 3) - (RISK_ORDER[b.riskLevel] ?? 3)
  )

  // KPI hesapları
  const highCount   = predictions.filter(p => p.riskLevel === 'HIGH').length
  const mediumCount = predictions.filter(p => p.riskLevel === 'MEDIUM').length
  const avgConfidence = predictions.length
    ? ((predictions.reduce((s, p) => s + (p.confidence ?? 0), 0) / predictions.length) * 100).toFixed(0)
    : 0

  // Grafik verisi
  const chartData = sorted.map(p => ({
    name:       p.zoneName,
    doluluk:    parseFloat(((p.predictedLoad ?? 0) * 100).toFixed(1)),
    riskLevel:  p.riskLevel,
  }))

  return (
    <div className="flex-1 p-6 space-y-6 overflow-auto">
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

      {/* HIGH risk alert banner */}
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
        <KpiCard
          title="Toplam Bölge"
          value={predictions.length}
          subtitle="tahmin edilen"
          icon="🔮"
          color="blue"
        />
        <KpiCard
          title="Yüksek Risk"
          value={highCount}
          subtitle={highCount > 0 ? 'acil müdahale' : 'kritik bölge yok'}
          icon="🚨"
          color={highCount > 0 ? 'red' : 'green'}
        />
        <KpiCard
          title="Orta Risk"
          value={mediumCount}
          subtitle="takip edilmeli"
          icon="⚠️"
          color={mediumCount > 0 ? 'orange' : 'green'}
        />
        <KpiCard
          title="Ortalama Güven"
          value={`%${avgConfidence}`}
          subtitle="model güveni"
          icon="📈"
          color="blue"
        />
      </div>

      {/* BarChart — tahmin edilen doluluk + kritik eşik çizgisi */}
      <div className="bg-gray-800 rounded-xl p-4 border border-gray-700">
        <h2 className="text-white font-semibold mb-1">Bölge Bazlı Tahmin Edilen Doluluk</h2>
        <p className="text-gray-500 text-xs mb-4">Kırmızı çizgi: %85 kritik eşik</p>
        <ResponsiveContainer width="100%" height={220}>
          <BarChart data={chartData} margin={{ top: 5, right: 10, left: -15, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
            <XAxis
              dataKey="name"
              tick={{ fill: '#9CA3AF', fontSize: 10 }}
              tickLine={false}
            />
            <YAxis
              tick={{ fill: '#9CA3AF', fontSize: 10 }}
              tickLine={false}
              axisLine={false}
              domain={[0, 100]}
              tickFormatter={v => `${v}%`}
            />
            <Tooltip
              contentStyle={{
                backgroundColor: '#1F2937',
                border: '1px solid #374151',
                borderRadius: '0.5rem',
                fontSize: '11px',
              }}
              formatter={(v, name) => [`%${v}`, 'Tahmini Doluluk']}
              labelStyle={{ color: '#F9FAFB', marginBottom: 4 }}
            />
            <ReferenceLine
              y={85}
              stroke="#E74C3C"
              strokeDasharray="4 4"
              strokeWidth={2}
              label={{ value: 'Kritik %85', fill: '#E74C3C', fontSize: 10, position: 'right' }}
            />
            <Bar dataKey="doluluk" radius={[4, 4, 0, 0]}>
              {chartData.map((entry, index) => (
                <Cell
                  key={`cell-${index}`}
                  fill={BAR_COLOR[entry.riskLevel] ?? '#6B7280'}
                />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
        {/* Legend */}
        <div className="flex items-center gap-4 mt-2 justify-center text-xs text-gray-400">
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded bg-red-500 inline-block" /> Yüksek Risk</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded bg-orange-400 inline-block" /> Orta Risk</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded bg-eco-green inline-block" /> Düşük Risk</span>
        </div>
      </div>

      {/* Prediction Card Grid — riske göre sıralı */}
      <div>
        <h2 className="text-white font-semibold mb-3">Bölge Tahmin Kartları</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {sorted.map(p => (
            <PredictionCard key={p.zoneId} prediction={p} />
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
