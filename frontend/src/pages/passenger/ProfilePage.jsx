import React, { useState, useCallback, useRef } from 'react'
import { useProfile } from '../../hooks/useProfile'
import { useLoyalty } from '../../hooks/useLoyalty'
import TierBadge from '../../components/TierBadge'
import ProgressBar from '../../components/ProgressBar'
import RewardsModal from '../../components/RewardsModal'

const TABS = [
  { key: 'profile',      label: 'Profil Bilgileri' },
  { key: 'preferences',  label: 'Tercihler' },
  { key: 'eco',          label: 'Eko-Puanlarım' },
]

export default function ProfilePage() {
  const [activeTab, setActiveTab]   = useState('profile')
  const [showRewards, setShowRewards] = useState(false)
  const { profile, loading, updateProfile, updatePreferences } = useProfile()
  const { wallet, transactions, rewards, spendPoints } = useLoyalty()

  if (loading) return <PageSkeleton />

  return (
    <div className="bg-gray-900 p-6">
      <div className="max-w-2xl mx-auto space-y-5">

        {/* Profil kartı */}
        <ProfileHeader profile={profile} wallet={wallet} />

        {/* Tab Nav */}
        <div className="flex gap-1 bg-gray-800/50 rounded-xl p-1">
          {TABS.map(tab => (
            <button key={tab.key} onClick={() => setActiveTab(tab.key)}
                    className={`flex-1 py-2 rounded-lg text-xs font-medium transition-colors
                                ${activeTab === tab.key
                                  ? 'bg-eco-green/20 text-eco-green border border-eco-green/30'
                                  : 'text-gray-500 hover:text-gray-300'}`}>
              {tab.label}
            </button>
          ))}
        </div>

        {/* Tab içerikleri */}
        {activeTab === 'profile'     && <ProfileTab    profile={profile} onSave={updateProfile} />}
        {activeTab === 'preferences' && <PreferencesTab profile={profile} onSave={updatePreferences} />}
        {activeTab === 'eco'         && (
          <EcoTab
            wallet={wallet}
            transactions={transactions}
            onOpenRewards={() => setShowRewards(true)}
          />
        )}
      </div>

      {showRewards && (
        <RewardsModal
          rewards={rewards}
          balance={wallet?.currentBalance ?? 0}
          onSpend={spendPoints}
          onClose={() => setShowRewards(false)}
        />
      )}
    </div>
  )
}

// ── Profil Başlık Kartı ───────────────────────────────────────────────────

function ProfileHeader({ profile, wallet }) {
  const initials = profile?.fullName?.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase() ?? '?'

  return (
    <div className="bg-gray-800 rounded-xl border border-gray-700 p-5 flex items-start gap-4">
      {/* Avatar */}
      {profile?.avatarUrl ? (
        <img src={profile.avatarUrl} alt="avatar"
             className="w-16 h-16 rounded-full object-cover flex-shrink-0" />
      ) : (
        <div className="w-16 h-16 rounded-full bg-eco-green/20 border-2 border-eco-green/40
                        flex items-center justify-center flex-shrink-0">
          <span className="text-eco-green text-xl font-bold">{initials}</span>
        </div>
      )}

      {/* Bilgiler */}
      <div className="flex-1 min-w-0">
        <p className="text-white font-bold text-lg leading-tight">{profile?.fullName ?? '—'}</p>
        <p className="text-gray-400 text-sm">{profile?.email}</p>
        <div className="mt-1.5">
          <TierBadge tierLevel={wallet?.tierLevel ?? 'GREEN'} size="sm" />
        </div>

        {/* Cüzdan özeti */}
        {wallet && (
          <div className="mt-3">
            <div className="flex items-center justify-between text-xs text-gray-400 mb-1">
              <span>🌿 {wallet.currentBalance} puan</span>
              {wallet.nextTierName && (
                <span>{wallet.nextTierName}'a {wallet.pointsToNextTier} puan kaldı</span>
              )}
            </div>
            <ProgressBar
              value={wallet.progressPct}
              color={wallet.tierLevel === 'GOLD' ? 'gold' : wallet.tierLevel === 'PLATINUM' ? 'purple' : 'green'}
              height={6}
              showLabel={false}
              animated
            />
          </div>
        )}
      </div>
    </div>
  )
}

