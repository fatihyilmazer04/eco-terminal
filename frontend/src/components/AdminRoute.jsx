import React from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

/**
 * Sadece ADMIN rolüne izin verir.
 * Authenticated ama USER rolünde ise /passenger/dashboard'a yönlendirir.
 */
export default function AdminRoute({ children }) {
  const { isAuthenticated, user, isLoading } = useAuth()

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-900">
        <div className="w-10 h-10 border-4 border-eco-green border-t-transparent rounded-full animate-spin" />
      </div>
    )
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  if (user?.role !== 'ADMIN') {
    return <Navigate to="/passenger/dashboard" replace />
  }

  return children
}
