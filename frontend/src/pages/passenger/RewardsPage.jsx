import React, { useState } from 'react'
import { useLoyalty } from '../../hooks/useLoyalty'
import TierBadge from '../../components/TierBadge'
import ProgressBar from '../../components/ProgressBar'

// ── Ödül tipi ikonları ────────────────────────────────────────────────────────
const REWARD_ICON = {
  LOUNGE_ACCESS: { emoji: '🛋', bg: 'bg-purple-500/15 border-purple-500/30 text-purple-300' },
  COFFEE:        { emoji: '☕', bg: 'bg-amber-500/15  border-amber-500/30  text-amber-300'  },
  DISCOUNT:      { emoji: '🏷',  bg: 'bg-blue-500/15   border-blue-500/30   text-blue-300'  },
  UPGRADE:       { emoji: '✈',  bg: 'bg-sky-500/15    border-sky-500/30    text-sky-300'   },
  PRIORITY:      { emoji: '⚡', bg: 'bg-yellow-500/15 border-yellow-500/30 text-yellow-300'},
}
const DEFAULT_ICON = { emoji: '🎁', bg: 'bg-gray-700 border-gray-600 text-gray-300' }

// ── Tier renk bar'ı ───────────────────────────────────────────────────────────
const TIER_BAR_COLOR = { GREEN: 'green', GOLD: 'gold', PLATINUM: 'purple' }

// ─────────────────────────────────────────────────────────────────────────────

export default function RewardsPage() {
  const { wallet, transactions, rewards, loading, error, spendPoints, refetch } = useLoyalty()
  const [confirmReward, setConfirmReward] = useState(null)
  const [spending, setSpending] = useState(false)

  // ── Ödül harca ──
  const handleSpend = async () => {
    if (!confirmReward) return
    setSpending(true)
    try {
      await spendPoints(confirmReward.rewardId)
      setConfirmReward(null)
    } finally {
      setSpending(false)
    }
  }

  if (loading) return <PageSkeleton />

  const balance = wallet?.currentBalance ?? 0
  const tier    = wallet?.tierLevel ?? 'GREEN'

  return (
    <div className="min-h-screen bg-gray-900 px-4 py-6 max-w-2xl mx-auto space-y-6">

      {/* ── Hata banner ── */}
      {error && (
        <div className="px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-400 text-sm">
          {error}
        </div>
      )}

      {/* ── Cüzdan özet kartı ── */}
      <WalletCard wallet={wallet} />

      {/* ── Ödül Kataloğu ── */}
      <section>
        <h2 className="text-white font-bold text-base mb-3 flex items-center gap-2">
          <span>🎁</span> Ödül Kataloğu
        </h2>

        {rewards.length === 0 ? (
          <div className="eco-card text-center py-10">
            <p className="text-gray-400">Şu an aktif ödül bulunmuyor.</p>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-3">
            {rewards.map(reward => (
              <RewardCard
                key={reward.rewardId}
                reward={reward}
                balance={balance}
                onSelect={() => setConfirmReward(reward)}
              />
            ))}
          </div>
        )}
      </section>

      {/* ── Nasıl Kazanılır? ── */}
      <EarnGuide />

      {/* ── İşlem Geçmişi ── */}
      <section>
        <h2 className="text-white font-bold text-base mb-3 flex items-center gap-2">
          <span>📋</span> İşlem Geçmişi
        </h2>

        {transactions.length === 0 ? (
          <div className="eco-card text-center py-8">
            <p className="text-gray-500 text-sm">Henüz hiç işlem yok.</p>
          </div>
        ) : (
          <div className="bg-gray-800 rounded-xl border border-gray-700 divide-y divide-gray-700/60">
            {transactions.map(tx => (
              <TransactionRow key={tx.transId} tx={tx} />
            ))}
          </div>
        )}
      </section>

      {/* ── Onay modal'ı ── */}
      {confirmReward && (
        <ConfirmModal
          reward={confirmReward}
          balance={balance}
          loading={spending}
          onConfirm={handleSpend}
          onCancel={() => setConfirmReward(null)}
        />
      )}
    </div>
  )
}

// ── Alt bileşenler ────────────────────────────────────────────────────────────

function WalletCard({ wallet }) {
  if (!wallet) return null
  const balance  = wallet.currentBalance ?? 0
  const tier     = wallet.tierLevel ?? 'GREEN'
  const progress = wallet.progressPct ?? 0
  const toNext   = wallet.pointsToNextTier ?? 0
  const nextName = wallet.nextTierName

  return (
    <div className="bg-gray-800 rounded-2xl border border-gray-700 p-5">
      <div className="flex items-center justify-between mb-4">
        <p className="text-gray-400 text-sm font-medium">Eko-Puan Bakiyesi</p>
        <TierBadge tierLevel={tier} size="md" />
      </div>

      {/* Büyük bakiye */}
      <p className="text-5xl font-bold text-eco-green tabular-nums mb-1">{balance}</p>
      <p className="text-gray-500 text-xs mb-4">Eko-Puan</p>

      {/* İlerleme */}
      {nextName ? (
        <div>
          <div className="flex justify-between text-xs text-gray-400 mb-1.5">
            <span>{balance} puan</span>
            <span>{nextName}'a <span className="text-eco-green font-semibold">{toNext} puan</span> kaldı</span>
          </div>
          <ProgressBar
            value={progress}
            color={TIER_BAR_COLOR[tier] ?? 'green'}
            height={8}
            showLabel={false}
            animated
          />
        </div>
      ) : (
        <div className="text-center text-purple-300 text-sm">
          💎 En yüksek seviye — Platinum Member
        </div>
      )}
    </div>
  )
}

