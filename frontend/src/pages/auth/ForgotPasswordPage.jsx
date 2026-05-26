import React, { useState, useEffect, useRef } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { authApi } from '../../api/authApi'
import toast from 'react-hot-toast'

const RESEND_COOLDOWN = 60

export default function ForgotPasswordPage() {
  const navigate = useNavigate()

  const [step, setStep]               = useState(1)   // 1: email, 2: kod+yeni şifre, 3: başarı
  const [email, setEmail]             = useState('')
  const [emailError, setEmailError]   = useState('')
  const [code, setCode]               = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPwd, setConfirmPwd]   = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [loading, setLoading]         = useState(false)
  const [apiError, setApiError]       = useState('')
  const [countdown, setCountdown]     = useState(0)
  const timerRef                      = useRef(null)

  useEffect(() => () => clearInterval(timerRef.current), [])

  function startCountdown() {
    setCountdown(RESEND_COOLDOWN)
    timerRef.current = setInterval(() => {
      setCountdown(prev => {
        if (prev <= 1) { clearInterval(timerRef.current); return 0 }
        return prev - 1
      })
    }, 1000)
  }

  // ── ADIM 1: Email gönder ──────────────────────────────────────────────────
  async function handleSendCode(e) {
    e.preventDefault()
    if (!/\S+@\S+\.\S+/.test(email)) {
      setEmailError('Geçerli bir e-posta girin')
      return
    }
    setEmailError('')
    setApiError('')
    setLoading(true)
    try {
      await authApi.forgotPassword(email)
      toast.success('Doğrulama kodu gönderildi!')
      setStep(2)
      startCountdown()
    } catch (err) {
      const msg = err.response?.data?.message || 'Bir hata oluştu. Tekrar deneyin.'
      setApiError(msg)
      toast.error(msg)
    } finally {
      setLoading(false)
    }
  }

  // ── ADIM 2: Kodu doğrula + şifreyi güncelle ──────────────────────────────
  function validateStep2() {
    const e = {}
    if (code.length !== 6)                    e.code = '6 haneli kodu eksiksiz girin'
    if (!newPassword || newPassword.length < 6) e.newPassword = 'Şifre en az 6 karakter olmalıdır'
    if (newPassword !== confirmPwd)            e.confirmPwd = 'Şifreler eşleşmiyor'
    return e
  }

  async function handleReset(e) {
    e.preventDefault()
    const errs = validateStep2()
    if (Object.keys(errs).length > 0) { setFieldErrors(errs); return }
    setFieldErrors({})
    setApiError('')
    setLoading(true)
    try {
      await authApi.resetPassword({ email, code, newPassword })
      setStep(3)
      toast.success('Şifreniz güncellendi!')
    } catch (err) {
      const msg = err.response?.data?.message || 'Doğrulama başarısız. Tekrar deneyin.'
      if (msg.toLowerCase().includes('hatalı') || msg.toLowerCase().includes('süresi')) {
        setFieldErrors(prev => ({ ...prev, code: msg }))
      } else {
        setApiError(msg)
      }
      toast.error(msg)
    } finally {
      setLoading(false)
    }
  }

  // ── Kodu yeniden gönder ───────────────────────────────────────────────────
  async function handleResend() {
    if (countdown > 0 || loading) return
    setLoading(true)
    setApiError('')
    setFieldErrors({})
    try {
      await authApi.forgotPassword(email)
      toast.success('Yeni kod gönderildi!')
      setCode('')
      startCountdown()
    } catch {
      toast.error('Kod gönderilemedi.')
    } finally {
      setLoading(false)
    }
  }

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div className="min-h-screen bg-gray-900 flex items-center justify-center px-4 py-8">
      <div className="w-full max-w-md">

        {/* Başlık */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl
                          bg-eco-green/10 border border-eco-green/30 mb-4">
            <svg className="w-8 h-8 text-eco-green" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z" />
            </svg>
          </div>
          <h1 className="text-3xl font-bold text-white">Eco-Terminal</h1>
          <p className="text-gray-400 mt-1 text-sm">Şifre sıfırlama</p>
        </div>

        {/* İlerleme göstergesi */}
        <div className="flex items-center gap-2 mb-6">
          {[1, 2, 3].map(s => (
            <div key={s}
              className={`flex-1 h-1 rounded-full transition-colors duration-300
                ${step >= s ? 'bg-eco-green' : 'bg-gray-700'}`}
            />
          ))}
        </div>

        <div className="eco-card">

          {/* ── ADIM 1: Email ─────────────────────────────────────────────── */}
          {step === 1 && (
            <>
              <div className="flex items-center gap-3 mb-6">
                <Link to="/login" className="text-gray-400 hover:text-white transition-colors">
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                  </svg>
                </Link>
                <h2 className="text-xl font-semibold text-white">Şifremi Unuttum</h2>
              </div>

              <p className="text-gray-400 text-sm mb-5 leading-relaxed">
                Kayıtlı e-posta adresinizi girin. Şifre sıfırlama kodu göndereceğiz.
              </p>

              {apiError && (
                <div className="mb-4 px-4 py-3 rounded-lg bg-red-500/10 border border-red-500/30 text-red-400 text-sm">
                  {apiError}
                </div>
              )}

              <form onSubmit={handleSendCode} noValidate className="space-y-4">
                <div>
                  <label htmlFor="email" className="eco-label">E-posta</label>
                  <input
                    id="email" type="email" autoComplete="email"
                    value={email}
                    onChange={e => { setEmail(e.target.value); setEmailError('') }}
                    placeholder="ornek@mail.com"
                    className={`eco-input ${emailError ? 'border-red-500 focus:border-red-500 focus:ring-red-500' : ''}`}
                  />
                  {emailError && <p className="mt-1 text-xs text-red-400">{emailError}</p>}
                </div>

                <button type="submit" disabled={loading}
                  className="eco-btn-primary w-full flex items-center justify-center gap-2 mt-2">
                  {loading ? (
                    <>
                      <span className="w-4 h-4 border-2 border-gray-900 border-t-transparent rounded-full animate-spin" />
                      Gönderiliyor...
                    </>
                  ) : (
                    <>
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                          d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                      </svg>
                      Kod Gönder
                    </>
                  )}
                </button>
              </form>
            </>
          )}

          {/* ── ADIM 2: Kod + Yeni Şifre ──────────────────────────────────── */}
          {step === 2 && (
            <>
              <div className="flex items-center gap-3 mb-5">
                <button onClick={() => { setStep(1); setCode(''); setFieldErrors({}); setApiError('') }}
                  className="text-gray-400 hover:text-white transition-colors">
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                  </svg>
                </button>
                <h2 className="text-xl font-semibold text-white">Kodu Girin</h2>
              </div>

              {/* Bilgi */}
              <div className="mb-5 p-4 rounded-xl bg-eco-green/10 border border-eco-green/30">
                <div className="flex items-start gap-3">
                  <div className="w-8 h-8 rounded-full bg-eco-green/20 flex items-center justify-center flex-shrink-0 mt-0.5">
                    <svg className="w-4 h-4 text-eco-green" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                        d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2 2v10a2 2 0 002 2z" />
                    </svg>
                  </div>
                  <div>
                    <p className="text-sm text-eco-green font-medium">Kod gönderildi</p>
                    <p className="text-xs text-gray-400 mt-0.5">
                      <span className="text-white font-medium">{email}</span> adresine
                      6 haneli kod gönderildi. Kod 10 dakika geçerlidir.
                    </p>
                  </div>
                </div>
              </div>

              {apiError && (
                <div className="mb-4 px-4 py-3 rounded-lg bg-red-500/10 border border-red-500/30 text-red-400 text-sm">
                  {apiError}
                </div>
              )}

              <form onSubmit={handleReset} noValidate className="space-y-4">
                {/* Kod */}
                <div>
                  <label htmlFor="code" className="eco-label">Doğrulama Kodu</label>
                  <input
                    id="code" type="text" inputMode="numeric" autoComplete="one-time-code"
                    value={code}
                    onChange={e => {
                      setCode(e.target.value.replace(/\D/g, '').slice(0, 6))
                      setFieldErrors(prev => ({ ...prev, code: '' }))
                    }}
                    placeholder="000000" maxLength={6}
                    className={`eco-input text-center text-2xl font-mono tracking-[0.5em]
                      ${fieldErrors.code ? 'border-red-500 focus:border-red-500 focus:ring-red-500' : ''}`}
                  />
                  {fieldErrors.code && <p className="mt-1 text-xs text-red-400">{fieldErrors.code}</p>}
                </div>

                {/* Yeni şifre */}
                <div>
                  <label htmlFor="newPassword" className="eco-label">Yeni Şifre</label>
                  <input
                    id="newPassword" type="password" autoComplete="new-password"
                    value={newPassword}
                    onChange={e => {
                      setNewPassword(e.target.value)
                      setFieldErrors(prev => ({ ...prev, newPassword: '' }))
                    }}
                    placeholder="••••••••"
                    className={`eco-input ${fieldErrors.newPassword ? 'border-red-500 focus:border-red-500 focus:ring-red-500' : ''}`}
                  />
                  {fieldErrors.newPassword && <p className="mt-1 text-xs text-red-400">{fieldErrors.newPassword}</p>}
                </div>

                {/* Yeni şifre tekrar */}
                <div>
                  <label htmlFor="confirmPwd" className="eco-label">Yeni Şifre Tekrar</label>
                  <input
                    id="confirmPwd" type="password" autoComplete="new-password"
                    value={confirmPwd}
                    onChange={e => {
                      setConfirmPwd(e.target.value)
                      setFieldErrors(prev => ({ ...prev, confirmPwd: '' }))
                    }}
                    placeholder="••••••••"
                    className={`eco-input ${fieldErrors.confirmPwd ? 'border-red-500 focus:border-red-500 focus:ring-red-500' : ''}`}
                  />
                  {fieldErrors.confirmPwd && <p className="mt-1 text-xs text-red-400">{fieldErrors.confirmPwd}</p>}
                </div>

                <button type="submit" disabled={loading || code.length !== 6}
                  className="eco-btn-primary w-full flex items-center justify-center gap-2 mt-2
                             disabled:opacity-50 disabled:cursor-not-allowed">
                  {loading ? (
                    <>
                      <span className="w-4 h-4 border-2 border-gray-900 border-t-transparent rounded-full animate-spin" />
                      Güncelleniyor...
                    </>
                  ) : (
                    <>
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                      </svg>
                      Şifreyi Sıfırla
                    </>
                  )}
                </button>
              </form>

              {/* Yeniden gönder */}
              <div className="mt-4 text-center">
                <p className="text-sm text-gray-400">
                  Kodu almadın mı?{' '}
                  {countdown > 0 ? (
                    <span className="text-gray-600">{countdown} sn sonra tekrar gönder</span>
                  ) : (
                    <button onClick={handleResend} disabled={loading}
                      className="text-eco-green hover:text-green-400 font-medium transition-colors disabled:opacity-50">
                      Kodu tekrar gönder
                    </button>
                  )}
                </p>
              </div>
            </>
          )}

          {/* ── ADIM 3: Başarı ────────────────────────────────────────────── */}
          {step === 3 && (
            <div className="text-center py-4">
              <div className="w-16 h-16 rounded-full bg-eco-green/20 border border-eco-green/40
                              flex items-center justify-center mx-auto mb-5">
                <svg className="w-8 h-8 text-eco-green" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                </svg>
              </div>
              <h2 className="text-xl font-semibold text-white mb-2">Şifre Güncellendi!</h2>
              <p className="text-gray-400 text-sm mb-6 leading-relaxed">
                Şifreniz başarıyla değiştirildi.<br />
                Yeni şifrenizle giriş yapabilirsiniz.
              </p>
              <button
                onClick={() => navigate('/login', { replace: true })}
                className="eco-btn-primary w-full flex items-center justify-center gap-2"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                    d="M11 16l-4-4m0 0l4-4m-4 4h14m-5 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h7a3 3 0 013 3v1" />
                </svg>
                Giriş Yap
              </button>
            </div>
          )}

        </div>
      </div>
    </div>
  )
}
