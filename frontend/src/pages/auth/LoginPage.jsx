import React, { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import toast from 'react-hot-toast'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()

  const [form, setForm] = useState({ email: '', password: '' })
  const [errors, setErrors] = useState({})
  const [loading, setLoading] = useState(false)
  const [apiError, setApiError] = useState('')

  function handleChange(e) {
    const { name, value } = e.target
    setForm(prev => ({ ...prev, [name]: value }))
    // Yazarken hatayı temizle
    if (errors[name]) setErrors(prev => ({ ...prev, [name]: '' }))
    if (apiError) setApiError('')
  }

  function validate() {
    const newErrors = {}
    if (!form.email) newErrors.email = 'E-posta zorunludur'
    else if (!/\S+@\S+\.\S+/.test(form.email)) newErrors.email = 'Geçerli bir e-posta girin'
    if (!form.password) newErrors.password = 'Şifre zorunludur'
    else if (form.password.length < 6) newErrors.password = 'En az 6 karakter'
    return newErrors
  }

  async function handleSubmit(e) {
    e.preventDefault()
    const validationErrors = validate()
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors)
      return
    }

    setLoading(true)
    setApiError('')

    try {
      const role = await login(form.email, form.password)
      toast.success('Hoş geldiniz!')
      // Role göre yönlendir
      navigate(role === 'ADMIN' ? '/admin/dashboard' : '/passenger/dashboard', { replace: true })
    } catch (err) {
      const message = err.response?.data?.message || 'Giriş başarısız. Tekrar deneyin.'
      setApiError(message)
      toast.error(message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-900 flex items-center justify-center px-4">
      <div className="w-full max-w-md">

        {/* Logo / Başlık */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-eco-green/10 border border-eco-green/30 mb-4">
            <svg className="w-8 h-8 text-eco-green" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M3.055 11H5a2 2 0 012 2v1a2 2 0 002 2 2 2 0 012 2v2.945M8 3.935V5.5A2.5 2.5 0 0010.5 8h.5a2 2 0 012 2 2 2 0 104 0 2 2 0 012-2h1.064M15 20.488V18a2 2 0 012-2h3.064" />
            </svg>
          </div>
          <h1 className="text-3xl font-bold text-white">Eco-Terminal</h1>
          <p className="text-gray-400 mt-1 text-sm">Akıllı Havalimanı Yönetim Sistemi</p>
        </div>

        {/* Form Kartı */}
        <div className="eco-card">
          <h2 className="text-xl font-semibold text-white mb-6">Giriş Yap</h2>

          {/* API Hata Mesajı */}
          {apiError && (
            <div className="mb-4 px-4 py-3 rounded-lg bg-red-500/10 border border-red-500/30 text-red-400 text-sm">
              {apiError}
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate className="space-y-4">

            {/* E-posta */}
            <div>
              <label htmlFor="email" className="eco-label">E-posta</label>
              <input
                id="email"
                name="email"
                type="email"
                autoComplete="email"
                value={form.email}
                onChange={handleChange}
                placeholder="ornek@mail.com"
                className={`eco-input ${errors.email ? 'border-red-500 focus:border-red-500 focus:ring-red-500' : ''}`}
              />
              {errors.email && (
                <p className="mt-1 text-xs text-red-400">{errors.email}</p>
              )}
            </div>

            {/* Şifre */}
            <div>
              <label htmlFor="password" className="eco-label">Şifre</label>
              <input
                id="password"
                name="password"
                type="password"
                autoComplete="current-password"
                value={form.password}
                onChange={handleChange}
                placeholder="••••••••"
                className={`eco-input ${errors.password ? 'border-red-500 focus:border-red-500 focus:ring-red-500' : ''}`}
              />
              {errors.password && (
                <p className="mt-1 text-xs text-red-400">{errors.password}</p>
              )}
            </div>

            {/* Şifremi Unuttum */}
            <div className="flex justify-end -mt-1">
              <Link
                to="/forgot-password"
                className="text-xs text-gray-500 hover:text-eco-green transition-colors"
              >
                Şifremi Unuttum?
              </Link>
            </div>

            {/* Giriş Butonu */}
            <button
              type="submit"
              disabled={loading}
              className="eco-btn-primary w-full flex items-center justify-center gap-2 mt-2"
            >
              {loading ? (
                <>
                  <span className="w-4 h-4 border-2 border-gray-900 border-t-transparent rounded-full animate-spin" />
                  Giriş yapılıyor...
                </>
              ) : 'Giriş Yap'}
            </button>
          </form>

          {/* Kayıt Linki */}
          <p className="mt-6 text-center text-sm text-gray-400">
            Hesabın yok mu?{' '}
            <Link to="/register" className="text-eco-green hover:text-green-400 font-medium transition-colors">
              Kayıt Ol
            </Link>
          </p>
        </div>

        {/* Test Bilgileri (sadece geliştirme) */}
        {import.meta.env.DEV && (
          <div className="mt-4 p-3 rounded-lg bg-gray-800/50 border border-gray-700 text-xs text-gray-500">
            <p className="font-medium text-gray-400 mb-1">Test Hesapları:</p>
            <p>Admin: admin@ecoterminal.com / admin123</p>
            <p>Yolcu: passenger@ecoterminal.com / pass123</p>
          </div>
        )}
      </div>
    </div>
  )
}
