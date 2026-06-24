import React, { useState, useEffect, useRef } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { authApi } from '../../api/authApi'
import toast from 'react-hot-toast'

const RESEND_COOLDOWN = 60

/* ── Sol panel ───────────────────────────────────────────────────────────── */
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

export default function ForgotPasswordPage() {
  const navigate = useNavigate()

  const [step, setStep]               = useState(1)
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

  function validateStep2() {
    const e = {}
    if (code.length !== 6)                     e.code = '6 haneli kodu eksiksiz girin'
    if (!newPassword || newPassword.length < 6) e.newPassword = 'Şifre en az 6 karakter olmalıdır'
    if (newPassword !== confirmPwd)             e.confirmPwd = 'Şifreler eşleşmiyor'
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

  return (
    <div className="min-h-screen bg-[#0B1120] flex">
      <LeftPanel />

      {/* Sağ panel */}
      <div className="flex-1 flex items-center justify-center p-10">
        <div className="max-w-[280px] w-full flex flex-col gap-6">

          {/* Başlık */}
          <div>
            <h1 className="text-white text-lg font-normal mb-1">Şifremi Unuttum</h1>
            <p className="text-[#3A5070] text-xs">Şifre sıfırlama</p>
          </div>

          {/* İlerleme */}
          <div className="flex items-center gap-1.5">
            {[1, 2, 3].map(s => (
              <div key={s}
                className={`flex-1 h-0.5 rounded-full transition-colors duration-300 ${step >= s ? 'bg-eco-green' : 'bg-[#1A2540]'}`}
              />
            ))}
          </div>

          {/* API Hata */}
          {apiError && (
            <div className="px-3 py-2.5 rounded-md bg-red-500/10 border border-red-500/20 text-red-400 text-xs">
              {apiError}
            </div>
          )}

          {/* ── ADIM 1: E-posta ─────────────────────────────────────────── */}
          {step === 1 && (
            <div className="flex flex-col gap-5">
              <p className="text-[#3A5070] text-xs leading-relaxed">
                Kayıtlı e-posta adresinizi girin. Şifre sıfırlama kodu göndereceğiz.
              </p>

              <form onSubmit={handleSendCode} noValidate className="flex flex-col gap-5">
                <div>
                  <label className="text-[#4A6080] text-[11px] block mb-1.5">E-posta</label>
                  <div className={`border-b pb-2.5 flex items-center gap-2 ${emailError ? 'border-red-500/60' : 'border-[#1E3050]'}`}>
                    <svg className="w-4 h-4 text-[#253545] flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8}
                        d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"/>
                    </svg>
                    <input
                      type="email" autoComplete="email"
                      value={email}
                      onChange={e => { setEmail(e.target.value); setEmailError('') }}
                      placeholder="ornek@mail.com"
                      className="bg-transparent flex-1 text-[#8AA0BC] text-[13px] outline-none placeholder-[#253545]"
                    />
                  </div>
                  {emailError && <p className="mt-1 text-[11px] text-red-400">{emailError}</p>}
                </div>

                <button type="submit" disabled={loading}
                  className="w-full bg-eco-green text-[#0B1120] py-3 rounded-md text-[13px] font-medium hover:bg-green-400 transition-colors disabled:opacity-60 flex items-center justify-center gap-2">
                  {loading
                    ? <span className="w-4 h-4 border-2 border-[#0B1120] border-t-transparent rounded-full animate-spin" />
                    : 'Kod Gönder'
                  }
                </button>
              </form>

              <p className="text-center text-xs">
                <Link to="/login" className="text-[#3A5070] hover:text-eco-green transition-colors">
                  ← Giriş sayfasına dön
                </Link>
              </p>
            </div>
          )}

          {/* ── ADIM 2: Kod + Yeni Şifre ────────────────────────────────── */}
          {step === 2 && (
            <div className="flex flex-col gap-5">
              <button
                onClick={() => { setStep(1); setCode(''); setFieldErrors({}); setApiError('') }}
                className="flex items-center gap-1.5 text-[#3A5070] hover:text-white transition-colors text-xs w-fit"
              >
                <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                </svg>
                Geri
              </button>

              {/* Bilgi */}
              <div className="p-3 rounded-md bg-eco-green/5 border border-eco-green/20">
                <p className="text-xs text-eco-green font-medium mb-0.5">Kod gönderildi</p>
                <p className="text-[11px] text-[#3A5070]">
                  <span className="text-[#8AA0BC]">{email}</span> adresine 6 haneli kod gönderildi. 10 dakika geçerlidir.
                </p>
              </div>

              <form onSubmit={handleReset} noValidate className="flex flex-col gap-5">
                {/* Kod */}
                <div>
                  <label className="text-[#4A6080] text-[11px] block mb-1.5">Doğrulama Kodu</label>
                  <div className={`border-b pb-2.5 ${fieldErrors.code ? 'border-red-500/60' : 'border-[#1E3050]'}`}>
                    <input
                      type="text" inputMode="numeric" autoComplete="one-time-code"
                      value={code}
                      onChange={e => {
                        setCode(e.target.value.replace(/\D/g, '').slice(0, 6))
                        setFieldErrors(prev => ({ ...prev, code: '' }))
                      }}
                      placeholder="000000" maxLength={6}
                      className="bg-transparent w-full text-[#8AA0BC] text-xl font-mono tracking-[0.4em] outline-none placeholder-[#253545] text-center"
                    />
                  </div>
                  {fieldErrors.code && <p className="mt-1 text-[11px] text-red-400">{fieldErrors.code}</p>}
                </div>

                {/* Yeni şifre */}
                <div>
                  <label className="text-[#4A6080] text-[11px] block mb-1.5">Yeni Şifre</label>
                  <div className={`border-b pb-2.5 flex items-center gap-2 ${fieldErrors.newPassword ? 'border-red-500/60' : 'border-[#1E3050]'}`}>
                    <svg className="w-4 h-4 text-[#253545] flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8}
                        d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"/>
                    </svg>
                    <input
                      type="password" autoComplete="new-password"
                      value={newPassword}
                      onChange={e => {
                        setNewPassword(e.target.value)
                        setFieldErrors(prev => ({ ...prev, newPassword: '' }))
                      }}
                      placeholder="••••••••"
                      className="bg-transparent flex-1 text-[#8AA0BC] text-[13px] outline-none placeholder-[#253545]"
                    />
                  </div>
                  {fieldErrors.newPassword && <p className="mt-1 text-[11px] text-red-400">{fieldErrors.newPassword}</p>}
                </div>

                {/* Şifre tekrar */}
                <div>
                  <label className="text-[#4A6080] text-[11px] block mb-1.5">Yeni Şifre Tekrar</label>
                  <div className={`border-b pb-2.5 flex items-center gap-2 ${fieldErrors.confirmPwd ? 'border-red-500/60' : 'border-[#1E3050]'}`}>
                    <svg className="w-4 h-4 text-[#253545] flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8}
                        d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"/>
                    </svg>
                    <input
                      type="password" autoComplete="new-password"
                      value={confirmPwd}
                      onChange={e => {
                        setConfirmPwd(e.target.value)
                        setFieldErrors(prev => ({ ...prev, confirmPwd: '' }))
                      }}
                      placeholder="••••••••"
                      className="bg-transparent flex-1 text-[#8AA0BC] text-[13px] outline-none placeholder-[#253545]"
                    />
                  </div>
                  {fieldErrors.confirmPwd && <p className="mt-1 text-[11px] text-red-400">{fieldErrors.confirmPwd}</p>}
                </div>

                <button type="submit" disabled={loading || code.length !== 6}
                  className="w-full bg-eco-green text-[#0B1120] py-3 rounded-md text-[13px] font-medium hover:bg-green-400 transition-colors disabled:opacity-60 flex items-center justify-center gap-2">
                  {loading
                    ? <span className="w-4 h-4 border-2 border-[#0B1120] border-t-transparent rounded-full animate-spin" />
                    : 'Şifreyi Sıfırla'
                  }
                </button>
              </form>

              <p className="text-center text-[11px] text-[#3A5070]">
                Kodu almadın mı?{' '}
                {countdown > 0 ? (
                  <span className="text-[#1E3050]">{countdown} sn sonra tekrar gönder</span>
                ) : (
                  <button onClick={handleResend} disabled={loading}
                    className="text-eco-green hover:text-green-400 transition-colors disabled:opacity-50">
                    Kodu tekrar gönder
                  </button>
                )}
              </p>
            </div>
          )}

          {/* ── ADIM 3: Başarı ──────────────────────────────────────────── */}
          {step === 3 && (
            <div className="flex flex-col items-center gap-5 py-4 text-center">
              <div className="w-14 h-14 rounded-full bg-eco-green/10 border border-eco-green/30 flex items-center justify-center">
                <svg className="w-7 h-7 text-eco-green" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                </svg>
              </div>
              <div>
                <p className="text-white text-base font-normal mb-1">Şifre Güncellendi</p>
                <p className="text-[#3A5070] text-xs leading-relaxed">
                  Şifreniz başarıyla değiştirildi.<br />
                  Yeni şifrenizle giriş yapabilirsiniz.
                </p>
              </div>
              <button
                onClick={() => navigate('/login', { replace: true })}
                className="w-full bg-eco-green text-[#0B1120] py-3 rounded-md text-[13px] font-medium hover:bg-green-400 transition-colors"
              >
                Giriş Yap
              </button>
            </div>
          )}

        </div>
      </div>
    </div>
  )
}
