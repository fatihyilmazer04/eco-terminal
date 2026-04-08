import React from 'react'
import { useSearchParams, Link } from 'react-router-dom'
import { useSuggestedRoute, useAlternatives } from '../../hooks/useFlights'
import OccupancyCard from '../../components/OccupancyCard'

const LEVEL_COLORS = {
  LOW:      '#2ECC71',
  MEDIUM:   '#F39C12',
  HIGH:     '#E67E22',
  CRITICAL: '#E74C3C',
}

const LEVEL_LABELS = {
  LOW:      'Sakin',
  MEDIUM:   'Orta',
  HIGH:     'Yoğun',
  CRITICAL: 'Kritik',
}

function StepCard({ step }) {
  const isBusy = step.densityLevel === 'HIGH' || step.densityLevel === 'CRITICAL'
  const color  = LEVEL_COLORS[step.densityLevel] ?? '#9CA3AF'
  const label  = LEVEL_LABELS[step.densityLevel] ?? step.densityLevel

  return (
    <div className={`
      flex gap-4 p-4 rounded-xl border transition-colors
      ${isBusy
        ? 'bg-yellow-500/5 border-yellow-500/20'
        : 'bg-gray-800 border-gray-700'}
    `}>
      {/* Numara çemberi */}
      <div
        className="flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center
                   text-sm font-bold text-gray-900"
        style={{ backgroundColor: color }}
      >
        {step.stepNumber}
      </div>

      <div className="flex-1 min-w-0">
        <div className="flex items-start justify-between gap-2">
          <div>
            <p className="text-sm font-medium text-white">{step.instruction}</p>
            <p className="text-xs text-gray-400 mt-0.5">{step.zoneName}</p>
          </div>
          <div className="flex-shrink-0 flex flex-col items-end gap-1">
            {/* Density chip */}
            <span
              className="text-xs px-2 py-0.5 rounded-full font-medium"
              style={{ color, backgroundColor: color + '20', border: `1px solid ${color}40` }}
            >
              {label}
            </span>
            {/* Yürüyüş süresi */}
            <span className="text-xs text-gray-500">~{step.estimatedWalkMinutes} dk</span>
          </div>
        </div>

        {/* Uyarı ikonu — yoğun bölgeler için */}
        {isBusy && (
          <div className="mt-2 flex items-center gap-1.5 text-yellow-400 text-xs">
            <svg className="w-3.5 h-3.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
            </svg>
            Bu bölge yoğun — dikkatli ilerleyin
          </div>
        )}
      </div>
    </div>
  )
}

export default function RouteSuggestionPage() {
  const [searchParams] = useSearchParams()
  const { data: route, loading, error } = useSuggestedRoute()

  // Hedef kapı zoneId'yi alternatifler için bul
  const targetZoneId = route?.steps?.at(-1)
    ? null  // RouteResponse'ta zoneId yok, alternatifler genel olarak çekilecek
    : null
  const { data: alternatives } = useAlternatives(1) // Gate A1 default

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-900 p-6">
        <div className="h-7 w-48 bg-gray-700 rounded animate-pulse mb-6" />
        <div className="eco-card animate-pulse mb-4 h-24" />
        {[1, 2, 3, 4, 5].map(i => (
          <div key={i} className="eco-card animate-pulse mb-3 h-16" />
        ))}
      </div>
    )
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-900 p-6 flex items-center justify-center">
        <div className="eco-card text-center max-w-sm">
          <div className="text-4xl mb-3">✈️</div>
          <p className="text-gray-300 font-medium mb-1">Rota oluşturulamadı</p>
          <p className="text-gray-500 text-sm mb-4">{error}</p>
          <Link to="/passenger/flights" className="eco-btn-secondary text-sm">
            Uçuşlarıma Dön
          </Link>
        </div>
      </div>
    )
  }

  if (!route) return null

  const totalMins = parseInt(route.estimatedTotalWalkMinutes) || 0

  return (
    <div className="min-h-screen bg-gray-900 p-6">
      {/* Başlık */}
      <div className="flex items-center gap-3 mb-6">
        <Link to="/passenger/flights" className="text-gray-400 hover:text-white transition-colors">
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
        </Link>
        <div>
          <h1 className="text-2xl font-bold text-white">Rota Önerisi</h1>
          <p className="text-gray-400 text-sm">{route.destination}</p>
        </div>
      </div>

      {/* Üst Banner — tahmini varış */}
      <div className="mb-6 p-4 rounded-xl bg-eco-green/10 border border-eco-green/30 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-full bg-eco-green/20 flex items-center justify-center">
            <svg className="w-5 h-5 text-eco-green" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7" />
            </svg>
          </div>
          <div>
            <p className="text-eco-green font-semibold">
              {route.targetGate}'a tahmini varış: {route.estimatedTotalWalkMinutes}
            </p>
            <p className="text-gray-400 text-xs">
              Kalkışa {route.minutesToDeparture} dakika kaldı
            </p>
          </div>
        </div>
        <div className="text-right">
          <p className="text-xs text-gray-400">Uçuş</p>
          <p className="text-sm font-mono font-bold text-white">{route.flightId}</p>
        </div>
      </div>

      {/* Adım Adım Rota */}
      <h2 className="text-base font-semibold text-white mb-3">Güzergah</h2>
      <div className="flex flex-col gap-3 mb-8">
        {route.steps.map(step => (
          <StepCard key={step.stepNumber} step={step} />
        ))}
      </div>

      {/* Alternatif Sakin Alanlar */}
      {alternatives.length > 0 && (
        <>
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-base font-semibold text-white">Alternatif Sakin Alanlar</h2>
            <Link to="/passenger/lounges" className="text-sm text-eco-green hover:text-green-400">
              Tümünü Gör →
            </Link>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {alternatives.slice(0, 2).map(zone => (
              <OccupancyCard
                key={zone.zoneId}
                zoneName={zone.zoneName}
                type={zone.type}
                currentCount={zone.currentCount}
                maxCapacity={zone.maxCapacity}
                densityPct={zone.densityPct}
                densityLevel={zone.densityLevel}
                colorCode={zone.colorCode}
                criticalThreshold={zone.criticalThreshold}
                compact
              />
            ))}
          </div>
        </>
      )}
    </div>
  )
}
