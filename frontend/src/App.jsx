import React from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import { AuthProvider, useAuth } from './context/AuthContext'
import PrivateRoute from './components/PrivateRoute'
import AdminRoute from './components/AdminRoute'
import AdminLayout from './components/AdminLayout'
import Navbar from './components/Navbar'
import LoginPage from './pages/auth/LoginPage'
import RegisterPage from './pages/auth/RegisterPage'
import PassengerDashboard from './pages/passenger/PassengerDashboard'
import HeatmapPage from './pages/passenger/HeatmapPage'
import FlightInfoPage from './pages/passenger/FlightInfoPage'
import RouteSuggestionPage from './pages/passenger/RouteSuggestionPage'
import LoungesPage from './pages/passenger/LoungesPage'
import NotificationsPage from './pages/passenger/NotificationsPage'
import ProfilePage from './pages/passenger/ProfilePage'
import AdminDashboard from './pages/admin/AdminDashboard'
import OccupancyManagement from './pages/admin/OccupancyManagement'
import EnergyManagement from './pages/admin/EnergyManagement'
import ReportsPage from './pages/admin/ReportsPage'
import AIPredictionsPage from './pages/admin/AIPredictionsPage'
import { useFcmToken } from './hooks/useFcmToken'

// Placeholder — sonraki fazlarda gerçek sayfalarla değiştirilecek
const PlaceholderPage = ({ title }) => (
  <div className="min-h-screen flex items-center justify-center bg-gray-900">
    <div className="eco-card text-center max-w-sm">
      <div className="w-12 h-12 rounded-xl bg-eco-green/10 border border-eco-green/30 flex items-center justify-center mx-auto mb-4">
        <span className="text-eco-green text-xl">✦</span>
      </div>
      <h1 className="text-xl font-bold text-white mb-1">{title}</h1>
      <p className="text-gray-400 text-sm">Bu sayfa bir sonraki fazda gelecek.</p>
    </div>
  </div>
)

function RootRedirect() {
  const { isAuthenticated, user, isLoading } = useAuth()
  if (isLoading) return <LoadingSpinner />
  if (!isAuthenticated) return <Navigate to="/login" replace />
  return user?.role === 'ADMIN'
    ? <Navigate to="/admin/dashboard" replace />
    : <Navigate to="/passenger/dashboard" replace />
}

function LoadingSpinner() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-900">
      <div className="w-10 h-10 border-4 border-eco-green border-t-transparent rounded-full animate-spin" />
    </div>
  )
}

/** Admin sayfalarını AdminLayout + AdminRoute ile sarar */
function AdminPage({ children }) {
  return (
    <AdminRoute>
      <AdminLayout>{children}</AdminLayout>
    </AdminRoute>
  )
}

/** Yolcu sayfalarını Navbar + PrivateRoute ile sarar */
function PassengerPage({ children }) {
  return (
    <PrivateRoute>
      <div className="min-h-screen bg-gray-900 flex flex-col">
        <Navbar />
        <div className="flex-1">{children}</div>
      </div>
    </PrivateRoute>
  )
}

function AppRoutes() {
  useFcmToken()  // Uygulama açılışında bildirim izni iste + FCM token al
  return (
    <Routes>
      <Route path="/" element={<RootRedirect />} />

      {/* Auth — herkese açık */}
      <Route path="/login"    element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      {/* Yolcu rotaları — Navbar dahil */}
      <Route path="/passenger/dashboard"      element={<PassengerPage><PassengerDashboard /></PassengerPage>} />
      <Route path="/passenger/heatmap"        element={<PassengerPage><HeatmapPage /></PassengerPage>} />
      <Route path="/passenger/route"          element={<PassengerPage><RouteSuggestionPage /></PassengerPage>} />
      <Route path="/passenger/flights"        element={<PassengerPage><FlightInfoPage /></PassengerPage>} />
      <Route path="/passenger/lounges"        element={<PassengerPage><LoungesPage /></PassengerPage>} />
      <Route path="/passenger/notifications"  element={<PassengerPage><NotificationsPage /></PassengerPage>} />
      <Route path="/passenger/profile"         element={<PassengerPage><ProfilePage /></PassengerPage>} />

      {/* Admin rotaları — AdminLayout + Sidebar */}
      <Route path="/admin/dashboard"   element={<AdminPage><AdminDashboard /></AdminPage>} />
      <Route path="/admin/occupancy"   element={<AdminPage><OccupancyManagement /></AdminPage>} />
      <Route path="/admin/energy"      element={<AdminPage><EnergyManagement /></AdminPage>} />
      <Route path="/admin/reports"     element={<AdminPage><ReportsPage /></AdminPage>} />
      <Route path="/admin/predictions" element={<AdminPage><AIPredictionsPage /></AdminPage>} />
      <Route path="/admin/settings"    element={<AdminPage><PlaceholderPage title="Sistem Ayarları" /></AdminPage>} />

      <Route path="*" element={<PlaceholderPage title="404 — Sayfa Bulunamadı" />} />
    </Routes>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
        <Toaster
          position="top-right"
          toastOptions={{
            style: {
              background: '#1F2937',
              color: '#F9FAFB',
              border: '1px solid #374151',
              borderRadius: '0.75rem',
            },
            success: { iconTheme: { primary: '#2ECC71', secondary: '#1F2937' } },
            error:   { iconTheme: { primary: '#E74C3C', secondary: '#1F2937' } },
          }}
        />
      </AuthProvider>
    </BrowserRouter>
  )
}
