import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import { useSuggestedRoute, useAlternatives } from '../../hooks/useFlights'
import { useHeatmap } from '../../hooks/useHeatmap'
import OccupancyCard from '../../components/OccupancyCard'
import AirportHeatmap from '../../components/AirportHeatmap'
import { routeApi } from '../../api/routeApi'
import { useLoyaltyContext } from '../../context/LoyaltyContext'
import toast from 'react-hot-toast'

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

function StepCard({ step, isActive, isCompleted, journeyStarted, onCheckin, checkingIn }) {
  const isBusy = step.densityLevel === 'HIGH' || step.densityLevel === 'CRITICAL'
  const color  = LEVEL_COLORS[step.densityLevel] ?? '#9CA3AF'
  const label  = LEVEL_LABELS[step.densityLevel] ?? step.densityLevel
  const hasMap = step.posX != null && step.posY != null

  return (
    <div
      className={`
        flex gap-4 p-4 rounded-xl border transition-all
        ${isCompleted
          ? 'bg-eco-green/10 border-eco-green/30 opacity-80'
          : isActive
            ? 'bg-eco-green/10 border-eco-green/50 ring-1 ring-eco-green/30'
            : 'bg-gray-800 border-gray-700'}
      `}
    >
      {/* Numara / tamamlandı çemberi */}
      <div
        className="flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center
                   text-sm font-bold text-gray-900"
        style={{
          backgroundColor: isCompleted ? '#2ECC71' : isActive ? '#2ECC71' : '#374151',
          color: isCompleted || isActive ? '#111827' : '#9CA3AF',
        }}
      >
        {isCompleted ? '✓' : step.stepNumber}
      </div>

      <div className="flex-1 min-w-0">
        <div className="flex items-start justify-between gap-2">
          <div>
            <p className={`text-sm font-medium ${isCompleted ? 'text-gray-400 line-through' : 'text-white'}`}>
              {step.instruction}
            </p>
            <p className="text-xs text-gray-400 mt-0.5 flex items-center gap-1.5">
              {step.zoneName}
              {hasMap && (
                <span className="text-eco-green/60">● haritada</span>
              )}
            </p>
          </div>
          <div className="flex-shrink-0 flex flex-col items-end gap-1">
            <span
              className="text-xs px-2 py-0.5 rounded-full font-medium"
              style={{ color, backgroundColor: color + '20', border: `1px solid ${color}40` }}
            >
              {label}
            </span>
            <span className="text-xs text-gray-500">~{step.estimatedWalkMinutes} dk</span>
          </div>
        </div>

        {isBusy && !isCompleted && (
          <div className="mt-2 flex items-center gap-1.5 text-yellow-400 text-xs">
            <svg className="w-3.5 h-3.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
            </svg>
            Bu bölge yoğun — dikkatli ilerleyin
          </div>
        )}

        {/* "Buraya Ulaştım" butonu — yalnızca aktif adım */}
        {journeyStarted && isActive && !isCompleted && (
          <button
            onClick={onCheckin}
            disabled={checkingIn}
            className={`
              mt-3 w-full py-2 rounded-lg text-sm font-semibold transition-all
              flex items-center justify-center gap-2
              ${checkingIn
                ? 'bg-eco-green/40 text-gray-900 cursor-wait'
                : 'bg-eco-green text-gray-900 hover:bg-green-400 active:scale-95 shadow-md shadow-eco-green/20'}
            `}
          >
            {checkingIn ? (
              <>Kaydediliyor...</>
            ) : (
              <>📍 {step.zoneName} — Buraya Ulaştım</>
            )}
          </button>
        )}
      </div>
    </div>
  )
}

