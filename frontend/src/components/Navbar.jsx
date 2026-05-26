import React, { useState, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useNotifications } from '../hooks/useNotifications'
import NotificationBell from './NotificationBell'
import EcoPointsBadge from './EcoPointsBadge'
import { loyaltyApi } from '../api/loyaltyApi'

/**
 * Yolcu sayfaları için üst navigasyon çubuğu.
 * - Sol: Eco-Terminal logo + yeşil sistem aktif noktası
 * - Sağ: NotificationBell + kullanıcı adı + çıkış
 * - Admin modunda: "Admin Panel" kırmızı badge
 * - Mobil: hamburger menü
 */
export default function Navbar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [mobileOpen, setMobileOpen] = useState(false)
  const { notifications, unreadCount, markAsRead, markAllAsRead } = useNotifications()
  const [walletBalance, setWalletBalance] = useState(null)
  const [walletTier, setWalletTier]       = useState('GREEN')

  useEffect(() => {
    loyaltyApi.getWallet()
      .then(r => {
        setWalletBalance(r.data.data?.currentBalance ?? 0)
        setWalletTier(r.data.data?.tierLevel ?? 'GREEN')
      })
      .catch(() => {})
  }, [])

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  const isAdmin = user?.role === 'ADMIN'

  return (
    <nav className="bg-gray-900 border-b border-gray-800 sticky top-0 z-40">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-14">
          {/* Logo */}
          <Link to={isAdmin ? '/admin/dashboard' : '/passenger/dashboard'}
                className="flex items-center gap-2">
            <div className="flex items-center gap-1.5">
              <div className="w-6 h-6 rounded-md bg-eco-green/20 border border-eco-green/40
                              flex items-center justify-center">
                <span className="text-eco-green text-xs font-bold">E</span>
              </div>
              <span className="text-white font-semibold text-sm hidden sm:block">Eco-Terminal</span>
            </div>
            {/* Sistem aktif göstergesi */}
            <div className="flex items-center gap-1 ml-1">
              <span className="relative flex h-2 w-2">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-eco-green opacity-75" />
                <span className="relative inline-flex rounded-full h-2 w-2 bg-eco-green" />
              </span>
              <span className="text-eco-green text-xs hidden sm:block">Aktif</span>
            </div>
          </Link>

          {/* Desktop nav linkleri */}
          <div className="hidden sm:flex items-center gap-1 text-sm">
            <NavLink to="/passenger/dashboard" label="Ana Sayfa" />
            <NavLink to="/passenger/heatmap"   label="Isı Haritası" />
            <NavLink to="/passenger/flights"   label="Uçuşlar" />
            <NavLink to="/passenger/route"     label="Rota Öner" />
            <NavLink to="/passenger/lounges"   label="Salonlar" />
            <NavLink to="/passenger/rewards"   label="🌿 Ödüller" />
          </div>

          {/* Sağ: Actions */}
          <div className="flex items-center gap-2">
            {/* Admin badge */}
            {isAdmin && (
              <Link to="/admin/dashboard"
                    className="hidden sm:flex items-center px-2 py-1 rounded-md bg-red-500/20
                               border border-red-500/40 text-red-400 text-xs font-medium">
                Admin Panel
              </Link>
            )}

            {/* Bildirim çanı */}
            <NotificationBell
              notifications={notifications}
              unreadCount={unreadCount}
              onMarkAsRead={markAsRead}
              onMarkAllAsRead={markAllAsRead}
            />

            {/* EcoPoints badge (masaüstü) */}
            {walletBalance !== null && (
              <div className="hidden sm:block">
                <Link to="/passenger/profile">
                  <EcoPointsBadge balance={walletBalance} tierLevel={walletTier} />
                </Link>
              </div>
            )}

            {/* Kullanıcı adı + çıkış */}
            <div className="hidden sm:flex items-center gap-2">
              <Link to="/passenger/profile"
                    className="text-gray-400 text-xs hover:text-white transition-colors">
                {user?.fullName?.split(' ')[0] ?? user?.email}
              </Link>
              <button
                onClick={handleLogout}
                className="px-2 py-1 rounded-lg text-gray-500 text-xs hover:text-red-400
                           hover:bg-red-500/10 transition-colors border border-transparent
                           hover:border-red-500/30"
              >
                Çıkış
              </button>
            </div>

            {/* Hamburger (mobil) */}
            <button
              onClick={() => setMobileOpen(prev => !prev)}
              className="sm:hidden p-2 rounded-lg text-gray-400 hover:text-white hover:bg-gray-800"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                {mobileOpen
                  ? <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  : <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
                }
              </svg>
            </button>
          </div>
        </div>

        {/* Mobil menü */}
        {mobileOpen && (
          <div className="sm:hidden border-t border-gray-800 py-2 space-y-1">
            <MobileLink to="/passenger/dashboard" label="Ana Sayfa"    onClick={() => setMobileOpen(false)} />
            <MobileLink to="/passenger/heatmap"   label="Isı Haritası" onClick={() => setMobileOpen(false)} />
            <MobileLink to="/passenger/flights"   label="Uçuşlar"      onClick={() => setMobileOpen(false)} />
            <MobileLink to="/passenger/route"     label="Rota Öner"    onClick={() => setMobileOpen(false)} />
            <MobileLink to="/passenger/lounges"        label="Salonlar"           onClick={() => setMobileOpen(false)} />
            <MobileLink to="/passenger/rewards"        label="🌿 Ödüller"          onClick={() => setMobileOpen(false)} />
            <MobileLink to="/passenger/notifications"   label={`Bildirimler${unreadCount > 0 ? ` (${unreadCount})` : ''}`} onClick={() => setMobileOpen(false)} />
            <MobileLink to="/passenger/profile"         label="Profilim"           onClick={() => setMobileOpen(false)} />
            {isAdmin && (
              <MobileLink to="/admin/dashboard" label="Admin Panel" onClick={() => setMobileOpen(false)} />
            )}
            <button
              onClick={handleLogout}
              className="w-full text-left px-4 py-2 text-red-400 text-sm"
            >
              Çıkış Yap
            </button>
          </div>
        )}
      </div>
    </nav>
  )
}

function NavLink({ to, label }) {
  return (
    <Link to={to}
          className="px-3 py-1.5 rounded-lg text-gray-400 text-sm hover:text-white
                     hover:bg-gray-800 transition-colors">
      {label}
    </Link>
  )
}

function MobileLink({ to, label, onClick }) {
  return (
    <Link to={to} onClick={onClick}
          className="block px-4 py-2 text-gray-300 text-sm hover:text-white hover:bg-gray-800 rounded-lg">
      {label}
    </Link>
  )
}
