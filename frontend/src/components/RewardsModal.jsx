import React, { useState } from 'react'
import ProgressBar from './ProgressBar'

const REWARD_ICON = {
  LOUNGE_ACCESS: '🛋',
  COFFEE:        '☕',
  DISCOUNT:      '🏷',
  UPGRADE:       '✈',
}

export default function RewardsModal({ rewards, balance, onSpend, onClose }) {
  const [confirming, setConfirming] = useState(null) // reward object
  const [loading, setLoading]       = useState(false)

  const handleConfirm = async () => {
    if (!confirming) return
    setLoading(true)
    try {
      await onSpend(confirming.rewardId)
      setConfirming(null)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
      <div className="bg-gray-800 rounded-2xl border border-gray-700 w-full max-w-md
                      shadow-2xl overflow-hidden">

        {/* Başlık */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-700">
          <div>
            <h2 className="text-white font-bold text-lg">Ödül Kataloğu</h2>
            <p className="text-gray-400 text-xs mt-0.5">Bakiye: <span className="text-eco-green font-semibold">{balance} 🌿</span></p>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl leading-none">✕</button>
        </div>

        {/* Onay adımı */}
        {confirming ? (
          <div className="px-5 py-6 space-y-4">
            <div className="text-center">
              <p className="text-3xl mb-3">{REWARD_ICON[confirming.rewardType] ?? '🎁'}</p>
              <p className="text-white font-semibold">{confirming.title}</p>
              <p className="text-gray-400 text-sm mt-1">
                <span className="text-eco-green font-bold">{confirming.costPoints}</span> 🌿 puan harcayarak bu ödülü almak istiyor musunuz?
              </p>
              <p className="text-gray-500 text-xs mt-1">
                Kalan bakiye: {balance - confirming.costPoints} puan
              </p>
            </div>
            <div className="flex gap-3">
              <button
                onClick={() => setConfirming(null)}
                className="flex-1 py-2.5 rounded-xl border border-gray-600 text-gray-300
                           text-sm hover:border-gray-500 transition-colors"
              >
                İptal
              </button>
              <button
                onClick={handleConfirm}
                disabled={loading}
                className="flex-1 py-2.5 rounded-xl bg-eco-green text-gray-900 font-bold
                           text-sm hover:bg-green-400 transition-colors disabled:opacity-50"
              >
                {loading ? 'İşleniyor...' : 'Onayla'}
              </button>
            </div>
          </div>
        ) : (
          /* Ödül Listesi */
          <div className="p-4 grid grid-cols-2 gap-3 max-h-96 overflow-y-auto">
            {rewards.map(reward => (
              <div key={reward.rewardId}
                   className={`rounded-xl border p-3 flex flex-col gap-2
                               ${reward.canAfford
                                 ? 'border-gray-600 bg-gray-900/50'
                                 : 'border-gray-700 bg-gray-900/30 opacity-60'}`}>
                <p className="text-2xl text-center">{REWARD_ICON[reward.rewardType] ?? '🎁'}</p>
                <p className="text-white text-xs font-semibold text-center leading-tight">{reward.title}</p>
                <p className="text-gray-500 text-[10px] text-center line-clamp-2">{reward.description}</p>
                <p className="text-eco-green text-xs font-bold text-center">🌿 {reward.costPoints} puan</p>
                <button
                  onClick={() => reward.canAfford && setConfirming(reward)}
                  disabled={!reward.canAfford}
                  className={`w-full py-1.5 rounded-lg text-xs font-semibold transition-colors
                              ${reward.canAfford
                                ? 'bg-eco-green text-gray-900 hover:bg-green-400'
                                : 'bg-gray-700 text-gray-500 cursor-not-allowed'}`}
                >
                  {reward.canAfford ? 'Kullan' : 'Yetersiz Puan'}
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