function RewardCard({ reward, balance, onSelect }) {
  const icon      = REWARD_ICON[reward.rewardType] ?? DEFAULT_ICON
  const canAfford = reward.canAfford
  const missing   = reward.costPoints - balance

  return (
    <div className={`
      rounded-xl border p-3.5 flex flex-col gap-2.5 transition-opacity
      ${canAfford ? 'bg-gray-800 border-gray-600' : 'bg-gray-900/60 border-gray-700 opacity-70'}
    `}>
      {/* İkon */}
      <div className={`w-10 h-10 rounded-xl border flex items-center justify-center text-xl ${icon.bg}`}>
        {icon.emoji}
      </div>

      {/* İçerik */}
      <div className="flex-1">
        <p className="text-white text-xs font-semibold leading-tight">{reward.title}</p>
        <p className="text-gray-500 text-[10px] mt-0.5 leading-snug line-clamp-2">
          {reward.description}
        </p>
      </div>

      {/* Maliyet */}
      <p className="text-eco-green text-xs font-bold">🌿 {reward.costPoints} puan</p>

      {/* Buton */}
      <button
        onClick={onSelect}
        disabled={!canAfford}
        className={`
          w-full py-2 rounded-lg text-xs font-semibold transition-colors
          ${canAfford
            ? 'bg-eco-green text-gray-900 hover:bg-green-400 active:scale-95'
            : 'bg-gray-800 text-gray-500 cursor-not-allowed border border-gray-700'}
        `}
      >
        {canAfford ? 'Kullan' : `${missing} puan daha gerekli`}
      </button>
    </div>
  )
}

function EarnGuide() {
  const items = [
    { action: '🗺 Eko-Rota Seç',       points: '+50' },
    { action: '✈ Check-in Tamamla',    points: '+25' },
    { action: '🛋 Lounge\'a Gir',       points: '+20' },
    { action: '🌿 Eko Rota Kullan',     points: '+15' },
    { action: '🧘 Sakin Alanda Bekle',  points: '+10' },
  ]

  return (
    <div className="bg-eco-green/5 border border-eco-green/20 rounded-xl p-4">
      <p className="text-eco-green text-sm font-semibold mb-3">Nasıl Puan Kazanılır?</p>
      <div className="space-y-2">
        {items.map(item => (
          <div key={item.action} className="flex items-center justify-between text-xs">
            <span className="text-gray-300">{item.action}</span>
            <span className="text-eco-green font-bold">{item.points}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function TransactionRow({ tx }) {
  const isEarn = tx.transType === 'EARN'
  const label  = tx.rewardTitle ?? tx.description ?? '—'

  return (
    <div className="flex items-center justify-between px-4 py-3">
      <div className="flex items-center gap-3">
        <span className={`text-base ${isEarn ? 'text-green-400' : 'text-red-400'}`}>
          {isEarn ? '⬆' : '⬇'}
        </span>
        <div>
          <p className="text-gray-200 text-xs font-medium">{label}</p>
          <p className="text-gray-600 text-[10px] mt-0.5">{tx.timeAgo}</p>
        </div>
      </div>
      <span className={`font-bold text-sm tabular-nums ${isEarn ? 'text-green-400' : 'text-red-400'}`}>
        {isEarn ? '+' : '−'}{tx.amount}
      </span>
    </div>
  )
}

function ConfirmModal({ reward, balance, loading, onConfirm, onCancel }) {
  const icon    = REWARD_ICON[reward.rewardType] ?? DEFAULT_ICON
  const afterPt = balance - reward.costPoints

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
      <div className="bg-gray-800 rounded-2xl border border-gray-700 w-full max-w-sm shadow-2xl p-6">
        <div className="text-center mb-6">
          <div className={`w-16 h-16 rounded-2xl border ${icon.bg} text-3xl
                           flex items-center justify-center mx-auto mb-4`}>
            {icon.emoji}
          </div>
          <p className="text-white font-bold text-lg">{reward.title}</p>
          <p className="text-gray-400 text-sm mt-1">
            <span className="text-eco-green font-bold">{reward.costPoints}</span> 🌿 puan harcayacaksınız
          </p>
          <p className="text-gray-500 text-xs mt-1">
            İşlem sonrası bakiye: {afterPt} puan
          </p>
        </div>
        <div className="flex gap-3">
          <button
            onClick={onCancel}
            disabled={loading}
            className="flex-1 py-2.5 rounded-xl border border-gray-600 text-gray-300
                       text-sm hover:border-gray-500 transition-colors disabled:opacity-50"
          >
            İptal
          </button>
          <button
            onClick={onConfirm}
            disabled={loading}
            className="flex-1 py-2.5 rounded-xl bg-eco-green text-gray-900 font-bold
                       text-sm hover:bg-green-400 transition-colors disabled:opacity-50"
          >
            {loading ? 'İşleniyor...' : 'Onayla'}
          </button>
        </div>
      </div>
    </div>
  )
}

function PageSkeleton() {
  return (
    <div className="min-h-screen bg-gray-900 px-4 py-6 max-w-2xl mx-auto space-y-6 animate-pulse">
      <div className="h-40 bg-gray-800 rounded-2xl" />
      <div className="grid grid-cols-2 gap-3">
        {[1,2,3,4].map(i => <div key={i} className="h-44 bg-gray-800 rounded-xl" />)}
      </div>
      <div className="h-32 bg-gray-800 rounded-xl" />
    </div>
  )
}
