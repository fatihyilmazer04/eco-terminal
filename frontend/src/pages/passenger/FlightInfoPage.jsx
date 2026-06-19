import React, { useState, useCallback } from 'react'
import toast from 'react-hot-toast'
import { useMyFlights } from '../../hooks/useFlights'
import FlightCard from '../../components/FlightCard'
import { ticketApi } from '../../api/ticketApi'

// ── Skeleton ──────────────────────────────────────────────────────────────────

function SkeletonCard() {
  return (
    <div className="eco-card animate-pulse">
      <div className="flex items-center justify-between mb-4">
        <div className="h-5 w-24 bg-gray-700 rounded" />
        <div className="h-5 w-16 bg-gray-700 rounded-full" />
      </div>
      <div className="flex items-center gap-3 mb-4">
        <div className="h-10 w-20 bg-gray-700 rounded" />
        <div className="flex-1 h-px bg-gray-700" />
        <div className="h-8 w-28 bg-gray-700 rounded" />
      </div>
      <div className="flex justify-between pt-3 border-t border-gray-700">
        <div className="h-4 w-20 bg-gray-700 rounded" />
        <div className="h-4 w-16 bg-gray-700 rounded" />
        <div className="h-4 w-16 bg-gray-700 rounded" />
      </div>
    </div>
  )
}

// ── PNR Claim Modal ───────────────────────────────────────────────────────────

