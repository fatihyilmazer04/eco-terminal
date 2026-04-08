import React, { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { loungeApi } from '../../api/loungeApi'
import ProgressBar from '../../components/ProgressBar'

function StarRating({ score }) {
  return (
    <div className="flex gap-0.5">
      {[1, 2, 3, 4, 5].map(i => (
        <span key={i} className={i <= score ? 'text-yellow-400' : 'text-gray-600'}>★</span>
      ))}
    </div>
  )
}

const DENSITY_COLOR = {
  LOW:      'green',
  MEDIUM:   'orange',
  HIGH:     'red',
  CRITICAL: 'red',
}

export default function LoungesPage() {
  const [lounges, setLounges]   = useState([])
  const [best, setBest]         = useState(null)
  const [loading, setLoading]   = useState(true)
  const [error, setError]       = useState(null)

  useEffect(() => {
    Promise.all([loungeApi.getQuietLounges(), loungeApi.getBestLounge()])
      .then(([listRes, bestRes]) => {
        setLounges(listRes.data.data ?? [])
        setBest(bestRes.data.data ?? null)
      })
      .catch(err => setError(err.response?.data?.message || 'Veri alınamadı'))
      .finally(() => setLoading(false))
  }, [])

  if (loading) return (
    <div className="bg-gray-900 p-6 space-y-4">
      {[1,2,3].map(i => <div key={i} className="eco-card animate-pulse h-32" />)}
    </div>
  )

  return (
    <div className="bg-gray-900 p-6 space-y-5">
      {/* Başlık */}
      <div>
        <h1 className="text-2xl font-bold text-white">En İyi Bekleme Alanları</h1>
        <p className="text-gray-400 text-sm mt-0.5">
          Sakin ve konforlu bekleme noktaları
        </p>
      </div>

      {error && (
        <div className="px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-400 text-sm">
          {error}
        </div>
      )}

      {/* En İyi Lounge — yeşil highlight */}
      {best && (
        <div className="bg-eco-green/10 border-2 border-eco-green/40 rounded-xl p-4 relative">
          <div className="absolute -top-3 left-4 bg-eco-green text-gray-900 text-xs font-bold
                          px-3 py-0.5 rounded-full flex items-center gap-1">
            ✨ En Sakin Alan
          </div>
          <LoungeCard lounge={best} highlight />
        </div>
      )}

      {/* Liste */}
      {lounges.length === 0 && !best ? (
        <div className="eco-card text-center py-12">
          <div className="text-4xl mb-3">🛋</div>
          <p className="text-gray-400">Şu an tüm bekleme alanları yoğun.</p>
          <p className="text-gray-500 text-sm mt-1">Biraz bekleyip tekrar kontrol edin.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {lounges.filter(l => l.zoneId !== best?.zoneId).map(lounge => (
            <div key={lounge.zoneId} className="eco-card">
              <LoungeCard lounge={lounge} />
            </div>
          ))}
        </div>
      )}

      {/* Alt bilgi */}
      <div className="px-4 py-3 rounded-xl bg-eco-green/5 border border-eco-green/20 text-center">
        <p className="text-eco-green text-xs">
          Sakin alanlarda bekleme yaparak Eko-Puan kazanabilirsiniz 🌿
        </p>
      </div>
    </div>
  )
}

function LoungeCard({ lounge, highlight }) {
  return (
    <div className="space-y-3">
      {/* Üst satır */}
      <div className="flex items-start justify-between gap-2">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-lg">🛋</span>
            <p className={`font-semibold ${highlight ? 'text-eco-green' : 'text-white'}`}>
              {lounge.zoneName}
            </p>
            {lounge.isRecommended && (
              <span className="px-1.5 py-0.5 rounded-full bg-eco-green/20 border border-eco-green/30
                               text-eco-green text-[10px] font-semibold">
                Önerilen
              </span>
            )}
          </div>
          <p className="text-gray-500 text-xs mt-0.5">{lounge.suggestion}</p>
        </div>
        <StarRating score={lounge.comfortScore} />
      </div>

      {/* Doluluk bar */}
      <div>
        <div className="flex justify-between text-xs text-gray-500 mb-1">
          <span>Doluluk</span>
          <span>%{((lounge.densityPct ?? 0) * 100).toFixed(0)}</span>
        </div>
        <ProgressBar
          value={(lounge.densityPct ?? 0) * 100}
          color={DENSITY_COLOR[lounge.densityLevel] ?? 'green'}
          height={6}
        />
      </div>

      {/* Rota butonu */}
      <Link to="/passenger/route"
            className="flex items-center justify-center gap-1.5 w-full py-2 rounded-lg
                       bg-gray-700 hover:bg-gray-600 text-gray-300 text-xs font-medium
                       transition-colors">
        <span>🗺</span> Rotamı Göster
      </Link>
    </div>
  )
}
