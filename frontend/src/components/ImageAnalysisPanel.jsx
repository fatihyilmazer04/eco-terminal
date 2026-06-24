import React, { useState, useRef, useEffect, useCallback } from 'react'
import toast from 'react-hot-toast'
import { occupancyApi } from '../api/occupancyApi'
import { sendMessage } from '../api/chatbot'

// ── Risk renk yardımcısı ─────────────────────────────────────────────────────
function riskColor(level) {
  switch (level) {
    case 'CRITICAL': return 'text-red-400'
    case 'HIGH':     return 'text-orange-400'
    case 'MEDIUM':   return 'text-yellow-400'
    default:         return 'text-eco-green'
  }
}
function riskBg(level) {
  switch (level) {
    case 'CRITICAL': return 'bg-red-500/10 border-red-500/30'
    case 'HIGH':     return 'bg-orange-500/10 border-orange-500/30'
    case 'MEDIUM':   return 'bg-yellow-500/10 border-yellow-500/30'
    default:         return 'bg-eco-green/10 border-eco-green/30'
  }
}
function riskLabel(level) {
  switch (level) {
    case 'CRITICAL': return '🔴 KRİTİK'
    case 'HIGH':     return '🟠 YÜKSEK'
    case 'MEDIUM':   return '🟡 ORTA'
    default:         return '🟢 DÜŞÜK'
  }
}

// ── Canvas üzerine bbox çiz ───────────────────────────────────────────────────
function drawDetections(canvas, imageSrc, detections, result) {
  const ctx = canvas.getContext('2d')
  const img = new Image()
  img.onload = () => {
    // Canvas boyutunu görsele uydur (max 600px genişlik)
    const maxW = 600
    const scale = img.width > maxW ? maxW / img.width : 1
    canvas.width  = img.width  * scale
    canvas.height = img.height * scale

    ctx.drawImage(img, 0, 0, canvas.width, canvas.height)

    // Bounding box'ları çiz
    detections.forEach(det => {
      if (!det.bbox || det.bbox.length < 4) return
      const [x1, y1, x2, y2] = det.bbox.map(v => v * scale)
      const conf = det.confidence ?? 0

      // Yeşil kutu
      ctx.strokeStyle = '#00FF00'
      ctx.lineWidth   = 2
      ctx.strokeRect(x1, y1, x2 - x1, y2 - y1)

      // Yüzde etiketi
      const label = `${Math.round(conf * 100)}%`
      ctx.font      = `bold ${Math.max(10, 12 * scale)}px sans-serif`
      const tw      = ctx.measureText(label).width
      ctx.fillStyle = '#00FF00'
      ctx.fillRect(x1, y1 - 16 * scale, tw + 6, 16 * scale)
      ctx.fillStyle = '#000'
      ctx.fillText(label, x1 + 3, y1 - 3 * scale)
    })

    // Sol üst bilgi paneli
    if (result) {
      const panelH = 70 * scale
      ctx.fillStyle = 'rgba(0,0,0,0.65)'
      ctx.fillRect(0, 0, canvas.width, panelH)

      ctx.font      = `bold ${Math.max(11, 13 * scale)}px sans-serif`
      ctx.fillStyle = '#fff'
      const lineH   = 20 * scale
      ctx.fillText(`Zone: ${result.zoneName} | Kapasite: ${result.capacity}`, 8, lineH)
      ctx.fillText(`Tespit: ${result.peopleCount} kişi | Doluluk: ${(result.densityPct * 100).toFixed(1)}%`, 8, lineH * 2)
      ctx.fillStyle = result.riskLevel === 'LOW' ? '#2ECC71'
                    : result.riskLevel === 'MEDIUM' ? '#F1C40F'
                    : result.riskLevel === 'HIGH' ? '#E67E22' : '#E74C3C'
      ctx.fillText(`Risk: ${result.riskLevel} | ${result.source}`, 8, lineH * 3)
    }
  }
  img.src = imageSrc
}

