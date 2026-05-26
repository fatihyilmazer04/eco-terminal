import React, { useState, useEffect, useRef } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { authApi } from '../../api/authApi'
import toast from 'react-hot-toast'

const RESEND_COOLDOWN = 60 // saniye

export default function RegisterPage() {
  const { loginWithTokens } = useAuth()
  const navigate = useNavigate()

  // ── Adım yönetimi ──────────────────────────────────────────────────────────
  const [step, setStep] = useState(1) // 1: form, 2: kod doğrulama

  // ── Adım 1 form durumu ─────────────────────────────────────────────────────
  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirmPassword: '' })
  const [errors, setErrors] = useState({})

  // ── Adım 2 kod durumu ──────────────────────────────────────────────────────
  const [code, setCode] = useState('')
  const [codeError, setCodeError] = useState('')

  // ── Genel ─────────────────────────────────────────────────────────────────
  const [loading, setLoading] = useState(false)
  const [apiError, setApiError] = useState('')

  // ── Geri sayım (yeniden gönder) ────────────────────────────────────────────
  const [countdown, setCountdown] = useState(0)
  const timerRef = useRef(null)

  useEffect(() => {
    return () => clearInterval(timerRef.current)
  }, [])

  function startCountdown() {
    setCountdown(RESEND_COOLDOWN)
    timerRef.current = setInterval(() => {
      setCountdown(prev => {
        if (prev <= 1) { clearInterval(timerRef.current); return 0 }
        return prev - 1
      })
    }, 1000)
  }

  // ── Adım 1: Validasyon ─────────────────────────────────────────────────────
  function validate() {
    const e = {}
    if (!form.fullName || form.fullName.trim().length < 2)
      e.fullName = 'Ad soyad en az 2 karakter olmalıdır'
    if (!form.email)
      e.email = 'E-posta zorunludur'
    else if (!/\S+@\S+\.\S+/.test(form.email))
      e.email = 'Geçerli bir e-posta girin'
    if (!form.password || form.password.length < 6)
      e.password = 'Şifre en az 6 karakter olmalıdır'
    if (form.password !== form.confirmPassword)
      e.confirmPassword = 'Şifreler eşleşmiyor'
    return e
  }

  function handleFormChange(e) {
    const { name, value } = e.target
    setForm(prev => ({ ...prev, [name]: value }))
    if (errors[name]) setErrors(prev => ({ ...prev, [name]: '' }))
    if (apiError) setApiError('')
  }

  // ── Adım 1 submit: kod gönder ──────────────────────────────────────────────
  async function handleSendCode(e) {
    e.preventDefault()
    const validationErrors = validate()
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors)
      return
    }

    setLoading(true)
    setApiError('')
    try {
      await authApi.sendRegisterCode(form.email, form.fullName, form.password)
      toast.success('Doğrulama kodu e-posta adresinize gönderildi!')
      setStep(2)
      startCountdown()
    } catch (err) {
      const message = err.response?.data?.message || 'Kod gönderilemedi. Tekrar deneyin.'
      setApiError(message)
      toast.error(message)
    } finally {
      setLoading(false)
    }
  }

  // ── Adım 2: Kodu doğrula ve kayıt tamamla ─────────────────────────────────
  async function handleVerify(e) {
    e.preventDefault()
    if (code.length !== 6) {
      setCodeError('6 haneli kodu eksiksiz girin')
      return
    }

    setLoading(true)
    setCodeError('')
    setApiError('')
    try {
      const res = await authApi.verifyRegister(form.email, code, form.fullName, form.password)
      loginWithTokens(res.data.data)
      toast.success('Kayıt tamamlandı! Hoş geldiniz.')
      navigate('/passenger/dashboard', { replace: true })
    } catch (err) {
      const message = err.response?.data?.message || 'Doğrulama başarısız. Tekrar deneyin.'
      if (message.toLowerCase().includes('hatalı') || message.toLowerCase().includes('süresi')) {
        setCodeError(message)
      } else {
        setApiError(message)
      }
      toast.error(message)
    } finally {
      setLoading(false)
    }
  }

  // ── Kodu yeniden gönder ────────────────────────────────────────────────────
  async function handleResend() {
    if (countdown > 0) return
    setLoading(true)
    setApiError('')
    setCodeError('')
    try {
      await authApi.sendRegisterCode(form.email, form.fullName, form.password)
      toast.success('Yeni kod gönderildi!')
      setCode('')
      startCountdown()
    } catch (err) {
      const message = err.response?.data?.message || 'Kod gönderilemedi.'
      setApiError(message)
      toast.error(message)
    } finally {
      setLoading(false)
    }
  }

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div className="min-h-screen bg-gray-900 flex items-center justify-center px-4 py-8">
      <div className="w-full max-w-md">

        {/* Başlık */}
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-white">Eco-Terminal</h1>
          <p className="text-gray-400 mt-1 text-sm">
            {step === 1 ? 'Yeni hesap oluştur' : 'E-posta doğrulama'}
          </p>
        </div>

        {/* İlerleme göstergesi */}
        <div className="flex items-center gap-2 mb-6">
          <div className={`flex-1 h-1 rounded-full transition-colors ${step >= 1 ? 'bg-eco-green' : 'bg-gray-700'}`} />
          <div className={`flex-1 h-1 rounded-full transition-colors ${step >= 2 ? 'bg-eco-green' : 'bg-gray-700'}`} />
        </div>

        <div className="eco-card">

          {/* ── ADIM 1: Kayıt Formu ──────────────────────────────────────────── */}
          {step === 1 && (
            <>
              <h2 className="text-xl font-semibold text-white mb-6">Kayıt Ol</h2>

              {apiError && (
                <div className="mb-4 px-4 py-3 rounded-lg bg-red-500/10 border border-red-500/30 text-red-400 text-sm">
                  {apiError}
                </div>
              )}

              <form onSubmit={handleSendCode} noValidate className="space-y-4">
                {/* Ad Soyad */}
                <div>
                  <label htmlFor="fullName" className="eco-label">Ad Soyad</label>
                  <input
                    id="fullName" name="fullName" type="text" autoComplete="name"
                    value={form.fullName} onChange={handleFormChange}
                    placeholder="Adınız Soyadınız"
                    className={`eco-input ${errors.fullName ? 'border-red-500 focus:border-red-500 focus:ring-red-500' : ''}`}
                  />
                  {errors.fullName && <p className="mt-1 text-xs text-red-400">{errors.fullName}</p>}
                </div>

                {/* E-posta */}
                <div>
                  <label htmlFor="email" className="eco-label">E-posta</label>
                  <input
                    id="email" name="email" type="email" autoComplete="email"
                    value={form.email} onChange={handleFormChange}
                    placeholder="ornek@mail.com"
                    className={`eco-input ${errors.email ? 'border-red-500 focus:border-red-500 focus:ring-red-500' : ''}`}
                  />
                  {errors.email && <p className="mt-1 text-xs text-red-400">{errors.email}</p>}
                </div>

                {/* Şifre */}
                <div>
                  <label htmlFor="password" className="eco-label">Şifre</label>
                  <input
                    id="password" name="password" type="password" autoComplete="new-password"
                    value={form.password} onChange={handleFormChange}
                    placeholder="••••••••"
                    className={`eco-input ${errors.password ? 'border-red-500 focus:border-red-500 focus:ring-red-500' : ''}`}
                  />
                  {errors.password && <p className="mt-1 text-xs text-red-400">{errors.password}</p>}
                </div>

                {/* Şifre Tekrar */}
                <div>
                  <label htmlFor="confirmPassword" className="eco-label">Şifre Tekrar</label>
                  <input
                    id="confirmPassword" name="confirmPassword" type="password" autoComplete="new-password"
                    value={form.confirmPassword} onChange={handleFormChange}
                    placeholder="••••••••"
                    className={`eco-input ${errors.confirmPassword ? 'border-red-500 focus:border-red-500 focus:ring-red-500' : ''}`}
                  />
                  {errors.confirmPassword && <p className="mt-1 text-xs text-red-400">{errors.confirmPassword}</p>}
                </div>

                <button
                  type="submit" disabled={loading}
                  className="eco-btn-primary w-full flex items-center justify-center gap-2 mt-2"
                >
                  {loading ? (
                    <>
                      <span className="w-4 h-4 border-2 border-gray-900 border-t-transparent rounded-full animate-spin" />
                      Kod gönderiliyor...
                    </>
                  ) : (
                    <>
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                          d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                      </svg>
                      Doğrulama Kodu Gönder
                    </>
                  )}
                </button>
              </form>

              <p className="mt-6 text-center text-sm text-gray-400">
                Zaten hesabın var mı?{' '}
                <Link to="/login" className="text-eco-green hover:text-green-400 font-medium transition-colors">
                  Giriş Yap
                </Link>
              </p>
            </>
          )}

          {/* ── ADIM 2: Kod Doğrulama ────────────────────────────────────────── */}
          {step === 2 && (
            <>
              <div className="flex items-center gap-3 mb-6">
                <button
                  onClick={() => { setStep(1); setCode(''); setCodeError(''); setApiError('') }}
                  className="text-gray-400 hover:text-white transition-colors"
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                  </svg>
                </button>
                <h2 className="text-xl font-semibold text-white">E-posta Doğrulama</h2>
              </div>

              {/* Bilgi kutusu */}
              <div className="mb-5 p-4 rounded-xl bg-eco-green/10 border border-eco-green/30">
                <div className="flex items-start gap-3">
                  <div className="w-8 h-8 rounded-full bg-eco-green/20 flex items-center justify-center flex-shrink-0 mt-0.5">
                    <svg className="w-4 h-4 text-eco-green" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                        d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                    </svg>
                  </div>
                  <div>
                    <p className="text-sm text-eco-green font-medium">Kod gönderildi</p>
                    <p className="text-xs text-gray-400 mt-0.5">
                      <span className="text-white font-medium">{form.email}</span> adresine 6 haneli doğrulama kodu gönderildi.
                      Kod 10 dakika geçerlidir.
                    </p>
                  </div>
                </div>
              </div>

              {apiError && (
                <div className="mb-4 px-4 py-3 rounded-lg bg-red-500/10 border border-red-500/30 text-red-400 text-sm">
                  {apiError}
                </div>
              )}

              <form onSubmit={handleVerify} noValidate className="space-y-4">
                <div>
                  <label htmlFor="code" className="eco-label">Doğrulama Kodu</label>
                  <input
                    id="code" name="code" type="text" inputMode="numeric"
                    autoComplete="one-time-code"
                    value={code}
                    onChange={e => {
                      const v = e.target.value.replace(/\D/g, '').slice(0, 6)
                      setCode(v)
                      if (codeError) setCodeError('')
                      if (apiError) setApiError('')
                    }}
                    placeholder="000000"
                    maxLength={6}
                    className={`eco-input text-center text-2xl font-mono tracking-[0.5em] ${
                      codeError ? 'border-red-500 focus:border-red-500 focus:ring-red-500' : ''
                    }`}
                  />
                  {codeError && <p className="mt-1 text-xs text-red-400">{codeError}</p>}
                </div>

                <button
                  type="submit" disabled={loading || code.length !== 6}
                  className="eco-btn-primary w-full flex items-center justify-center gap-2 mt-2
                             disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {loading ? (
                    <>
                      <span className="w-4 h-4 border-2 border-gray-900 border-t-transparent rounded-full animate-spin" />
                      Doğrulanıyor...
                    </>
                  ) : (
                    <>
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                      </svg>
                      Doğrula ve Kayıt Ol
                    </>
                  )}
                </button>
              </form>

              {/* Yeniden gönder */}
              <div className="mt-5 text-center">
                <p className="text-sm text-gray-400">
                  Kodu almadın mı?{' '}
                  {countdown > 0 ? (
                    <span className="text-gray-600">{countdown} sn sonra tekrar gönder</span>
                  ) : (
                    <button
                      onClick={handleResend}
                      disabled={loading}
                      className="text-eco-green hover:text-green-400 font-medium transition-colors disabled:opacity-50"
                    >
                      Kodu tekrar gönder
                    </button>
                  )}
                </p>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