// ── Tab 1: Profil Bilgileri ───────────────────────────────────────────────

function ProfileTab({ profile, onSave }) {
  const [form, setForm] = useState({
    fullName: profile?.fullName ?? '',
    phone:    profile?.phone ?? '',
  })
  const [saving, setSaving] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setSaving(true)
    try { await onSave(form) } finally { setSaving(false) }
  }

  return (
    <form onSubmit={handleSubmit} className="bg-gray-800 rounded-xl border border-gray-700 p-5 space-y-4">
      <Field label="Ad Soyad" value={form.fullName}
             onChange={v => setForm(p => ({ ...p, fullName: v }))} />
      <Field label="Telefon"  value={form.phone}
             onChange={v => setForm(p => ({ ...p, phone: v }))} type="tel" />
      <div>
        <label className="block text-xs text-gray-500 mb-1">E-posta</label>
        <input value={profile?.email ?? ''} readOnly
               className="w-full bg-gray-900 border border-gray-700 rounded-lg px-3 py-2
                          text-gray-500 text-sm cursor-not-allowed" />
      </div>
      <button type="submit" disabled={saving}
              className="w-full py-2.5 rounded-xl bg-eco-green text-gray-900 font-bold
                         text-sm hover:bg-green-400 transition-colors disabled:opacity-50">
        {saving ? 'Kaydediliyor...' : 'Kaydet'}
      </button>
    </form>
  )
}

function Field({ label, value, onChange, type = 'text' }) {
  return (
    <div>
      <label className="block text-xs text-gray-400 mb-1">{label}</label>
      <input type={type} value={value} onChange={e => onChange(e.target.value)}
             className="w-full bg-gray-900 border border-gray-700 rounded-lg px-3 py-2
                        text-white text-sm focus:border-eco-green/50 focus:outline-none
                        transition-colors" />
    </div>
  )
}

// ── Tab 2: Tercihler ─────────────────────────────────────────────────────