export default function RouteSuggestionPage() {
  const { data: route, loading, error } = useSuggestedRoute()
  const { data: heatmapData } = useHeatmap(60000)
  const { data: alternatives } = useAlternatives(1)
  const { refreshWallet } = useLoyaltyContext()

  const [journeyStarted,  setJourneyStarted]  = useState(false)
  const [activeStep,      setActiveStep]      = useState(null)
  const [completedSteps,  setCompletedSteps]  = useState(new Set())
  const [journeyComplete, setJourneyComplete] = useState(false)
  const [checkingIn,      setCheckingIn]      = useState(false)

  const handleStartJourney = () => {
    if (!route?.steps?.length) return
    setJourneyStarted(true)
    setActiveStep(route.steps[0].stepNumber)
  }

  const handleCheckin = async (step) => {
    if (checkingIn || !route) return
    setCheckingIn(true)
    try {
      await routeApi.checkinStep({
        flightId:   route.flightId,
        stepNumber: step.stepNumber,
        zoneName:   step.zoneName,
        totalSteps: route.steps.length,
      })

      const newCompleted = new Set(completedSteps)
      newCompleted.add(step.stepNumber)
      setCompletedSteps(newCompleted)

      // Son adım mı?
      const isLastStep = step.stepNumber === route.steps[route.steps.length - 1].stepNumber
      if (isLastStep) {
        // Rotayı tamamla
        try {
          const res = await routeApi.completeRoute(route.flightId)
          const data = res.data.data
          toast.success(
            `🏆 Rota tamamlandı! +${data.pointsEarned} Eko-Puan kazandınız! Toplam: ${data.newBalance}`,
            { duration: 5000 }
          )
          setJourneyComplete(true)
          setActiveStep(null)
          refreshWallet()   // Navbar'daki bakiyeyi anında güncelle
        } catch (err) {
          const msg = err.response?.data?.message ?? 'Rota tamamlanamadı'
          toast.error(msg)
        }
      } else {
        // Sonraki adıma geç
        const nextIdx = route.steps.findIndex(s => s.stepNumber === step.stepNumber) + 1
        if (nextIdx < route.steps.length) {
          setActiveStep(route.steps[nextIdx].stepNumber)
          toast.success(`✓ ${step.zoneName} tamamlandı!`)
        }
      }
    } catch (err) {
      const msg = err.response?.data?.message ?? 'Check-in başarısız'
      toast.error(msg)
    } finally {
      setCheckingIn(false)
    }
  }

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

  if (!route) return (
    <div className="min-h-screen bg-gray-900 p-6 flex items-center justify-center">
      <div className="eco-card text-center max-w-sm">
        <div className="text-4xl mb-3">✈️</div>
        <p className="text-gray-300 font-medium mb-1">Rota bulunamadı</p>
        <p className="text-gray-500 text-sm mb-4">Aktif bir uçuş biletiniz olmayabilir.</p>
        <Link to="/passenger/flights" className="eco-btn-secondary text-sm">
          Uçuşlarıma Dön
        </Link>
      </div>
    </div>
  )

  const heatmapZones  = heatmapData?.zones ?? []
  const hasMapCoords  = heatmapZones.some(z => z.posX != null)
  const totalSteps    = route.steps.length
  const doneCount     = completedSteps.size

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

      {/* Üst Banner */}
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

      {/* İlerleme çubuğu (yolculuk başladıktan sonra) */}
      {journeyStarted && (
        <div className="mb-5 p-4 rounded-xl bg-gray-800 border border-gray-700">
          <div className="flex items-center justify-between mb-2">
            <span className="text-sm font-medium text-white">
              {journeyComplete ? '🏆 Rota Tamamlandı!' : `İlerleme: ${doneCount}/${totalSteps} durak`}
            </span>
            <span className="text-xs text-eco-green font-mono">
              {journeyComplete ? '+50 Eko-Puan' : `${Math.round((doneCount / totalSteps) * 100)}%`}
            </span>
          </div>
          <div className="w-full h-2.5 bg-gray-700 rounded-full overflow-hidden">
            <div
              className="h-full bg-eco-green rounded-full transition-all duration-500"
              style={{ width: `${(doneCount / totalSteps) * 100}%` }}
            />
          </div>
          {!journeyComplete && activeStep != null && (
            <p className="text-xs text-gray-400 mt-2">
              Şu an: <span className="text-eco-green font-medium">
                {route.steps.find(s => s.stepNumber === activeStep)?.zoneName}
              </span> — hedefe ulaştığınızda "Buraya Ulaştım"a basın
            </p>
          )}
        </div>
      )}

      {/* Rotayı Başlat butonu (henüz başlamadıysa) */}
      {!journeyStarted && !journeyComplete && (
        <button
          onClick={handleStartJourney}
          className="w-full py-3.5 rounded-xl font-bold text-sm mb-6 transition-all
                     flex items-center justify-center gap-2
                     bg-eco-green text-gray-900 hover:bg-green-400 active:scale-95
                     shadow-lg shadow-eco-green/20"
        >
          🚶 Rotayı Başlat — Her Durağı Tek Tek Tamamla
        </button>
      )}

      {/* Tamamlandı banner */}
      {journeyComplete && (
        <div className="mb-6 p-4 rounded-xl bg-eco-green/15 border border-eco-green/40 text-center">
          <div className="text-3xl mb-1">🏆</div>
          <p className="text-eco-green font-bold text-lg">Tebrikler! Rotayı Tamamladınız</p>
          <p className="text-gray-400 text-sm mt-1">50 Eko-Puan hesabınıza eklendi</p>
        </div>
      )}

      {/* Adım Adım Rota */}
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-base font-semibold text-white">Güzergah</h2>
        <span className="text-xs text-gray-500">{totalSteps} durak · 🌿 50 puan</span>
      </div>
      <div className="flex flex-col gap-3 mb-6">
        {route.steps.map(step => (
          <StepCard
            key={step.stepNumber}
            step={step}
            isActive={step.stepNumber === activeStep}
            isCompleted={completedSteps.has(step.stepNumber)}
            journeyStarted={journeyStarted}
            onCheckin={() => handleCheckin(step)}
            checkingIn={checkingIn}
          />
        ))}
      </div>

      {/* ── Terminal Haritası ─────────────────────────────────────── */}
      {hasMapCoords && (
        <div className="mb-8">
          <div className="flex items-center justify-between mb-3">
            <div>
              <h2 className="text-base font-semibold text-white">Terminal Haritası</h2>
              <p className="text-xs text-gray-500 mt-0.5">
                {journeyStarted
                  ? 'Yeşil duraklar tamamlananları, parlayan durak aktif konumu gösterir'
                  : 'Rota üzerindeki duraklar haritada işaretlendi'}
              </p>
            </div>
            {journeyStarted && activeStep != null && !journeyComplete && (
              <div className="flex items-center gap-1.5 px-3 py-1 rounded-full
                              bg-eco-green/10 border border-eco-green/30">
                <span className="w-2 h-2 rounded-full bg-eco-green animate-pulse" />
                <span className="text-xs text-eco-green font-medium">
                  Durak {activeStep}
                </span>
              </div>
            )}
          </div>

          <AirportHeatmap
            zones={heatmapZones}
            onZoneClick={() => {}}
            selectedZoneId={null}
            routeSteps={route.steps}
            activeStepNumber={activeStep}
            completedSteps={completedSteps}
            onRouteStepClick={() => {}}
          />

          <p className="text-center text-xs text-gray-600 mt-2">
            {journeyStarted
              ? '✓ işaretli duraklar tamamlandı · Parlayan durak aktif hedefiniz'
              : 'Rotayı başlatarak her durağı adım adım tamamlayın'}
          </p>
        </div>
      )}

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
