import { useState, useEffect, useRef } from 'react'

/**
 * QR Tarayıcı Modal
 *
 * Modlar:
 *   1) Kamera  — html5-qrcode ile gerçek zamanlı kamera taraması
 *   2) Fotoğraf — dosya seçimi + canvas preprocessing (max 1024px, no smoothing, inversionAttempts)
 *   3) Yapıştır — JSON/token manuel girişi
 */

const MAX_SCAN_SIZE = 1024 // büyük fotoğrafları bu boyuta indir

/** Görsel dosyasını HTMLImageElement'e yükler */
function loadImage(file) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = reject
    img.src = URL.createObjectURL(file)
  })
}

/**
 * Fotoğrafı canvas'a çiz, QR için optimize et, ImageData döndür.
 * - Maksimum MAX_SCAN_SIZE × MAX_SCAN_SIZE ölçekler (oran korunur)
 * - imageSmoothingEnabled = false (piksel yumuşatma kapalı)
 */
function drawToCanvas(img) {
  let { naturalWidth: w, naturalHeight: h } = img
  if (w > MAX_SCAN_SIZE || h > MAX_SCAN_SIZE) {
    const ratio = Math.min(MAX_SCAN_SIZE / w, MAX_SCAN_SIZE / h)
    w = Math.round(w * ratio)
    h = Math.round(h * ratio)
  }
  const canvas = document.createElement('canvas')
  canvas.width  = w
  canvas.height = h
  const ctx = canvas.getContext('2d')
  ctx.imageSmoothingEnabled = false
  ctx.drawImage(img, 0, 0, w, h)
  return { canvas, ctx, w, h }
}

/** canvas'ı File/Blob'a çevirir (html5-qrcode scanFile için) */
function canvasToFile(canvas) {
  return new Promise((resolve) => {
    canvas.toBlob((blob) => {
      resolve(new File([blob], 'qr_scaled.png', { type: 'image/png' }))
    }, 'image/png')
  })
}