function PreferencesTab({ profile, onSave }) {
  const prefs = profile?.preferences ?? {}
  const [form, setForm] = useState({
    seatPreference:   prefs.seatPreference   ?? 'NO_PREFERENCE',
    mealPreference:   prefs.mealPreference   ?? 'STANDARD',
    crowdAlerts:      prefs.crowdAlerts      ?? true,
    flightUpdates:    prefs.flightUpdates    ?? true,
    routeSuggestions: prefs.routeSuggestions ?? true,
    ecoRewards:       prefs.ecoRewards       ?? true,
  })

  const debounceRef = useRef(null)

  const handleChange = (key, value) => {
    const next = { ...form, [key]: value }
    setForm(next)
    clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => onSave(next), 800)
  }

  return (
    <div className="bg-gray-800 rounded-xl border border-gray-700 p-5 space-y-5">
      {/* Uçuş Tercihleri */}
      <div>
        <p className="text-white font-semibold text-sm mb-3">Uçuş Tercihleri</p>
        <div className="space-y-3">
          <div>
            <label className="block text-xs text-gray-400 mb-2">Koltuk Tercihi</label>
            <div className="flex gap-2">
              {[['WINDOW','Pencere'],['AISLE','Orta Yol'],['NO_PREFERENCE','Fark Etmez']].map(([v,l]) => (
                <button key={v} onClick={() => handleChange('seatPreference', v)}
                        className={`flex-1 py-1.5 rounded-lg text-xs border transition-colors
                                    ${form.seatPreference === v
                                      ? 'bg-eco-green/20 border-eco-green/50 text-eco-green'
                                      : 'border-gray-700 text-gray-400 hover:border-gray-500'}`}>
                  {l}
                </button>
              ))}
            </div>
          </div>
          <div>
            <label className="block text-xs text-gray-400 mb-1">Yemek Tercihi</label>
            <select value={form.mealPreference}
                    onChange={e => handleChange('mealPreference', e.target.value)}
                    className="w-full bg-gray-900 border border-gray-700 rounded-lg px-3 py-2
                               text-sm text-white focus:border-eco-green/50 focus:outline-none">
              <option value="STANDARD">Standart</option>
              <option value="VEGETARIAN">Vejetaryen</option>
              <option value="VEGAN">Vegan</option>
            </select>
          </div>
        </div>
      </div>

      {/* Bildirim Tercihleri */}
      <div>
        <p className="text-white font-semibold text-sm mb-3">Bildirim Tercihleri</p>
        <div className="space-y-2">
          {[
            ['crowdAlerts',      'Yoğunluk Uyarıları'],
            ['flightUpdates',    'Uçuş Güncellemeleri'],
            ['routeSuggestions', 'Rota Önerileri'],
            ['ecoRewards',       'Eko-Ödül Bildirimleri'],
          ].map(([key, label]) => (
            <div key={key} className="flex items-center justify-between py-1.5">
              <span className="text-gray-300 text-sm">{label}</span>
              <button onClick={() => handleChange(key, !form[key])}
                      className={`relative w-10 h-5 rounded-full transition-colors
                                  ${form[key] ? 'bg-eco-green' : 'bg-gray-600'}`}>
                <span className={`absolute top-0.5 w-4 h-4 bg-white rounded-full shadow
                                  transition-transform ${form[key] ? 'translate-x-5' : 'translate-x-0.5'}`} />
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

// ── Tab 3: Eko-Puanlarım ─────────────────────────────────────────────────

function EcoTab({ wallet, transactions, onOpenRewards }) {
  return (
    <div className="space-y-4">
      {/* Cüzdan kartı */}
      {wallet && (
        <div className="bg-gray-800 rounded-xl border border-gray-700 p-5">
          <div className="text-center mb-4">
            <p className="text-6xl font-bold text-eco-green">{wallet.currentBalance}</p>
            <p className="text-gray-400 text-sm mt-1">Eko-Puan</p>
            <div className="mt-2 flex justify-center">
              <TierBadge tierLevel={wallet.tierLevel} size="md" />
            </div>
          </div>
          {wallet.nextTierName && (
            <div>
              <div className="flex justify-between text-xs text-gray-400 mb-1.5">
                <span>{wallet.currentBalance} puan</span>
                <span>{wallet.nextTierName}'a {wallet.pointsToNextTier} puan kaldı</span>
              </div>
              <ProgressBar
                value={wallet.progressPct}
                color={wallet.tierLevel === 'GOLD' ? 'gold' : 'green'}
                height={8}
                showLabel
                animated
              />
            </div>
          )}
          <button onClick={onOpenRewards}
                  className="w-full mt-4 py-2.5 rounded-xl bg-eco-green/10 border border-eco-green/30
                             text-eco-green font-semibold text-sm hover:bg-eco-green/20 transition-colors">
            Ödülleri Keşfet 🎁
          </button>
        </div>
      )}

      {/* İşlem Geçmişi */}
      <div className="bg-gray-800 rounded-xl border border-gray-700 p-4">
        <h3 className="text-white font-semibold text-sm mb-3">İşlem Geçmişi</h3>
        {transactions.length === 0 ? (
          <p className="text-gray-500 text-sm text-center py-4">Henüz işlem yok</p>
        ) : (
          <div className="space-y-2">
            {transactions.map(tx => (
              <div key={tx.transId}
                   className="flex items-center justify-between py-2 border-b border-gray-700/50 last:border-0">
                <div className="flex items-center gap-2">
                  <span className={`text-base ${tx.transType === 'EARN' ? 'text-green-400' : 'text-red-400'}`}>
                    {tx.transType === 'EARN' ? '⬆' : '⬇'}
                  </span>
                  <div>
                    <p className="text-gray-300 text-xs font-medium">{tx.rewardTitle ?? tx.description}</p>
                    <p className="text-gray-600 text-[10px]">{tx.timeAgo}</p>
                  </div>
                </div>
                <span className={`font-bold text-sm ${tx.transType === 'EARN' ? 'text-green-400' : 'text-red-400'}`}>
                  {tx.transType === 'EARN' ? '+' : '-'}{tx.amount}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function PageSkeleton() {
  return (
    <div className="bg-gray-900 p-6 animate-pulse">
      <div className="max-w-2xl mx-auto space-y-4">
        <div className="h-28 bg-gray-800 rounded-xl" />
        <div className="h-10 bg-gray-800 rounded-xl" />
        <div className="h-48 bg-gray-800 rounded-xl" />
      </div>
    </div>
  )
}