// ── Ana bileşen ───────────────────────────────────────────────────────────────
export default function ImageAnalysisPanel({ onAnalysisComplete }) {
  const [open, setOpen]             = useState(false)
  const [zones, setZones]           = useState([])
  const [selectedZone, setSelected] = useState('')
  const [imageFile, setImageFile]   = useState(null)
  const [previewUrl, setPreviewUrl] = useState(null)
  const [loading, setLoading]       = useState(false)
  const [result, setResult]         = useState(null)
  const [routeLoading, setRouteLoading] = useState(false)
  const [routeResult, setRouteResult]   = useState(null)

  const canvasRef  = useRef(null)
  const fileRef    = useRef(null)

  // Zone listesini yükle
  useEffect(() => {
    occupancyApi.getZones()
      .then(res => {
        const list = res.data?.data ?? res.data ?? []
        setZones(list)
        if (list.length > 0) setSelected(String(list[0].zoneId))
      })
      .catch(err => console.error('[ImageAnalysisPanel] zone yüklenemedi', err))
  }, [])

  // Canvas'ı sonuç gelince güncelle
  useEffect(() => {
    if (result && previewUrl && canvasRef.current) {
      drawDetections(canvasRef.current, previewUrl, result.detections ?? [], result)
    }
  }, [result, previewUrl])

  // Dosya seç
  const handleFile = useCallback(e => {
    const file = e.target.files?.[0]
    if (!file) return
    if (!file.type.startsWith('image/')) {
      toast.error('Sadece JPEG/PNG görüntü yükleyebilirsiniz.')
      return
    }
    if (file.size > 10 * 1024 * 1024) {
      toast.error('Görüntü 10 MB\'dan büyük olamaz.')
      return
    }
    setImageFile(file)
    setPreviewUrl(URL.createObjectURL(file))
    setResult(null)
    setRouteResult(null)
    // Canvas'ı sıfırla
    if (canvasRef.current) {
      const ctx = canvasRef.current.getContext('2d')
      ctx.clearRect(0, 0, canvasRef.current.width, canvasRef.current.height)
    }
  }, [])

  // Analiz et
  const handleAnalyze = useCallback(async () => {
    if (!imageFile) { toast.error('Önce bir fotoğraf seçin.'); return }
    if (!selectedZone) { toast.error('Bir zone seçin.'); return }

    setLoading(true)
    setResult(null)
    setRouteResult(null)

    try {
      const base64 = await new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onload  = e => resolve(e.target.result)
        reader.onerror = reject
        reader.readAsDataURL(imageFile)
      })

      const res = await occupancyApi.analyzeImage(selectedZone, base64)
      const data = res.data?.data ?? res.data

      setResult(data)
      toast.success(`Analiz tamamlandı: ${data.peopleCount} kişi tespit edildi`)

      // Üst bileşene bildir (heatmap renk güncellemesi için)
      if (onAnalysisComplete) onAnalysisComplete(data)

    } catch (err) {
      const msg = err.response?.data?.message ?? err.message ?? 'Analiz başarısız'
      toast.error(`Hata: ${msg}`)
      console.error('[ImageAnalysisPanel] analyzeImage hatası', err)
    } finally {
      setLoading(false)
    }
  }, [imageFile, selectedZone, onAnalysisComplete])

  // Rota öner
  const handleRoute = useCallback(async () => {
    if (!result) return
    setRouteLoading(true)
    setRouteResult(null)

    try {
      const msg = `${result.zoneName} bölgesinden en az kalabalık kapıya nasıl gidebilirim?`
      const chatRes = await sendMessage(msg)

      setRouteResult({
        reply: chatRes.reply ?? 'Rota hesaplandı.',
        steps: chatRes.routeSteps ?? [],
      })
      toast.success('Rota önerisi hazır!')
    } catch (err) {
      toast.error('Rota servisi şu an yanıt vermiyor.')
      console.error('[ImageAnalysisPanel] rota hatası', err)
    } finally {
      setRouteLoading(false)
    }
  }, [result])

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div className="mb-6">
      {/* Aç/Kapat butonu */}
      <button
        onClick={() => setOpen(o => !o)}
        className="w-full flex items-center justify-between px-4 py-3 bg-gray-800 border
                   border-gray-700 rounded-xl hover:border-eco-green/50 transition-colors"
      >
        <span className="flex items-center gap-2 text-white font-semibold text-sm">
          <span>📸</span>
          Görüntü ile Doluluk Analizi
          {result && (
            <span className={`text-xs font-normal ml-2 ${riskColor(result.riskLevel)}`}>
              — {result.peopleCount} kişi tespit edildi
            </span>
          )}
        </span>
        <svg
          className={`w-4 h-4 text-gray-400 transition-transform ${open ? 'rotate-180' : ''}`}
          fill="none" stroke="currentColor" viewBox="0 0 24 24"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {/* Panel içeriği */}
      {open && (
        <div className="mt-2 p-4 bg-gray-800 border border-gray-700 rounded-xl space-y-4">

          {/* Kontroller: Zone seç + Dosya + Analiz */}
          <div className="flex flex-wrap items-center gap-3">
            {/* Zone dropdown */}
            <select
              value={selectedZone}
              onChange={e => setSelected(e.target.value)}
              className="bg-gray-700 border border-gray-600 text-white text-sm rounded-lg
                         px-3 py-2 focus:ring-1 focus:ring-eco-green focus:outline-none"
            >
              {zones.map(z => (
                <option key={z.zoneId} value={String(z.zoneId)}>
                  {z.zoneName} ({z.type})
                </option>
              ))}
            </select>

            {/* Dosya seç */}
            <input
              ref={fileRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              onChange={handleFile}
              className="hidden"
            />
            <button
              onClick={() => fileRef.current?.click()}
              className="flex items-center gap-1.5 px-3 py-2 bg-gray-700 hover:bg-gray-600
                         border border-gray-600 text-white text-sm rounded-lg transition-colors"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                      d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
              {imageFile ? imageFile.name.slice(0, 20) + (imageFile.name.length > 20 ? '…' : '') : 'Fotoğraf Seç'}
            </button>

            {/* Analiz et */}
            <button
              onClick={handleAnalyze}
              disabled={loading || !imageFile}
              className="flex items-center gap-1.5 px-4 py-2 bg-eco-green hover:bg-green-400
                         disabled:bg-gray-700 disabled:text-gray-500 text-gray-900 font-medium
                         text-sm rounded-lg transition-colors"
            >
              {loading ? (
                <>
                  <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/>
                  </svg>
                  YOLOv8 analiz ediyor...
                </>
              ) : (
                <>
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                          d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                  </svg>
                  Analiz Et
                </>
              )}
            </button>
          </div>

          {/* Görsel + Sonuç paneli */}
          {(previewUrl || result) && (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              {/* Canvas */}
              <div className="relative">
                {!result && previewUrl && (
                  <img
                    src={previewUrl}
                    alt="Önizleme"
                    className="w-full rounded-lg border border-gray-600 max-h-80 object-contain bg-black"
                  />
                )}
                <canvas
                  ref={canvasRef}
                  className={`rounded-lg border border-gray-600 max-w-full ${result ? 'block' : 'hidden'}`}
                  style={{ maxHeight: '320px', objectFit: 'contain' }}
                />
                {loading && (
                  <div className="absolute inset-0 flex items-center justify-center bg-black/50 rounded-lg">
                    <div className="text-center">
                      <svg className="w-8 h-8 animate-spin text-eco-green mx-auto mb-2" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/>
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/>
                      </svg>
                      <p className="text-sm text-gray-300">YOLOv8 analiz ediyor...</p>
                    </div>
                  </div>
                )}
              </div>

              {/* Sonuç kartı */}
              {result && (
                <div className={`border rounded-xl p-4 space-y-3 ${riskBg(result.riskLevel)}`}>
                  <h3 className="font-semibold text-white text-sm">Analiz Sonuçları</h3>

                  <div className="space-y-2 text-sm">
                    <div className="flex justify-between text-gray-300">
                      <span>Zone</span>
                      <span className="text-white font-medium">{result.zoneName}</span>
                    </div>
                    <div className="flex justify-between text-gray-300">
                      <span>Kapasite</span>
                      <span className="text-white font-medium">{result.capacity} kişi</span>
                    </div>
                    <div className="flex justify-between text-gray-300">
                      <span>Tespit</span>
                      <span className="text-white font-bold text-lg">{result.peopleCount} kişi</span>
                    </div>
                    <div className="flex justify-between text-gray-300">
                      <span>Doluluk</span>
                      <span className="text-white font-medium">
                        %{(result.densityPct * 100).toFixed(1)}
                      </span>
                    </div>
                    {/* Doluluk progress bar */}
                    <div className="h-2 bg-gray-700 rounded-full overflow-hidden">
                      <div
                        className="h-full rounded-full transition-all"
                        style={{
                          width: `${Math.min(100, result.densityPct * 100)}%`,
                          backgroundColor:
                            result.riskLevel === 'LOW'      ? '#2ECC71' :
                            result.riskLevel === 'MEDIUM'   ? '#F1C40F' :
                            result.riskLevel === 'HIGH'     ? '#E67E22' : '#E74C3C'
                        }}
                      />
                    </div>
                    <div className="flex justify-between text-gray-300">
                      <span>Risk</span>
                      <span className={`font-semibold ${riskColor(result.riskLevel)}`}>
                        {riskLabel(result.riskLevel)}
                      </span>
                    </div>
                    <div className="flex justify-between text-gray-300">
                      <span>Kaynak</span>
                      <span className="text-white text-xs">{result.source}</span>
                    </div>
                    <div className="flex justify-between text-gray-300">
                      <span>Bbox</span>
                      <span className="text-white">{result.detections?.length ?? 0} adet</span>
                    </div>
                  </div>

                  {/* Rota öner butonu */}
                  <button
                    onClick={handleRoute}
                    disabled={routeLoading}
                    className="w-full mt-2 flex items-center justify-center gap-2 px-3 py-2
                               bg-gray-700 hover:bg-gray-600 disabled:bg-gray-800
                               disabled:text-gray-600 text-white text-sm rounded-lg
                               border border-gray-600 transition-colors"
                  >
                    {routeLoading ? (
                      <>
                        <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/>
                          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/>
                        </svg>
                        Rota hesaplanıyor...
                      </>
                    ) : (
                      <>🗺️ Rota Öner</>
                    )}
                  </button>
                </div>
              )}
            </div>
          )}

          {/* Rota sonucu */}
          {routeResult && (
            <div className="border border-eco-green/30 bg-eco-green/5 rounded-xl p-4 space-y-3">
              <h3 className="text-eco-green font-semibold text-sm flex items-center gap-2">
                🗺️ Önerilen Rota
              </h3>
              <p className="text-gray-300 text-sm">{routeResult.reply}</p>
              {routeResult.steps.length > 0 && (
                <ol className="space-y-1.5">
                  {routeResult.steps.map((step, i) => (
                    <li key={i} className="flex items-start gap-2 text-sm">
                      <span className="flex-shrink-0 w-5 h-5 rounded-full bg-eco-green/20
                                       border border-eco-green text-eco-green text-xs
                                       flex items-center justify-center font-bold">
                        {step.stepNumber ?? i + 1}
                      </span>
                      <div>
                        <span className="text-white font-medium">{step.zoneName}</span>
                        {step.instruction && (
                          <span className="text-gray-400 ml-1">— {step.instruction}</span>
                        )}
                        {step.estimatedWalkMinutes > 0 && (
                          <span className="text-gray-500 text-xs ml-1">
                            (~{step.estimatedWalkMinutes} dk)
                          </span>
                        )}
                      </div>
                    </li>
                  ))}
                </ol>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
