import React from 'react'
import AdminSidebar from './AdminSidebar'

/**
 * Admin sayfaları için ortak layout: sidebar sol, içerik sağ.
 */
export default function AdminLayout({ children }) {
  return (
    <div className="flex min-h-screen bg-gray-900">
      <AdminSidebar />
      <main className="flex-1 flex flex-col min-w-0">
        {children}
      </main>
    </div>
  )
}
