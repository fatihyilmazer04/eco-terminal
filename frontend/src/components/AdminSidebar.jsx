import React from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

const NAV_ITEMS = [
  { to: '/admin/dashboard',   label: 'Dashboard',           icon: '🏠' },
  { to: '/admin/occupancy',   label: 'Yoğunluk Yönetimi',  icon: '📊' },
  { to: '/admin/energy',      label: 'Enerji Yönetimi',     icon: '⚡' },
  { to: '/admin/predictions', label: 'AI Tahminler',        icon: '🤖' },
  { to: '/admin/reports',     label: 'Raporlar',            icon: '📋' },
  { to: '/admin/settings',    label: 'Sistem Ayarları',     icon: '⚙️' },
]

export default function AdminSidebar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <aside className="w-56 min-h-screen bg-gray-900 border-r border-gray-800 flex flex-col">
      {/* Logo */}
      <div className="p-4 border-b border-gray-800">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-eco-green/20 border border-eco-green/40 flex items-center justify-center">
            <span className="text-eco-green text-sm font-bold">E</span>
          </div>
          <div>
            <p className="text-white text-sm font-bold leading-none">EcoTerminal</p>
            <p className="text-eco-green text-xs">Admin</p>
          </div>
        </div>
      </div>

      {/* Navigasyon */}
      <nav className="flex-1 p-3 space-y-0.5">
        {NAV_ITEMS.map(item => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) =>
              `flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm transition-colors
               ${isActive
                 ? 'bg-eco-green/10 text-eco-green border-l-2 border-eco-green font-medium'
                 : 'text-gray-400 hover:text-gray-200 hover:bg-gray-800'
               }`
            }
          >
            <span className="text-base">{item.icon}</span>
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>

      {/* Kullanıcı bilgisi + çıkış */}
      <div className="p-3 border-t border-gray-800">
        <div className="flex items-center gap-2.5 px-3 py-2 mb-1">
          <div className="w-7 h-7 rounded-full bg-eco-green/20 border border-eco-green/40
                          flex items-center justify-center flex-shrink-0">
            <span className="text-eco-green text-xs font-bold">
              {user?.email?.[0]?.toUpperCase() ?? 'A'}
            </span>
          </div>
          <div className="min-w-0">
            <p className="text-white text-xs font-medium truncate">{user?.email ?? 'Admin'}</p>
            <p className="text-gray-500 text-xs">Yönetici</p>
          </div>
        </div>
        <button
          onClick={handleLogout}
          className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm
                     text-gray-400 hover:text-red-400 hover:bg-red-500/10 transition-colors"
        >
          <span>🚪</span>
          <span>Çıkış Yap</span>
        </button>
      </div>
    </aside>
  )
}
