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

// Ödül tipi → kullanım açıklaması
const REWARD_USAGE_DESC = {
  COFFEE:        'Bu kodu terminal içindeki kafede göstererek kahvenizi alabilirsiniz.',
  LOUNGE_ACCESS: 'Bu kodu lounge girişinde göstererek erişim sağlayabilirsiniz.',
  UPGRADE:       'Bu kodu check-in kontuarında göstererek koltuğunuzu yükseltebilirsiniz.',
  DISCOUNT:      'Bu kodu duty-free kasasında göstererek indiriminizi kullanabilirsiniz.',
  PRIORITY:      'Bu kodu kapı önünde göstererek öncelikli biniş hakkınızı kullanabilirsiniz.',
}

const TABS = [
  { key: 'catalog',      label: '🎁 Ödül Kataloğu' },
  { key: 'my-codes',     label: '🎫 Kodlarım'       },
  { key: 'transactions', label: '📋 Geçmiş'          },
]

export default function RewardsPage() {
  const { wallet, transactions, rewards, redemptions, loading, error, spendPoints } = useLoyalty()
  const [activeTab, setActiveTab]       = useState('catalog')
  const [confirmReward, setConfirmReward] = useState(null)
  const [spending, setSpending]         = useState(false)
  const [successResult, setSuccessResult] = useState(null) // { code, rewardTitle, rewardType, pointsSpent, remaining }

  // ── Ödül harca ──
  const handleSpend = async () => {
    if (!confirmReward) return
    setSpending(true)
    try {
      const data = await spendPoints(confirmReward.rewardId)
      setConfirmReward(null)
      if (data?.redemptionCode) {
        setSuccessResult({
          code:        data.redemptionCode,
          rewardTitle: data.rewardTitle ?? confirmReward.title,
          rewardType:  confirmReward.rewardType,
          pointsSpent: confirmReward.costPoints,
          remaining:   data.remainingBalance,
        })
      }
    } finally {
      setSpending(false)
    }
  }

  if (loading) return <PageSkeleton />

  const balance = wallet?.currentBalance ?? 0

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

      {/* ── Tab Nav ── */}
      <div className="flex gap-1 bg-gray-800/50 rounded-xl p-1">
        {TABS.map(tab => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`flex-1 py-2 rounded-lg text-xs font-medium transition-colors
              ${activeTab === tab.key
                ? 'bg-eco-green/20 text-eco-green border border-eco-green/30'
                : 'text-gray-500 hover:text-gray-300'}`}
          >
            {tab.label}
            {tab.key === 'my-codes' && redemptions.length > 0 && (
              <span className="ml-1 bg-eco-green text-gray-900 text-[9px] font-bold
                               px-1.5 py-0.5 rounded-full align-middle">
                {redemptions.length}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* ── Tab İçerikleri ── */}
      {activeTab === 'catalog' && (
        <>
          {rewards.length === 0 ? (
            <div className="bg-gray-800 rounded-xl border border-gray-700 text-center py-10">
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
        </>
      )}

      {activeTab === 'my-codes' && (
        <MyCodesTab redemptions={redemptions} />
      )}

      {activeTab === 'transactions' && (
        <>
          {transactions.length === 0 ? (
            <div className="bg-gray-800 rounded-xl border border-gray-700 text-center py-8">
              <p className="text-gray-500 text-sm">Henüz hiç işlem yok.</p>
            </div>
          ) : (
            <div className="bg-gray-800 rounded-xl border border-gray-700 divide-y divide-gray-700/60">
              {transactions.map(tx => (
                <TransactionRow key={tx.transId} tx={tx} />
              ))}
            </div>
          )}
        </>
      )}

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

      {/* ── Başarı modal'ı (kod göster) ── */}
      {successResult && (
        <RedemptionSuccessModal
          result={successResult}
          onClose={() => setSuccessResult(null)}
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

function MyCodesTab({ redemptions }) {
  if (redemptions.length === 0) {
    return (
      <div className="bg-gray-800 rounded-xl border border-gray-700 text-center py-12">
        <p className="text-4xl mb-3">🎫</p>
        <p className="text-gray-300 font-semibold text-sm">Henüz hiç ödül kodunuz yok</p>
        <p className="text-gray-500 text-xs mt-1">Ödül kataloğundan bir ödül alarak başlayın.</p>
      </div>
    )
  }

  return (
    <div className="space-y-3">
      {redemptions.map(item => (
        <RedemptionCodeCard key={item.transId} item={item} />
      ))}
    </div>
  )
}

function RedemptionCodeCard({ item }) {
  const icon      = REWARD_ICON[item.rewardType] ?? DEFAULT_ICON
  const usageDesc = REWARD_USAGE_DESC[item.rewardType] ?? 'Bu kodu ilgili kontuarda gösterin.'
  const [copied, setCopied] = useState(false)

  const handleCopy = () => {
    navigator.clipboard.writeText(item.redemptionCode).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }).catch(() => {})
  }

  return (
    <div className="bg-gray-800 rounded-xl border border-gray-700 p-4 space-y-3">
      {/* Başlık satırı */}
      <div className="flex items-center gap-3">
        <div className={`w-9 h-9 rounded-xl border flex items-center justify-center text-lg flex-shrink-0 ${icon.bg}`}>
          {icon.emoji}
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-white text-sm font-semibold truncate">{item.rewardName}</p>
          <p className="text-gray-500 text-[10px]">{item.timeAgo} · {item.pointsSpent} puan</p>
        </div>
      </div>

      {/* Kod kutusu */}
      <div className="flex items-center gap-2 bg-gray-900 border border-eco-green/20 rounded-lg px-3 py-2">
        <span className="flex-1 font-mono text-eco-green text-xs font-bold tracking-wider truncate">
          {item.redemptionCode}
        </span>
        <button
          onClick={handleCopy}
          className={`text-xs transition-colors flex-shrink-0 ${copied ? 'text-eco-green' : 'text-gray-400 hover:text-eco-green'}`}
          title="Kopyala"
        >
          {copied ? '✓ Kopyalandı' : '📋'}
        </button>
      </div>

      {/* Kullanım açıklaması */}
      <p className="text-gray-500 text-[10px] leading-relaxed">{usageDesc}</p>
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

function RedemptionSuccessModal({ result, onClose }) {
  const icon    = REWARD_ICON[result.rewardType] ?? DEFAULT_ICON
  const usageDesc = REWARD_USAGE_DESC[result.rewardType] ?? 'Bu kodu ilgili kontuarda göstererek ödülünüzü kullanabilirsiniz.'

  const handleCopy = () => {
    navigator.clipboard.writeText(result.code).then(() => {
      // toast zaten useLoyalty'de atıldı; sessizce geç
    }).catch(() => {})
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
      <div className="bg-gray-800 rounded-2xl border border-gray-700 w-full max-w-sm shadow-2xl p-6 space-y-4">

        {/* Başlık */}
        <div className="text-center">
          <div className="text-4xl mb-1">✅</div>
          <p className="text-white font-bold text-lg">Ödül Alındı!</p>
        </div>

        {/* Ödül ikonu + adı */}
        <div className="flex items-center gap-3 bg-gray-700/50 rounded-xl p-3">
          <div className={`w-10 h-10 rounded-xl border flex items-center justify-center text-xl flex-shrink-0 ${icon.bg}`}>
            {icon.emoji}
          </div>
          <p className="text-white font-semibold text-sm">{result.rewardTitle}</p>
        </div>

        {/* Kod kutusu */}
        <div>
          <p className="text-gray-400 text-xs mb-1.5">Kullanım Kodunuz</p>
          <div className="flex items-center gap-2 bg-gray-900 border border-eco-green/30 rounded-xl px-4 py-3">
            <span className="flex-1 font-mono text-eco-green font-bold tracking-widest text-sm">
              {result.code}
            </span>
            <button
              onClick={handleCopy}
              className="text-gray-400 hover:text-eco-green transition-colors text-sm"
              title="Kopyala"
            >
              📋
            </button>
          </div>
        </div>

        {/* Açıklama */}
        <p className="text-gray-400 text-xs leading-relaxed text-center">
          {usageDesc}
        </p>

        {/* Puan özeti */}
        <div className="flex justify-between text-xs text-gray-500 border-t border-gray-700 pt-3">
          <span>Harcanan: <span className="text-red-400 font-semibold">−{result.pointsSpent} puan</span></span>
          <span>Kalan: <span className="text-eco-green font-semibold">{result.remaining} puan</span></span>
        </div>

        <button
          onClick={onClose}
          className="w-full py-2.5 rounded-xl bg-eco-green text-gray-900 font-bold
                     text-sm hover:bg-green-400 transition-colors"
        >
          Tamam
        </button>
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