function PnrModal({ onClose, onClaimed }) {
  const [pnr, setPnr] = useState('')
  const [preview, setPreview] = useState(null) // TicketDetailResponse
  const [step, setStep] = useState('input')    // 'input' | 'preview' | 'done'
  const [loading, setLoading] = useState(false)

  const handleLookup = async (e) => {
    e.preventDefault()
    if (!pnr.trim()) return
    setLoading(true)
    try {
      const res = await ticketApi.lookupPnr(pnr.trim().toUpperCase())
      setPreview(res.data.data)
      setStep('preview')
    } catch (err) {
      toast.error(err?.response?.data?.message ?? 'PNR bulunamadı')
    } finally {
      setLoading(false)
    }
  }

  const handleClaim = async () => {
    setLoading(true)
    try {
      await ticketApi.claimTicket(pnr.trim().toUpperCase())
      toast.success('Bilet hesabınıza eklendi!')
      setStep('done')
      onClaimed()
    } catch (err) {
      toast.error(err?.response?.data?.message ?? 'Claim başarısız')
    } finally {
      setLoading(false)
    }
  }

  const fmt = (iso) => {
    if (!iso) return '—'
    return new Date(iso).toLocaleString('tr-TR', {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit',
    })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div className="bg-gray-800 border border-gray-700 rounded-2xl w-full max-w-md">
        <div className="flex items-center justify-between p-5 border-b border-gray-700">
          <h2 className="text-white font-bold text-lg">🎫 Bilet Ekle</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl">✕</button>
        </div>

        {step === 'input' && (
          <form onSubmit={handleLookup} className="p-5 space-y-4">
            <p className="text-gray-400 text-sm">
              PNR kodunuzu girerek biletinizi hesabınıza ekleyin.
              Format: <span className="font-mono text-eco-green">TK-A3F2B1</span>
            </p>
            <input
              autoFocus
              value={pnr}
              onChange={e => setPnr(e.target.value.toUpperCase())}
              placeholder="XX-XXXXXX"
              maxLength={10}
              className="w-full px-4 py-3 rounded-xl bg-gray-700 border border-gray-600 text-white text-lg font-mono tracking-widest text-center focus:outline-none focus:border-eco-green"
            />
            <button
              type="submit"
              disabled={loading || !pnr.trim()}
              className="w-full py-3 rounded-xl bg-eco-green text-white font-medium hover:bg-eco-green/80 transition-colors disabled:opacity-50"
            >
              {loading ? 'Sorgulanıyor…' : 'PNR Sorgula'}
            </button>
          </form>
        )}

        {step === 'preview' && preview && (
          <div className="p-5 space-y-4">
            <div className="bg-gray-700/50 rounded-xl p-4 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-gray-400 text-xs">PNR</span>
                <span className="font-mono text-eco-green font-bold">{preview.pnrCode}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-gray-400 text-xs">Uçuş</span>
                <span className="text-white font-medium">{preview.iataCode}{preview.flightCode}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-gray-400 text-xs">Rota</span>
                <span className="text-gray-300 text-sm">{preview.origin} → {preview.destination}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-gray-400 text-xs">Kalkış</span>
                <span className="text-gray-300 text-sm">{fmt(preview.departureTime)}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-gray-400 text-xs">Koltuk</span>
                <span className="text-gray-300 text-sm font-mono">{preview.seatNumber} · {preview.seatClass}</span>
              </div>
              {preview.gateName && (
                <div className="flex items-center justify-between">
                  <span className="text-gray-400 text-xs">Kapı</span>
                  <span className="text-gray-300 text-sm">{preview.gateName}</span>
                </div>
              )}
              {preview.passengerName && (
                <div className="flex items-center justify-between">
                  <span className="text-gray-400 text-xs">Yolcu</span>
                  <span className="text-gray-300 text-sm">{preview.passengerName}</span>
                </div>
              )}
            </div>

            {preview.userId ? (
              <p className="text-yellow-400 text-sm text-center">
                Bu bilet zaten bir kullanıcıya ait.
              </p>
            ) : (
              <p className="text-gray-400 text-sm text-center">
                Bu bileti hesabınıza eklemek istiyor musunuz?
              </p>
            )}

            <div className="flex gap-3">
              <button
                onClick={() => setStep('input')}
                className="flex-1 py-2.5 rounded-xl border border-gray-600 text-gray-300 hover:bg-gray-700 transition-colors text-sm"
              >
                Geri
              </button>
              <button
                onClick={handleClaim}
                disabled={loading || !!preview.userId}
                className="flex-1 py-2.5 rounded-xl bg-eco-green text-white font-medium hover:bg-eco-green/80 transition-colors text-sm disabled:opacity-50"
              >
                {loading ? 'Ekleniyor…' : 'Bileti Ekle'}
              </button>
            </div>
          </div>
        )}

        {step === 'done' && (
          <div className="p-5 text-center space-y-4">
            <div className="text-5xl">✅</div>
            <p className="text-white font-medium">Bilet hesabınıza eklendi!</p>
            <p className="text-gray-400 text-sm">
              <span className="font-mono text-eco-green">{pnr}</span> numaralı biletiniz artık "Uçuşlarım" listesinde görünecek.
            </p>
            <button
              onClick={onClose}
              className="w-full py-2.5 rounded-xl bg-eco-green text-white font-medium hover:bg-eco-green/80 transition-colors"
            >
              Kapat
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function FlightInfoPage() {
  const { data: flights, loading, error, refetch } = useMyFlights()
  const [pnrModalOpen, setPnrModalOpen] = useState(false)

  const handleUnclaim = useCallback(async (ticketId) => {
    try {
      await ticketApi.unclaimTicket(ticketId)
      toast.success('Bilet hesabınızdan kaldırıldı.')
      refetch()
    } catch (err) {
      toast.error(err?.response?.data?.message ?? 'İşlem başarısız.')
    }
  }, [refetch])

  return (
    <div className="min-h-screen bg-gray-900 p-6">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-white">Uçuşlarım</h1>
          <p className="text-gray-400 text-sm mt-0.5">Aktif biletleriniz</p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setPnrModalOpen(true)}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-eco-green/10 border border-eco-green/30 text-eco-green text-sm font-medium hover:bg-eco-green/20 transition-colors"
          >
            🎫 Bilet Ekle
          </button>
          <button
            onClick={refetch}
            className="text-gray-400 hover:text-white transition-colors p-1.5 rounded hover:bg-gray-700"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>
        </div>
      </div>

      {error && (
        <div className="mb-6 px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-400 text-sm">
          {error}
        </div>
      )}

      {loading ? (
        <div className="flex flex-col gap-4">
          <SkeletonCard />
          <SkeletonCard />
        </div>
      ) : flights.length === 0 ? (
        <div className="eco-card text-center py-12">
          <div className="text-5xl mb-4">✈️</div>
          <p className="text-gray-400 mb-4">Aktif biletiniz bulunamadı.</p>
          <button
            onClick={() => setPnrModalOpen(true)}
            className="px-5 py-2.5 rounded-xl bg-eco-green text-white text-sm font-medium hover:bg-eco-green/80 transition-colors"
          >
            🎫 PNR ile Bilet Ekle
          </button>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          {flights.map(flight => (
            <FlightCard
              key={flight.flightId}
              flightId={flight.flightId}
              flightCode={flight.flightCode}
              airline={flight.airline}
              iataCode={flight.iataCode}
              destination={flight.destination}
              origin={flight.origin}
              departureTime={flight.departureTime}
              minutesToDeparture={flight.minutesToDeparture}
              gateZoneName={flight.gateZoneName}
              gateDensityLevel={flight.gateDensityLevel}
              colorCode={flight.colorCode}
              seatNumber={flight.seatNumber}
              flightClass={flight.flightClass}
              status={flight.status}
              pnrCode={flight.pnrCode}
              ticketId={flight.ticketId}
              onUnclaim={handleUnclaim}
            />
          ))}
        </div>
      )}

      {pnrModalOpen && (
        <PnrModal
          onClose={() => setPnrModalOpen(false)}
          onClaimed={() => { refetch(); setPnrModalOpen(false) }}
        />
      )}
    </div>
  )
}
