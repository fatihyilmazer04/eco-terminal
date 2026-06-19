import React, { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import toast from 'react-hot-toast'

/* ── Ortak sol panel ─────────────────────────────────────────────────────── */
function LeftPanel() {
  return (
    <div className="hidden md:flex w-[42%] bg-[#0D1528] border-r border-[#1A2540] flex-col justify-between p-10">
      <div className="flex-1 flex flex-col items-center justify-center gap-5">
        <svg width="80" height="80" viewBox="0 0 80 80" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect width="80" height="80" rx="18" fill="#0F2240"/>
          <rect width="80" height="80" rx="18" fill="none" stroke="#1A3A60" strokeWidth="1.5"/>
          <path d="M40 14C40 14 26 23 26 35c0 8 4 13.5 10.5 16.5" stroke="#2ECC71" strokeWidth="2.4" strokeLinecap="round" fill="none"/>
          <path d="M40 14c0 0 14 9 14 21 0 8-4 13.5-10.5 16.5" stroke="#2ECC71" strokeWidth="2.4" strokeLinecap="round" fill="none" opacity="0.4"/>
          <path d="M36.5 51.5C38 55 39 56 40 59c1-3 2-4 3.5-7.5" stroke="#2ECC71" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" fill="none"/>
          <path d="M28 30Q40 27 52 30" stroke="#2ECC71" strokeWidth="1" strokeLinecap="round" fill="none" opacity="0.25"/>
          <circle cx="40" cy="36" r="4.5" fill="#2ECC71" opacity="0.9"/>
        </svg>
        <div className="text-center">
          <div className="text-white text-2xl font-medium tracking-wide">EcoTerminal</div>
          <div className="w-8 h-0.5 bg-eco-green mx-auto mt-2.5"></div>
        </div>
      </div>
      <div className="text-[#1E3050] text-[11px] text-center">© 2026 EcoTerminal</div>
    </div>
  )
}

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
    <div className="min-h-screen bg-[#0B1120] flex">
      <LeftPanel />

      {/* Sağ panel */}
      <div className="flex-1 flex items-center justify-center p-10">
        <div className="max-w-[280px] w-full flex flex-col gap-6">

          {/* Başlık */}
          <div>
            <h1 className="text-white text-lg font-normal mb-1">Giriş Yap</h1>
            <p className="text-[#3A5070] text-xs">Hesabınıza erişin</p>
          </div>

          {/* API Hata */}
          {apiError && (
            <div className="px-3 py-2.5 rounded-md bg-red-500/10 border border-red-500/20 text-red-400 text-xs">
              {apiError}
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-5">

            {/* E-posta */}
            <div>
              <label className="text-[#4A6080] text-[11px] block mb-1.5">E-posta</label>
              <div className={`border-b pb-2.5 flex items-center gap-2 ${errors.email ? 'border-red-500/60' : 'border-[#1E3050]'}`}>
                <svg className="w-4 h-4 text-[#253545] flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8}
                    d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"/>
                </svg>
                <input
                  name="email" type="email" autoComplete="email"
                  value={form.email} onChange={handleChange}
                  placeholder="ornek@mail.com"
                  className="bg-transparent flex-1 text-[#8AA0BC] text-[13px] outline-none placeholder-[#253545]"
                />
              </div>
              {errors.email && <p className="mt-1 text-[11px] text-red-400">{errors.email}</p>}
            </div>

            {/* Şifre */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="text-[#4A6080] text-[11px]">Şifre</label>
                <Link to="/forgot-password" className="text-eco-green text-[11px] hover:text-green-400 transition-colors">
                  Şifremi Unuttum?
                </Link>
              </div>
              <div className={`border-b pb-2.5 flex items-center gap-2 ${errors.password ? 'border-red-500/60' : 'border-[#1E3050]'}`}>
                <svg className="w-4 h-4 text-[#253545] flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8}
                    d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"/>
                </svg>
                <input
                  name="password" type="password" autoComplete="current-password"
                  value={form.password} onChange={handleChange}
                  placeholder="••••••••"
                  className="bg-transparent flex-1 text-[#8AA0BC] text-[13px] outline-none placeholder-[#253545]"
                />
              </div>
              {errors.password && <p className="mt-1 text-[11px] text-red-400">{errors.password}</p>}
            </div>

            {/* Giriş butonu */}
            <button
              type="submit" disabled={loading}
              className="w-full bg-eco-green text-[#0B1120] py-3 rounded-md text-[13px] font-medium hover:bg-green-400 transition-colors disabled:opacity-60 flex items-center justify-center gap-2"
            >
              {loading
                ? <span className="w-4 h-4 border-2 border-[#0B1120] border-t-transparent rounded-full animate-spin" />
                : 'Giriş Yap'
              }
            </button>
          </form>

          {/* Alt link */}
          <p className="text-center text-[#3A5070] text-xs">
            Hesabın yok mu?{' '}
            <Link to="/register" className="text-eco-green hover:text-green-400 transition-colors">
              Kayıt Ol
            </Link>
          </p>

          {/* Test bilgileri (sadece geliştirme) */}
          {import.meta.env.DEV && (
            <div className="p-3 rounded-md bg-[#0D1528] border border-[#1A2540] text-[11px] text-[#2A4060]">
              <p className="text-[#3A5070] mb-1">Test Hesapları:</p>
              <p>Admin: admin@ecoterminal.com / admin123</p>
              <p>Yolcu: passenger@ecoterminal.com / pass123</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
