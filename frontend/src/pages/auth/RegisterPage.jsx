import React, { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import toast from 'react-hot-toast'

export default function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()

  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirmPassword: '' })
  const [errors, setErrors] = useState({})
  const [loading, setLoading] = useState(false)
  const [apiError, setApiError] = useState('')

  function handleChange(e) {
    const { name, value } = e.target
    setForm(prev => ({ ...prev, [name]: value }))
    if (errors[name]) setErrors(prev => ({ ...prev, [name]: '' }))
    if (apiError) setApiError('')
  }

  function validate() {
    const e = {}
    if (!form.fullName || form.fullName.length < 2) e.fullName = 'Ad soyad en az 2 karakter olmalıdır'
    if (!form.email) e.email = 'E-posta zorunludur'
    else if (!/\S+@\S+\.\S+/.test(form.email)) e.email = 'Geçerli bir e-posta girin'
    if (!form.password || form.password.length < 6) e.password = 'Şifre en az 6 karakter olmalıdır'
    if (form.password !== form.confirmPassword) e.confirmPassword = 'Şifreler eşleşmiyor'
    return e
  }

  async function handleSubmit(e) {
    e.preventDefault()
    const validationErrors = validate()
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors)
      return
    }

    setLoading(true)
    try {
      await register(form.email, form.password, form.fullName)
      toast.success('Kayıt başarılı! Hoş geldiniz.')
      navigate('/passenger/dashboard', { replace: true })
    } catch (err) {
      const message = err.response?.data?.message || 'Kayıt başarısız. Tekrar deneyin.'
      setApiError(message)
      toast.error(message)
    } finally {
      setLoading(false)
    }
  }

  const fields = [
    { id: 'fullName',        label: 'Ad Soyad',          type: 'text',     placeholder: 'Adınız Soyadınız',    autoComplete: 'name' },
    { id: 'email',           label: 'E-posta',            type: 'email',    placeholder: 'ornek@mail.com',      autoComplete: 'email' },
    { id: 'password',        label: 'Şifre',              type: 'password', placeholder: '••••••••',            autoComplete: 'new-password' },
    { id: 'confirmPassword', label: 'Şifre Tekrar',       type: 'password', placeholder: '••••••••',            autoComplete: 'new-password' },
  ]

  return (
    <div className="min-h-screen bg-gray-900 flex items-center justify-center px-4 py-8">
      <div className="w-full max-w-md">

        {/* Başlık */}
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-white">Eco-Terminal</h1>
          <p className="text-gray-400 mt-1 text-sm">Yeni hesap oluştur</p>
        </div>

        <div className="eco-card">
          <h2 className="text-xl font-semibold text-white mb-6">Kayıt Ol</h2>

          {apiError && (
            <div className="mb-4 px-4 py-3 rounded-lg bg-red-500/10 border border-red-500/30 text-red-400 text-sm">
              {apiError}
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            {fields.map(field => (
              <div key={field.id}>
                <label htmlFor={field.id} className="eco-label">{field.label}</label>
                <input
                  id={field.id}
                  name={field.id}
                  type={field.type}
                  autoComplete={field.autoComplete}
                  value={form[field.id]}
                  onChange={handleChange}
                  placeholder={field.placeholder}
                  className={`eco-input ${errors[field.id] ? 'border-red-500 focus:border-red-500 focus:ring-red-500' : ''}`}
                />
                {errors[field.id] && (
                  <p className="mt-1 text-xs text-red-400">{errors[field.id]}</p>
                )}
              </div>
            ))}

            <button
              type="submit"
              disabled={loading}
              className="eco-btn-primary w-full flex items-center justify-center gap-2 mt-2"
            >
              {loading ? (
                <>
                  <span className="w-4 h-4 border-2 border-gray-900 border-t-transparent rounded-full animate-spin" />
                  Kayıt yapılıyor...
                </>
              ) : 'Kayıt Ol'}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-gray-400">
            Zaten hesabın var mı?{' '}
            <Link to="/login" className="text-eco-green hover:text-green-400 font-medium transition-colors">
              Giriş Yap
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
