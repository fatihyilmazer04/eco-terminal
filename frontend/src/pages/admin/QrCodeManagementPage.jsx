import { useState, useEffect } from 'react'
import { qrApi } from '../../api/qrApi'
import toast from 'react-hot-toast'

/**
 * Admin: QR Kod Yönetimi Sayfası
 *
 * - Tüm zone'ların QR kodlarını grid halinde gösterir
 * - "Yeni QR Ekle" butonu → zone dropdown modal → üret
 * - Her kartın "Sil" butonu → onay → token temizle
 * - "Kopyala" ve "İndir" butonları
 */
export default function QrCodeManagementPage() {
  const [zones, setZones]           = useState([])
  const [loading, setLoading]       = useState(true)
  const [error, setError]           = useState(null)
  const [QRCodeCanvas, setQRCodeCanvas] = useState(null)

  // Modal state
  const [showModal, setShowModal]   = useState(false)
  const [allZones, setAllZones]     = useState([])
  const [selectedZoneId, setSelectedZoneId] = useState('')
  const [generating, setGenerating] = useState(false)

  // Delete state
  const [deletingId, setDeletingId] = useState(null)

  // qrcode.react'ı dinamik olarak yükle
  useEffect(() => {
    import('qrcode.react')
      .then(mod => setQRCodeCanvas(() => mod.QRCodeCanvas))
      .catch(() => setError('QR kütüphanesi yüklenemedi.'))
  }, [])

  const fetchQrCodes = () => {
    setLoading(true)
    qrApi.getZoneQrCodes()
      .then(res => setZones(res.data.data ?? []))
      .catch(err => setError(err.response?.data?.message ?? 'QR kodlar yüklenemedi'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { fetchQrCodes() }, [])

  // Modal aç → tüm zone listesini çek
  const openModal = () => {
    setSelectedZoneId('')
    setShowModal(true)
    qrApi.getAllZones()
      .then(res => setAllZones(res.data.data ?? []))
      .catch(() => toast.error('Zone listesi yüklenemedi'))
  }

  const handleGenerate = () => {
    if (!selectedZoneId) { toast.error('Lütfen bir zone seçin'); return }
    setGenerating(true)
    qrApi.generateQrToken(Number(selectedZoneId))
      .then(() => {
        toast.success('QR token üretildi!')
        setShowModal(false)
        fetchQrCodes()
      })
      .catch(err => toast.error(err.response?.data?.message ?? 'QR üretilemedi'))
      .finally(() => setGenerating(false))
  }

  const handleDelete = (zone) => {
    if (!window.confirm(`"${zone.zoneName}" zone'unun QR token'ı silinecek. Devam edilsin mi?`)) return
    setDeletingId(zone.zoneId)
    qrApi.deleteQrToken(zone.zoneId)
      .then(() => {
        toast.success(`${zone.zoneName} QR token silindi`)
        fetchQrCodes()
      })
      .catch(err => toast.error(err.response?.data?.message ?? 'Silme başarısız'))
      .finally(() => setDeletingId(null))
  }

  const handleCopy = (zone) => {
    navigator.clipboard.writeText(zone.qrContent)
      .then(() => toast.success(`${zone.zoneName} QR içeriği kopyalandı!`))
      .catch(() => toast.error('Kopyalama başarısız'))
  }

  const handleDownload = (zone) => {
    const canvas = document.getElementById(`qr-${zone.zoneId}`)
    if (!canvas) { toast.error('QR bulunamadı'); return }
    const url = canvas.toDataURL('image/png')
    const a = document.createElement('a')
    a.href = url
    a.download = `qr-${zone.zoneName.toLowerCase().replace(/\s+/g, '-')}.png`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
  }

  const handlePrint = () => window.print()

  const typeColor = (type) => {
    if (type === 'GATE')     return 'text-blue-400 bg-blue-400/10'
    if (type === 'SECURITY') return 'text-orange-400 bg-orange-400/10'
    if (type === 'CHECKIN')  return 'text-purple-400 bg-purple-400/10'
    if (type === 'LOUNGE')   return 'text-green-400 bg-green-400/10'
    return 'text-gray-400 bg-gray-400/10'
  }

  // Modal içindeki dropdown: QR'ı olmayan zone'lar önce, olanlarda "üzerine yaz" notu
  const zonesWithoutQr = allZones.filter(z => !z.hasQr)
  const zonesWithQr    = allZones.filter(z => z.hasQr)

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-900 p-6">
        <div className="h-7 w-64 bg-gray-700 rounded animate-pulse mb-6" />
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
          {Array.from({ length: 15 }).map((_, i) => (
            <div key={i} className="bg-gray-800 rounded-xl p-4 animate-pulse h-56" />
          ))}
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-900 p-6 flex items-center justify-center">
        <div className="text-center">
          <p className="text-red-400 font-medium mb-2">Hata</p>
          <p className="text-gray-500 text-sm">{error}</p>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gray-900 p-6">
      {/* Başlık */}
      <div className="flex items-center justify-between mb-6 print:hidden">
        <div>
          <h1 className="text-2xl font-bold text-white">QR Kod Yönetimi</h1>
          <p className="text-gray-400 text-sm mt-1">
            Zone doğrulama QR kodları — yazdırın ve zone girişlerine yerleştirin
          </p>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-xs text-gray-500 font-mono">{zones.length} zone</span>
          <button
            onClick={openModal}
            className="px-4 py-2 rounded-lg bg-eco-green text-gray-900 text-sm font-semibold
                       hover:bg-eco-green/80 transition-colors"
          >
            + Yeni QR Ekle
          </button>
          <button
            onClick={handlePrint}
            className="px-4 py-2 rounded-lg bg-gray-700 text-white text-sm font-medium
                       hover:bg-gray-600 transition-colors"
          >
            Tümünü Yazdır
          </button>
        </div>
      </div>

      {/* Zone tipine göre bölümler */}
      {['GATE', 'SECURITY', 'CHECKIN', 'LOUNGE'].map(type => {
        const typeZones = zones.filter(z => z.zoneType === type)
        if (typeZones.length === 0) return null

        const typeLabel = {
          GATE: 'Kapılar', SECURITY: 'Güvenlik',
          CHECKIN: 'Check-In', LOUNGE: 'Bekleme Salonları',
        }[type]

        return (
          <div key={type} className="mb-8">
            <h2 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-3">
              {typeLabel}
            </h2>
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
              {typeZones.map(zone => (
                <div
                  key={zone.zoneId}
                  className="bg-gray-800 border border-gray-700 rounded-xl p-4 flex flex-col items-center gap-3
                             hover:border-gray-600 transition-colors print:border print:border-gray-300 print:bg-white"
                >
                  {/* QR Kod */}
                  <div className="p-2 bg-white rounded-lg">
                    {QRCodeCanvas ? (
                      <QRCodeCanvas
                        id={`qr-${zone.zoneId}`}
                        value={zone.qrContent}
                        size={140}
                        bgColor="#ffffff"
                        fgColor="#111827"
                        level="M"
                      />
                    ) : (
                      <div className="w-[140px] h-[140px] bg-gray-100 flex items-center justify-center text-xs text-gray-500">
                        QR yükleniyor...
                      </div>
                    )}
                  </div>

                  {/* Zone adı ve tipi */}
                  <div className="text-center">
                    <p className="text-white font-semibold text-sm">{zone.zoneName}</p>
                    <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${typeColor(zone.zoneType)}`}>
                      {zone.zoneType}
                    </span>
                  </div>

                  {/* Token */}
                  <p className="text-xs font-mono text-gray-500">{zone.qrToken}</p>

                  {/* Butonlar */}
                  <div className="flex gap-1.5 w-full print:hidden">
                    <button
                      onClick={() => handleCopy(zone)}
                      title="QR içeriğini panoya kopyala"
                      className="flex-1 py-1.5 rounded-lg bg-gray-700 text-gray-300 text-xs font-medium
                                 hover:bg-gray-600 hover:text-white transition-colors"
                    >
                      Kopyala
                    </button>
                    <button
                      onClick={() => handleDownload(zone)}
                      title="PNG olarak indir"
                      className="flex-1 py-1.5 rounded-lg bg-eco-green/10 text-eco-green text-xs font-medium
                                 hover:bg-eco-green/20 transition-colors border border-eco-green/30"
                    >
                      İndir
                    </button>
                    <button
                      onClick={() => handleDelete(zone)}
                      disabled={deletingId === zone.zoneId}
                      title="QR token'ı sil"
                      className="py-1.5 px-2 rounded-lg bg-red-500/10 text-red-400 text-xs font-medium
                                 hover:bg-red-500/20 transition-colors border border-red-500/30
                                 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {deletingId === zone.zoneId ? '...' : 'Sil'}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )
      })}

      {zones.length === 0 && (
        <div className="text-center py-16">
          <p className="text-gray-500">QR token atanmış zone bulunamadı.</p>
          <p className="text-gray-600 text-sm mt-1">
            "Yeni QR Ekle" butonuyla zone'lara QR token atayabilirsiniz.
          </p>
        </div>
      )}

      {/* Yeni QR Ekle Modal */}
      {showModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
          <div className="bg-gray-800 border border-gray-700 rounded-2xl p-6 w-full max-w-md shadow-2xl">
            <h2 className="text-lg font-bold text-white mb-1">Yeni QR Token Üret</h2>
            <p className="text-gray-400 text-sm mb-5">
              Zone seçin — otomatik QR token üretilir ve atanır.
            </p>

            <label className="block text-xs text-gray-400 font-medium mb-1.5">Zone</label>
            <select
              value={selectedZoneId}
              onChange={e => setSelectedZoneId(e.target.value)}
              className="w-full bg-gray-900 border border-gray-600 text-white text-sm rounded-lg px-3 py-2.5
                         focus:outline-none focus:ring-2 focus:ring-eco-green/50 mb-5"
            >
              <option value="">-- Zone seçin --</option>
              {zonesWithoutQr.length > 0 && (
                <optgroup label="QR Token Yok">
                  {zonesWithoutQr.map(z => (
                    <option key={z.zoneId} value={z.zoneId}>
                      {z.zoneName} ({z.zoneType})
                    </option>
                  ))}
                </optgroup>
              )}
              {zonesWithQr.length > 0 && (
                <optgroup label="Mevcut QR Var (Üzerine Yazar)">
                  {zonesWithQr.map(z => (
                    <option key={z.zoneId} value={z.zoneId}>
                      {z.zoneName} ({z.zoneType})
                    </option>
                  ))}
                </optgroup>
              )}
            </select>

            <div className="flex gap-3">
              <button
                onClick={() => setShowModal(false)}
                className="flex-1 py-2.5 rounded-lg bg-gray-700 text-white text-sm font-medium
                           hover:bg-gray-600 transition-colors"
              >
                İptal
              </button>
              <button
                onClick={handleGenerate}
                disabled={generating || !selectedZoneId}
                className="flex-1 py-2.5 rounded-lg bg-eco-green text-gray-900 text-sm font-semibold
                           hover:bg-eco-green/80 transition-colors
                           disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {generating ? 'Üretiliyor...' : 'QR Üret'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