export default function QrScannerModal({ isOpen, onClose, onScan, expectedZone }) {
  const [mode, setMode] = useState('camera')   // 'camera' | 'photo' | 'paste'
  const [pasteValue, setPasteValue]   = useState('')
  const [cameraError, setCameraError] = useState(null)
  const [photoError, setPhotoError]   = useState(null)
  const [photoScanning, setPhotoScanning] = useState(false)
  const [scanning, setScanning] = useState(false)
  const scannerRef   = useRef(null)
  const fileInputRef = useRef(null)
  const hiddenDivId  = 'eco-qr-file-scanner-hidden'
  const scannerElId  = 'eco-qr-reader'

  // ── Kamera tarayıcı ────────────────────────────────────────────────────────
  useEffect(() => {
    if (!isOpen || mode !== 'camera') return

    let scanner = null
    setCameraError(null)
    setScanning(true)

    import('html5-qrcode')
      .then(({ Html5QrcodeScanner }) => {
        scanner = new Html5QrcodeScanner(
          scannerElId,
          { fps: 10, qrbox: { width: 220, height: 220 }, aspectRatio: 1.0 },
          false
        )
        scannerRef.current = scanner

        scanner.render(
          (decodedText) => {
            scanner.clear().catch(() => {})
            setScanning(false)
            onScan(decodedText)
            onClose()
          },
          () => { /* her frame'de tetiklenir, yoksay */ }
        )
      })
      .catch(() => {
        setCameraError('Kamera kütüphanesi yüklenemedi. Fotoğraf veya Yapıştır modunu kullanın.')
        setScanning(false)
      })

    return () => {
      if (scannerRef.current) {
        scannerRef.current.clear().catch(() => {})
        scannerRef.current = null
      }
      setScanning(false)
    }
  }, [isOpen, mode])

  // Modal kapanınca temizle
  useEffect(() => {
    if (!isOpen && scannerRef.current) {
      scannerRef.current.clear().catch(() => {})
      scannerRef.current = null
    }
  }, [isOpen])

  // ── Mod geçişi ─────────────────────────────────────────────────────────────
  const handleModeSwitch = (newMode) => {
    if (scannerRef.current) {
      scannerRef.current.clear().catch(() => {})
      scannerRef.current = null
    }
    setMode(newMode)
    setCameraError(null)
    setPhotoError(null)
  }

  // ── Fotoğraf yükleme + preprocessing ──────────────────────────────────────
  const handleFileChange = async (e) => {
    const file = e.target.files?.[0]
    if (!file) return

    setPhotoError(null)
    setPhotoScanning(true)

    try {
      const img = await loadImage(file)
      const { canvas } = drawToCanvas(img)
      URL.revokeObjectURL(img.src)

      const scaledFile = await canvasToFile(canvas)

      // html5-qrcode'un Html5Qrcode sınıfı ile decode
      const { Html5Qrcode } = await import('html5-qrcode')

      // Hidden div gerekli — görünmez tutuyoruz
      let hiddenDiv = document.getElementById(hiddenDivId)
      if (!hiddenDiv) {
        hiddenDiv = document.createElement('div')
        hiddenDiv.id = hiddenDivId
        hiddenDiv.style.display = 'none'
        document.body.appendChild(hiddenDiv)
      }

      const qrScanner = new Html5Qrcode(hiddenDivId)
      try {
        // showImage: false → DOM'a resim ekleme
        const result = await qrScanner.scanFile(scaledFile, false)
        onScan(result)
        onClose()
      } catch {
        setPhotoError('QR kod okunamadı. Lütfen kodu daha net ve yakın çekin veya manuel girin.')
      } finally {
        qrScanner.clear().catch(() => {})
      }
    } catch {
      setPhotoError('Görsel işlenirken hata oluştu. Farklı bir fotoğraf deneyin.')
    } finally {
      setPhotoScanning(false)
      // input'u sıfırla — aynı dosyayı tekrar seçmeye izin ver
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  // ── Yapıştır submit ────────────────────────────────────────────────────────
  const handlePasteSubmit = () => {
    const text = pasteValue.trim()
    if (!text) return
    setPasteValue('')
    onScan(text)
    onClose()
  }

  // ── Modal kapat ────────────────────────────────────────────────────────────
  const handleClose = () => {
    if (scannerRef.current) {
      scannerRef.current.clear().catch(() => {})
      scannerRef.current = null
    }
    setPasteValue('')
    setCameraError(null)
    setPhotoError(null)
    setMode('camera')
    onClose()
  }

  if (!isOpen) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
      <div className="w-full max-w-sm bg-gray-900 rounded-2xl border border-gray-700 shadow-2xl overflow-hidden">

        {/* Başlık */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-700">
          <div>
            <h2 className="text-base font-bold text-white">📷 QR Kod Tara</h2>
            {expectedZone && (
              <p className="text-xs text-gray-400 mt-0.5">
                Beklenen: <span className="text-eco-green font-medium">{expectedZone}</span>
              </p>
            )}
          </div>
          <button
            onClick={handleClose}
            className="w-8 h-8 flex items-center justify-center rounded-full text-gray-400
                       hover:text-white hover:bg-gray-700 transition-colors"
          >
            ✕
          </button>
        </div>

        {/* Mod Seçici */}
        <div className="flex gap-0 border-b border-gray-700">
          {[
            { key: 'camera', label: '📷 Kamera' },
            { key: 'photo',  label: '🖼️ Fotoğraf' },
            { key: 'paste',  label: '📋 Yapıştır' },
          ].map(({ key, label }) => (
            <button
              key={key}
              onClick={() => handleModeSwitch(key)}
              className={`flex-1 py-2.5 text-xs font-medium transition-colors
                ${mode === key
                  ? 'text-eco-green border-b-2 border-eco-green bg-eco-green/5'
                  : 'text-gray-400 hover:text-white'}`}
            >
              {label}
            </button>
          ))}
        </div>

        {/* İçerik */}
        <div className="p-5">

          {/* ── Kamera modu ── */}
          {mode === 'camera' && (
            <div>
              {cameraError ? (
                <div className="p-3 rounded-lg bg-red-500/10 border border-red-500/30 text-red-400 text-sm text-center">
                  {cameraError}
                </div>
              ) : (
                <>
                  <div
                    id={scannerElId}
                    className="overflow-hidden rounded-xl"
                    style={{ minHeight: '260px' }}
                  />
                  <p className="text-xs text-gray-500 text-center mt-3">
                    Kameranızı zone QR koduna tutun
                  </p>
                </>
              )}
            </div>
          )}

          {/* ── Fotoğraf modu ── */}
          {mode === 'photo' && (
            <div className="flex flex-col gap-4">
              <p className="text-sm text-gray-400">
                Galerinizden QR kod fotoğrafı seçin. Fotoğraf otomatik işlenip taranır.
              </p>

              {/* Gizli dosya input */}
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                capture="environment"
                onChange={handleFileChange}
                className="hidden"
                id="qr-photo-input"
              />

              <label
                htmlFor="qr-photo-input"
                className={`
                  w-full py-10 rounded-xl border-2 border-dashed flex flex-col items-center
                  justify-center gap-3 cursor-pointer transition-colors
                  ${photoScanning
                    ? 'border-eco-green/30 bg-eco-green/5 cursor-wait'
                    : 'border-gray-600 hover:border-eco-green/50 hover:bg-eco-green/5'}
                `}
              >
                {photoScanning ? (
                  <>
                    <span className="w-8 h-8 border-2 border-eco-green border-t-transparent rounded-full animate-spin" />
                    <span className="text-sm text-eco-green">QR kod taranıyor...</span>
                  </>
                ) : (
                  <>
                    <span className="text-3xl">📸</span>
                    <span className="text-sm text-gray-300 font-medium">Fotoğraf Seç veya Çek</span>
                    <span className="text-xs text-gray-500">Galeriden seç veya kamerayla çek</span>
                  </>
                )}
              </label>

              {photoError && (
                <div className="px-4 py-3 rounded-lg bg-red-500/10 border border-red-500/30 text-red-400 text-sm text-center">
                  {photoError}
                </div>
              )}

              <p className="text-xs text-gray-600 text-center">
                Büyük fotoğraflar otomatik olarak küçültülüp optimize edilir
              </p>
            </div>
          )}

          {/* ── Yapıştır modu ── */}
          {mode === 'paste' && (
            <div>
              <p className="text-sm text-gray-400 mb-3">
                Admin panelinden kopyaladığınız QR içeriğini yapıştırın:
              </p>
              <textarea
                className="w-full h-28 px-3 py-2 rounded-xl bg-gray-800 border border-gray-600
                           text-white text-xs font-mono placeholder-gray-500 resize-none
                           focus:outline-none focus:border-eco-green"
                placeholder={'{"token":"SEC1-A3F2B1","name":"Security-1","zoneId":2}'}
                value={pasteValue}
                onChange={e => setPasteValue(e.target.value)}
              />
              <button
                onClick={handlePasteSubmit}
                disabled={!pasteValue.trim()}
                className="mt-3 w-full py-2.5 rounded-xl text-sm font-semibold transition-all
                           bg-eco-green text-gray-900 hover:bg-green-400 active:scale-95
                           disabled:opacity-40 disabled:cursor-not-allowed"
              >
                Doğrula
              </button>
              <p className="text-xs text-gray-600 text-center mt-2">
                Demo kolaylığı — gerçek sistemde kamera kullanılır
              </p>
            </div>
          )}

        </div>
      </div>
    </div>
  )
}
