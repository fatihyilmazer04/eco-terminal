import React, { useState, useEffect, useCallback } from 'react'
import toast from 'react-hot-toast'
import { ticketApi } from '../../api/ticketApi'

// ── Helpers ──────────────────────────────────────────────────────────────────

const fmt = (iso) => {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('tr-TR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

const STATUS_LABELS = {
  SCHEDULED: 'Planlandı',
  BOARDING: 'Biniş',
  DEPARTED: 'Kalktı',
  ARRIVED: 'İndi',
  CANCELLED: 'İptal',
  DELAYED: 'Gecikti',
}

const CLASS_LABELS = { ECONOMY: 'Economy', BUSINESS: 'Business', FIRST: 'First' }

// ── Flight Modal ──────────────────────────────────────────────────────────────

function FlightModal({ flight, airlines, gates, onClose, onSaved }) {
  const isEdit = !!flight
  const [form, setForm] = useState({
    flightCode: flight?.flightCode ?? '',
    airlineId: flight?.airlineId ?? '',
    destination: flight?.destination ?? '',
    origin: flight?.origin ?? '',
    departureTime: flight?.departureTime
      ? new Date(flight.departureTime).toISOString().slice(0, 16) : '',
    arrivalTime: flight?.arrivalTime
      ? new Date(flight.arrivalTime).toISOString().slice(0, 16) : '',
    gateId: flight?.gateId ?? '',
    status: flight?.status ?? 'SCHEDULED',
  })
  const [saving, setSaving] = useState(false)

  const set = (k, v) => setForm(f => ({ ...f, [k]: v }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      const payload = {
        ...form,
        airlineId: form.airlineId ? Number(form.airlineId) : null,
        gateId: form.gateId ? Number(form.gateId) : null,
        departureTime: form.departureTime ? new Date(form.departureTime).toISOString() : null,
        arrivalTime: form.arrivalTime ? new Date(form.arrivalTime).toISOString() : null,
      }
      if (isEdit) {
        await ticketApi.adminUpdateFlight(flight.flightId, payload)
        toast.success('Uçuş güncellendi')
      } else {
        await ticketApi.adminCreateFlight(payload)
        toast.success('Uçuş oluşturuldu')
      }
      onSaved()
    } catch (err) {
      toast.error(err?.response?.data?.message ?? 'Hata oluştu')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div className="bg-gray-800 border border-gray-700 rounded-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between p-5 border-b border-gray-700">
          <h2 className="text-white font-bold text-lg">
            {isEdit ? 'Uçuş Düzenle' : 'Yeni Uçuş'}
          </h2>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl">✕</button>
        </div>
        <form onSubmit={handleSubmit} className="p-5 space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs text-gray-400 mb-1 block">Uçuş Kodu *</label>
              <input
                required
                value={form.flightCode}
                onChange={e => set('flightCode', e.target.value)}
                placeholder="TK1234"
                className="w-full px-3 py-2 rounded-lg bg-gray-700 border border-gray-600 text-white text-sm focus:outline-none focus:border-eco-green"
              />
            </div>
            <div>
              <label className="text-xs text-gray-400 mb-1 block">Havayolu *</label>
              <select
                required
                value={form.airlineId}
                onChange={e => set('airlineId', e.target.value)}
                className="w-full px-3 py-2 rounded-lg bg-gray-700 border border-gray-600 text-white text-sm focus:outline-none focus:border-eco-green"
              >
                <option value="">Seçiniz</option>
                {airlines.map(a => (
                  <option key={a.airlineId} value={a.airlineId}>{a.iataCode} — {a.name}</option>
                ))}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs text-gray-400 mb-1 block">Kalkış *</label>
              <input
                required
                value={form.origin}
                onChange={e => set('origin', e.target.value)}
                placeholder="İstanbul"
                className="w-full px-3 py-2 rounded-lg bg-gray-700 border border-gray-600 text-white text-sm focus:outline-none focus:border-eco-green"
              />
            </div>
            <div>
              <label className="text-xs text-gray-400 mb-1 block">Varış *</label>
              <input
                required
                value={form.destination}
                onChange={e => set('destination', e.target.value)}
                placeholder="Ankara"
                className="w-full px-3 py-2 rounded-lg bg-gray-700 border border-gray-600 text-white text-sm focus:outline-none focus:border-eco-green"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs text-gray-400 mb-1 block">Kalkış Saati *</label>
              <input
                required
                type="datetime-local"
                value={form.departureTime}
                onChange={e => set('departureTime', e.target.value)}
                className="w-full px-3 py-2 rounded-lg bg-gray-700 border border-gray-600 text-white text-sm focus:outline-none focus:border-eco-green"
              />
            </div>
            <div>
              <label className="text-xs text-gray-400 mb-1 block">Varış Saati</label>
              <input
                type="datetime-local"
                value={form.arrivalTime}
                onChange={e => set('arrivalTime', e.target.value)}
                className="w-full px-3 py-2 rounded-lg bg-gray-700 border border-gray-600 text-white text-sm focus:outline-none focus:border-eco-green"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs text-gray-400 mb-1 block">Kapı</label>
              <select
                value={form.gateId}
                onChange={e => set('gateId', e.target.value)}
                className="w-full px-3 py-2 rounded-lg bg-gray-700 border border-gray-600 text-white text-sm focus:outline-none focus:border-eco-green"
              >
                <option value="">Belirsiz</option>
                {gates.map(g => (
                  <option key={g.zoneId} value={g.zoneId}>{g.zoneName}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="text-xs text-gray-400 mb-1 block">Durum</label>
              <select
                value={form.status}
                onChange={e => set('status', e.target.value)}
                className="w-full px-3 py-2 rounded-lg bg-gray-700 border border-gray-600 text-white text-sm focus:outline-none focus:border-eco-green"
              >
                {Object.entries(STATUS_LABELS).map(([k, v]) => (
                  <option key={k} value={k}>{v}</option>
                ))}
              </select>
            </div>
          </div>

          <div className="flex gap-3 pt-2">
            <button
              type="button" onClick={onClose}
              className="flex-1 py-2 rounded-lg border border-gray-600 text-gray-300 hover:bg-gray-700 transition-colors text-sm"
            >
              İptal
            </button>
            <button
              type="submit" disabled={saving}
              className="flex-1 py-2 rounded-lg bg-eco-green text-white font-medium hover:bg-eco-green/80 transition-colors text-sm disabled:opacity-50"
            >
              {saving ? 'Kaydediliyor…' : isEdit ? 'Güncelle' : 'Oluştur'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ── Ticket Modal ──────────────────────────────────────────────────────────────

function TicketModal({ flights, onClose, onSaved }) {
  const [form, setForm] = useState({
    flightId: '', seatNumber: '', seatClass: 'ECONOMY', passengerName: '',
  })
  const [saving, setSaving] = useState(false)
  const set = (k, v) => setForm(f => ({ ...f, [k]: v }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      const payload = { ...form, flightId: Number(form.flightId) }
      await ticketApi.adminCreate(payload)
      toast.success('Bilet oluşturuldu, PNR üretildi')
      onSaved()
    } catch (err) {
      toast.error(err?.response?.data?.message ?? 'Hata oluştu')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div className="bg-gray-800 border border-gray-700 rounded-2xl w-full max-w-md">
        <div className="flex items-center justify-between p-5 border-b border-gray-700">
          <h2 className="text-white font-bold text-lg">Yeni Bilet Oluştur</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl">✕</button>
        </div>
        <form onSubmit={handleSubmit} className="p-5 space-y-4">
          <div>
            <label className="text-xs text-gray-400 mb-1 block">Uçuş *</label>
            <select
              required
              value={form.flightId}
              onChange={e => set('flightId', e.target.value)}
              className="w-full px-3 py-2 rounded-lg bg-gray-700 border border-gray-600 text-white text-sm focus:outline-none focus:border-eco-green"
            >
              <option value="">Seçiniz</option>
              {flights.map(f => (
                <option key={f.flightId} value={f.flightId}>
                  {f.iataCode}{f.flightCode} — {f.origin} → {f.destination}
                </option>
              ))}
            </select>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs text-gray-400 mb-1 block">Koltuk *</label>
              <input
                required
                value={form.seatNumber}
                onChange={e => set('seatNumber', e.target.value)}
                placeholder="12A"
                className="w-full px-3 py-2 rounded-lg bg-gray-700 border border-gray-600 text-white text-sm focus:outline-none focus:border-eco-green"
              />
            </div>
            <div>
              <label className="text-xs text-gray-400 mb-1 block">Sınıf *</label>
              <select
                value={form.seatClass}
                onChange={e => set('seatClass', e.target.value)}
                className="w-full px-3 py-2 rounded-lg bg-gray-700 border border-gray-600 text-white text-sm focus:outline-none focus:border-eco-green"
              >
                {Object.entries(CLASS_LABELS).map(([k, v]) => (
                  <option key={k} value={k}>{v}</option>
                ))}
              </select>
            </div>
          </div>
          <div>
            <label className="text-xs text-gray-400 mb-1 block">Yolcu Adı</label>
            <input
              value={form.passengerName}
              onChange={e => set('passengerName', e.target.value)}
              placeholder="Fatih Yılmazer"
              className="w-full px-3 py-2 rounded-lg bg-gray-700 border border-gray-600 text-white text-sm focus:outline-none focus:border-eco-green"
            />
          </div>
          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose}
              className="flex-1 py-2 rounded-lg border border-gray-600 text-gray-300 hover:bg-gray-700 transition-colors text-sm">
              İptal
            </button>
            <button type="submit" disabled={saving}
              className="flex-1 py-2 rounded-lg bg-eco-green text-white font-medium hover:bg-eco-green/80 transition-colors text-sm disabled:opacity-50">
              {saving ? 'Oluşturuluyor…' : 'Oluştur & PNR Üret'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ── Main Page ─────────────────────────────────────────────────────────────────

export default function AdminFlightManagementPage() {
  const [tab, setTab] = useState('flights')
  const [flights, setFlights] = useState([])
  const [tickets, setTickets] = useState([])
  const [airlines, setAirlines] = useState([])
  const [gates, setGates] = useState([])
  const [loading, setLoading] = useState(true)

  const [flightModal, setFlightModal] = useState(null) // null | 'new' | flight object
  const [ticketModal, setTicketModal] = useState(false)

  const loadAll = useCallback(async () => {
    setLoading(true)
    try {
      const [fRes, tRes, aRes, gRes] = await Promise.all([
        ticketApi.adminGetFlights(),
        ticketApi.adminGetAll(),
        ticketApi.getAirlines(),
        ticketApi.getGates(),
      ])
      setFlights(fRes.data.data ?? [])
      setTickets(tRes.data.data ?? [])
      setAirlines(aRes.data.data ?? [])
      setGates(gRes.data.data ?? [])
    } catch {
      toast.error('Veriler yüklenemedi')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { loadAll() }, [loadAll])

  const handleDeleteFlight = async (id) => {
    if (!window.confirm('Bu uçuşu silmek istediğinizden emin misiniz?')) return
    try {
      await ticketApi.adminDeleteFlight(id)
      toast.success('Uçuş silindi')
      loadAll()
    } catch (err) {
      toast.error(err?.response?.data?.message ?? 'Silinemedi')
    }
  }

  const handleDeleteTicket = async (id) => {
    if (!window.confirm('Bu bileti silmek istediğinizden emin misiniz?')) return
    try {
      await ticketApi.adminDelete(id)
      toast.success('Bilet silindi')
      loadAll()
    } catch (err) {
      toast.error(err?.response?.data?.message ?? 'Silinemedi')
    }
  }

  const onModalSaved = () => {
    setFlightModal(null)
    setTicketModal(false)
    loadAll()
  }

  return (
    <div className="p-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-white">Uçuş Yönetimi</h1>
          <p className="text-gray-400 text-sm mt-0.5">Uçuşlar ve PNR bilet sistemi</p>
        </div>
        <button
          onClick={() => tab === 'flights' ? setFlightModal('new') : setTicketModal(true)}
          className="flex items-center gap-2 px-4 py-2 rounded-xl bg-eco-green text-white text-sm font-medium hover:bg-eco-green/80 transition-colors"
        >
          <span>+</span>
          {tab === 'flights' ? 'Yeni Uçuş' : 'Yeni Bilet'}
        </button>
      </div>

      {/* Tabs */}
      <div className="flex gap-2 mb-6">
        {[
          { key: 'flights', label: `✈️ Uçuşlar (${flights.length})` },
          { key: 'tickets', label: `🎫 Biletler (${tickets.length})` },
        ].map(t => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`px-4 py-2 rounded-xl text-sm font-medium transition-colors
              ${tab === t.key
                ? 'bg-eco-green/10 text-eco-green border border-eco-green/30'
                : 'text-gray-400 hover:text-gray-200 hover:bg-gray-800'}`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-48">
          <div className="w-8 h-8 border-4 border-eco-green border-t-transparent rounded-full animate-spin" />
        </div>
      ) : tab === 'flights' ? (
        // ── Flights table ──
        <div className="eco-card p-0 overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-700">
                {['Kod', 'Havayolu', 'Rota', 'Kalkış', 'Kapı', 'Durum', ''].map(h => (
                  <th key={h} className="text-left text-gray-400 font-medium px-4 py-3 text-xs uppercase tracking-wide">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-800">
              {flights.length === 0 ? (
                <tr><td colSpan={7} className="text-center text-gray-500 py-12">Henüz uçuş yok</td></tr>
              ) : flights.map(f => (
                <tr key={f.flightId} className="hover:bg-gray-800/50 transition-colors">
                  <td className="px-4 py-3 text-white font-mono font-bold">{f.flightCode}</td>
                  <td className="px-4 py-3 text-gray-300">
                    <span className="text-xs bg-gray-700 text-gray-300 px-2 py-0.5 rounded mr-1">{f.iataCode}</span>
                    {f.airlineName}
                  </td>
                  <td className="px-4 py-3 text-gray-300">{f.origin} → {f.destination}</td>
                  <td className="px-4 py-3 text-gray-400 text-xs">{fmt(f.departureTime)}</td>
                  <td className="px-4 py-3 text-gray-400">{f.gateName ?? '—'}</td>
                  <td className="px-4 py-3">
                    <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                      f.status === 'CANCELLED' ? 'bg-red-500/20 text-red-400' :
                      f.status === 'BOARDING' ? 'bg-yellow-500/20 text-yellow-400' :
                      f.status === 'DEPARTED' ? 'bg-blue-500/20 text-blue-400' :
                      'bg-eco-green/20 text-eco-green'
                    }`}>{STATUS_LABELS[f.status] ?? f.status}</span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex gap-2">
                      <button
                        onClick={() => setFlightModal(f)}
                        className="text-xs text-blue-400 hover:text-blue-300 transition-colors"
                      >Düzenle</button>
                      <button
                        onClick={() => handleDeleteFlight(f.flightId)}
                        className="text-xs text-red-400 hover:text-red-300 transition-colors"
                      >Sil</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        // ── Tickets table ──
        <div className="eco-card p-0 overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-700">
                {['PNR', 'Yolcu', 'Uçuş', 'Koltuk', 'Sınıf', 'Kullanıcı', 'Durum', ''].map(h => (
                  <th key={h} className="text-left text-gray-400 font-medium px-4 py-3 text-xs uppercase tracking-wide">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-800">
              {tickets.length === 0 ? (
                <tr><td colSpan={8} className="text-center text-gray-500 py-12">Henüz bilet yok</td></tr>
              ) : tickets.map(t => (
                <tr key={t.ticketId} className="hover:bg-gray-800/50 transition-colors">
                  <td className="px-4 py-3">
                    <span className="font-mono text-eco-green font-bold text-xs tracking-wider bg-eco-green/10 px-2 py-1 rounded">
                      {t.pnrCode}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-gray-300 text-xs">{t.passengerName ?? <span className="text-gray-600">—</span>}</td>
                  <td className="px-4 py-3 text-gray-300 text-xs">
                    <span className="font-mono">{t.flightCode}</span>
                    <span className="text-gray-500 ml-1">{t.origin} → {t.destination}</span>
                  </td>
                  <td className="px-4 py-3 text-gray-400 text-xs font-mono">{t.seatNumber}</td>
                  <td className="px-4 py-3 text-gray-400 text-xs">{CLASS_LABELS[t.seatClass] ?? t.seatClass}</td>
                  <td className="px-4 py-3 text-xs">
                    {t.userEmail
                      ? <span className="text-blue-400">{t.userEmail}</span>
                      : <span className="text-gray-600 italic">Claim edilmedi</span>}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`text-xs px-2 py-0.5 rounded-full ${
                      t.ticketStatus === 'CANCELLED' ? 'bg-red-500/20 text-red-400' : 'bg-eco-green/20 text-eco-green'
                    }`}>{t.ticketStatus}</span>
                  </td>
                  <td className="px-4 py-3">
                    <button
                      onClick={() => handleDeleteTicket(t.ticketId)}
                      className="text-xs text-red-400 hover:text-red-300 transition-colors"
                    >Sil</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Modals */}
      {flightModal && (
        <FlightModal
          flight={flightModal === 'new' ? null : flightModal}
          airlines={airlines}
          gates={gates}
          onClose={() => setFlightModal(null)}
          onSaved={onModalSaved}
        />
      )}
      {ticketModal && (
        <TicketModal
          flights={flights}
          onClose={() => setTicketModal(false)}
          onSaved={onModalSaved}
        />
      )}
    </div>
  )
}
