import React, { useState, useEffect, useCallback } from 'react'
import { useLocation } from 'react-router-dom'
import toast from 'react-hot-toast'
import { useOccupancy } from '../../hooks/useOccupancy'
import OccupancyCard from '../../components/OccupancyCard'
import AirportHeatmap from '../../components/AirportHeatmap'
import ZoneDetailPanel from '../../components/ZoneDetailPanel'
import ImageAnalysisPanel from '../../components/ImageAnalysisPanel'
import { useHeatmap } from '../../hooks/useHeatmap'

function SkeletonCard() {
  return (
    <div className="eco-card animate-pulse">
      <div className="flex items-start justify-between mb-4">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 bg-gray-700 rounded-lg" />
          <div>
            <div className="h-4 w-24 bg-gray-700 rounded mb-1" />
            <div className="h-3 w-16 bg-gray-700 rounded" />
          </div>
        </div>
        <div className="h-5 w-16 bg-gray-700 rounded-full" />
      </div>
      <div className="flex items-end justify-between mb-3">
        <div className="h-8 w-20 bg-gray-700 rounded" />
        <div className="h-6 w-10 bg-gray-700 rounded" />
      </div>
      <div className="h-2 bg-gray-700 rounded-full" />
    </div>
  )
}

export default function HeatmapPage() {
  const { data, loading, error, lastUpdated, refetch } = useOccupancy(15000)
  const { data: heatmapData, refetch: refetchHeatmap } = useHeatmap(15000)
  const [selectedZoneId, setSelectedZoneId] = useState(null)

  // ── Chatbot rota state'i (Adım 5.4) ────────────────────────────────────────
  const location = useLocation()
  const routeFromChatbot = location.state?.routeFromChatbot

  const [chatbotRoute, setChatbotRoute]         = useState(null)
  const [activeStepNumber, setActiveStepNumber] = useState(null)
  const [completedSteps, setCompletedSteps]     = useState(new Set())

  // Chatbot rotası + zone koordinatları hazır olunca zenginleştir
  useEffect(() => {
    if (!Array.isArray(routeFromChatbot) || routeFromChatbot.length === 0) return
    if (!heatmapData?.zones?.length) return   // zone verisi henüz yüklenmediyse bekle

    const enriched = routeFromChatbot.map((step, idx) => {
      const zone = heatmapData.zones.find(z => z.zoneName === step.zoneName)
      if (!zone) {
        console.warn('zone_not_found_for_step', step.zoneName)
        return null
      }
      return {
        stepNumber:           step.stepNumber ?? idx + 1,
        zoneName:             step.zoneName,
        instruction:          step.instruction ?? '',
        estimatedWalkMinutes: step.estimatedWalkMinutes ?? 0,
        // Merkez koordinat: sol-üst + yarı boyut (%→SVG hesabı AirportHeatmap'te yapılıyor)
        posX: (zone.posX ?? 0) + ((zone.width ?? 0) / 2),
        posY: (zone.posY ?? 0) + ((zone.height ?? 0) / 2),
      }
    }).filter(Boolean)

    if (enriched.length === 0) {
      toast.error('Rota zone\'ları haritada bulunamadı — zone isimleri eşleşmedi')
      return
    }

    setChatbotRoute(enriched)
    setActiveStepNumber(enriched[0].stepNumber)
    setCompletedSteps(new Set())

    toast.success(
      `📍 Rota yüklendi: ${enriched.length} adım`,
      { duration: 4000 }
    )

    // URL state'i temizle → sayfa yenilenince tekrar tetiklenmesin
    window.history.replaceState({}, document.title)
  }, [routeFromChatbot, heatmapData?.zones])

  // ── Rota adım yönetimi ──────────────────────────────────────────────────────

  function handleRouteStepClick(stepNumber) {
    setActiveStepNumber(stepNumber)
  }

  function handleNextStep() {
    if (activeStepNumber == null || !chatbotRoute) return
    const idx = chatbotRoute.findIndex(s => s.stepNumber === activeStepNumber)
    if (idx === -1) return
    const newCompleted = new Set(completedSteps)
    newCompleted.add(activeStepNumber)
    setCompletedSteps(newCompleted)
    const next = chatbotRoute[idx + 1]
    setActiveStepNumber(next ? next.stepNumber : null)
    if (!next) toast.success('🎉 Hedefe ulaştınız!', { duration: 4000 })
  }

  function handleResetRoute() {
    setActiveStepNumber(chatbotRoute?.[0]?.stepNumber ?? null)
    setCompletedSteps(new Set())
  }

  function handleClearRoute() {
    setChatbotRoute(null)
    setActiveStepNumber(null)
    setCompletedSteps(new Set())
  }

  // ── Görüntü analizi tamamlandığında backend'den güncel veri çek ─────────────
  // Override state kaldırıldı: backend artık gerçek occupancy_readings döndürüyor.
  // Analiz tamamlanınca her iki hook'u refetch et — polling beklemeden anlık güncelleme.
  const handleAnalysisComplete = useCallback(() => {
    refetchHeatmap()
    refetch()
  }, [refetchHeatmap, refetch])

  // ── Hesaplamalar ────────────────────────────────────────────────────────────
  const zones          = data?.zones ?? []
  const heatmapZones   = heatmapData?.zones ?? []
  const totalRouteMins = chatbotRoute?.reduce((s, x) => s + (x.estimatedWalkMinutes ?? 0), 0) ?? 0

  return (
    <div className="min-h-screen bg-gray-900 p-6">

      {/* ── Başlık ─────────────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-white">Terminal Yoğunluk Haritası</h1>
          <p className="text-gray-400 text-sm mt-0.5">Her 15 saniyede bir güncelleniyor</p>
        </div>

        <div className="flex items-center gap-3">
          <div className="flex items-center gap-1.5 text-sm text-gray-400">
            <span className="relative flex h-2 w-2">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-eco-green opacity-75" />
              <span className="relative inline-flex rounded-full h-2 w-2 bg-eco-green" />
            </span>
            Canlı
          </div>
          {lastUpdated && (
            <span className="text-xs text-gray-500 bg-gray-800 px-2 py-1 rounded">
              {lastUpdated.toLocaleTimeString('tr-TR')}
            </span>
          )}
          <button
            onClick={refetch}
            className="text-gray-400 hover:text-white transition-colors p-1.5 rounded hover:bg-gray-700"
            title="Yenile"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>
        </div>
      </div>

      {/* ── Görüntü Analizi Paneli ─────────────────────────────────────────── */}
      <ImageAnalysisPanel onAnalysisComplete={handleAnalysisComplete} />


      {/* ── Hata durumu ────────────────────────────────────────────────────── */}
      {error && !loading && (
        <div className="mb-6 px-4 py-3 rounded-xl bg-yellow-500/10 border border-yellow-500/30 flex items-center gap-3">
          <svg className="w-5 h-5 text-yellow-400" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
          </svg>
          <p className="text-yellow-400 text-sm">
            Veri alınamadı, yeniden deneniyor...{' '}
            <button onClick={refetch} className="underline ml-1">Şimdi dene</button>
          </p>
        </div>
      )}

      {/* ── SVG Heatmap (koordinatı olan zone'lar için) ─────────────────────── */}
      {heatmapZones.some(z => z.posX != null) && (
        <div className="mb-6">
          <div className="flex items-center justify-between mb-2">
            <h2 className="text-base font-semibold text-white">
              {chatbotRoute ? '🗺️ Rota Haritası' : 'Terminal Haritası'}
            </h2>
            {selectedZoneId && !chatbotRoute && (
              <button onClick={() => setSelectedZoneId(null)}
                className="text-xs text-gray-500 hover:text-gray-300">
                Seçimi temizle
              </button>
            )}
          </div>

          {/* ── Chatbot rota kontrol paneli ────────────────────────────────── */}
          {chatbotRoute && (
            <div className="mb-4 bg-gray-800 border border-eco-green/30 rounded-xl p-3">
              {/* Panel başlığı */}
              <div className="flex items-center justify-between mb-2.5">
                <div className="flex items-center gap-2">
                  <span className="text-base">🤖</span>
                  <span className="font-semibold text-white text-sm">Chatbot Rotası</span>
                  <span className="text-xs text-gray-400">
                    ({chatbotRoute.length} adım{totalRouteMins > 0 ? `, ~${totalRouteMins} dk` : ''})
                  </span>
                </div>
                <button
                  onClick={handleClearRoute}
                  className="text-xs text-gray-500 hover:text-red-400 px-2 py-1 rounded transition-colors"
                >
                  ✕ Temizle
                </button>
              </div>

              {/* Adım göstergesi */}
              <div className="flex items-center gap-1.5 text-sm flex-wrap mb-2.5">
                {chatbotRoute.map((step, idx) => (
                  <span key={idx} className="flex items-center gap-1.5">
                    <button
                      onClick={() => setActiveStepNumber(step.stepNumber)}
                      className={`inline-flex items-center justify-center w-6 h-6 rounded-full text-xs font-bold transition-all ${
                        completedSteps.has(step.stepNumber)
                          ? 'bg-eco-green text-gray-900'
                          : activeStepNumber === step.stepNumber
                          ? 'bg-eco-green/20 border-2 border-eco-green text-eco-green'
                          : 'bg-gray-700 border border-gray-600 text-gray-400'
                      }`}
                      title={step.zoneName}
                    >
                      {completedSteps.has(step.stepNumber) ? '✓' : step.stepNumber}
                    </button>
                    <span className={`text-xs ${
                      activeStepNumber === step.stepNumber
                        ? 'font-semibold text-eco-green'
                        : completedSteps.has(step.stepNumber)
                        ? 'text-gray-500 line-through'
                        : 'text-gray-400'
                    }`}>
                      {step.zoneName}
                    </span>
                    {idx < chatbotRoute.length - 1 && (
                      <span className="text-gray-600 text-xs">→</span>
                    )}
                  </span>
                ))}
              </div>

              {/* Kontrol butonları */}
              <div className="flex gap-2">
                <button
                  onClick={handleNextStep}
                  disabled={activeStepNumber == null}
                  className="px-3 py-1.5 text-xs bg-eco-green hover:bg-green-400 disabled:bg-gray-700
                             disabled:text-gray-500 text-gray-900 font-medium rounded-lg transition-colors"
                >
                  ➜ Sonraki Adım
                </button>
                <button
                  onClick={handleResetRoute}
                  className="px-3 py-1.5 text-xs bg-gray-700 hover:bg-gray-600 border border-gray-600
                             text-gray-300 rounded-lg transition-colors"
                >
                  ↺ Sıfırla
                </button>
              </div>
            </div>
          )}

          {/* ── Harita + Zone detay paneli ─────────────────────────────────── */}
          <div className={`grid grid-cols-1 gap-4 ${selectedZoneId && !chatbotRoute ? 'xl:grid-cols-4' : ''}`}>
            <div className={selectedZoneId && !chatbotRoute ? 'xl:col-span-3' : ''}>
              <AirportHeatmap
                zones={heatmapZones}
                onZoneClick={id => {
                  if (!chatbotRoute) setSelectedZoneId(prev => prev === id ? null : id)
                }}
                selectedZoneId={chatbotRoute ? null : selectedZoneId}
                routeSteps={chatbotRoute ?? undefined}
                activeStepNumber={chatbotRoute ? activeStepNumber : undefined}
                completedSteps={chatbotRoute ? completedSteps : new Set()}
                onRouteStepClick={chatbotRoute ? handleRouteStepClick : undefined}
              />
            </div>
            {selectedZoneId && !chatbotRoute && (
              <div className="xl:col-span-1">
                <ZoneDetailPanel
                  zone={heatmapZones.find(z => z.zoneId === selectedZoneId)}
                  onClose={() => setSelectedZoneId(null)}
                />
              </div>
            )}
          </div>
        </div>
      )}

      {/* ── Zone kartları grid ──────────────────────────────────────────────── */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {loading
          ? Array.from({ length: 4 }).map((_, i) => <SkeletonCard key={i} />)
          : zones.map(zone => (
              <div key={zone.zoneId} className="relative">
                <OccupancyCard
                  zoneName={zone.zoneName}
                  type={zone.type}
                  currentCount={zone.currentCount}
                  maxCapacity={zone.maxCapacity}
                  densityPct={zone.densityPct}
                  densityLevel={zone.densityLevel}
                  colorCode={zone.colorCode}
                  criticalThreshold={zone.criticalThreshold}
                  lastUpdated={zone.lastUpdated}
                />
              </div>
            ))
        }
      </div>

      {/* ── Renk açıklaması ────────────────────────────────────────────────── */}
      {!loading && !chatbotRoute && (
        <div className="mt-6 flex flex-wrap gap-4 justify-center">
          {[
            { color: '#2ECC71', label: 'Düşük (<%60)' },
            { color: '#F39C12', label: 'Orta (%60–84)' },
            { color: '#E67E22', label: 'Yüksek (%85–94)' },
            { color: '#E74C3C', label: 'Kritik (≥%95)' },
          ].map(item => (
            <div key={item.label} className="flex items-center gap-1.5 text-xs text-gray-400">
              <span className="w-3 h-3 rounded-full" style={{ backgroundColor: item.color }} />
              {item.label}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
