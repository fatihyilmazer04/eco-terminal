import React, { useState, useEffect, useRef } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
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

export default function RegisterPage() {
  const { loginWithTokens } = useAuth()
  const navigate = useNavigate()

  const [step, setStep] = useState(1)
  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirmPassword: '' })
  const [errors, setErrors] = useState({})
  const [code, setCode] = useState('')
  const [codeError, setCodeError] = useState('')
  const [loading, setLoading] = useState(false)
  const [apiError, setApiError] = useState('')
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

  return (
    <div className="min-h-screen bg-[#0B1120] flex">
      <LeftPanel />

      {/* Sağ panel */}
      <div className="flex-1 flex items-center justify-center p-10">
        <div className="max-w-[280px] w-full flex flex-col gap-5">

          {/* Başlık */}
          <div>
            <h1 className="text-white text-lg font-normal mb-1">
              {step === 1 ? 'Kayıt Ol' : 'E-posta Doğrulama'}
            </h1>
            <p className="text-[#3A5070] text-xs">
              {step === 1 ? 'Yeni hesap oluşturun' : 'Kodunuzu girin'}
            </p>
          </div>

          {/* İlerleme */}
          <div className="flex items-center gap-1.5">
            <div className={`flex-1 h-0.5 rounded-full transition-colors ${step >= 1 ? 'bg-eco-green' : 'bg-[#1A2540]'}`} />
            <div className={`flex-1 h-0.5 rounded-full transition-colors ${step >= 2 ? 'bg-eco-green' : 'bg-[#1A2540]'}`} />
          </div>

          {/* API Hata */}
          {apiError && (
            <div className="px-3 py-2.5 rounded-md bg-red-500/10 border border-red-500/20 text-red-400 text-xs">
              {apiError}
            </div>
          )}

          {/* ── ADIM 1: Kayıt Formu ─────────────────────────────────────── */}
          {step === 1 && (
            <form onSubmit={handleSendCode} noValidate className="flex flex-col gap-5">

              {/* Ad Soyad */}
              <div>
                <label className="text-[#4A6080] text-[11px] block mb-1.5">Ad Soyad</label>
                <div className={`border-b pb-2.5 flex items-center gap-2 ${errors.fullName ? 'border-red-500/60' : 'border-[#1E3050]'}`}>
                  <svg className="w-4 h-4 text-[#253545] flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8}
                      d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/>
                  </svg>
                  <input
                    name="fullName" type="text" autoComplete="name"
                    value={form.fullName} onChange={handleFormChange}
                    placeholder="Adınız Soyadınız"
                    className="bg-transparent flex-1 text-[#8AA0BC] text-[13px] outline-none placeholder-[#253545]"
                  />
                </div>
                {errors.fullName && <p className="mt-1 text-[11px] text-red-400">{errors.fullName}</p>}
              </div>

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
                    value={form.email} onChange={handleFormChange}
                    placeholder="ornek@mail.com"
                    className="bg-transparent flex-1 text-[#8AA0BC] text-[13px] outline-none placeholder-[#253545]"
                  />
                </div>
                {errors.email && <p className="mt-1 text-[11px] text-red-400">{errors.email}</p>}
              </div>

              {/* Şifre */}
              <div>
                <label className="text-[#4A6080] text-[11px] block mb-1.5">Şifre</label>
                <div className={`border-b pb-2.5 flex items-center gap-2 ${errors.password ? 'border-red-500/60' : 'border-[#1E3050]'}`}>
                  <svg className="w-4 h-4 text-[#253545] flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8}
                      d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"/>
                  </svg>
                  <input
                    name="password" type="password" autoComplete="new-password"
                    value={form.password} onChange={handleFormChange}
                    placeholder="••••••••"
                    className="bg-transparent flex-1 text-[#8AA0BC] text-[13px] outline-none placeholder-[#253545]"
                  />
                </div>
                {errors.password && <p className="mt-1 text-[11px] text-red-400">{errors.password}</p>}
              </div>

              {/* Şifre Tekrar */}
              <div>
                <label className="text-[#4A6080] text-[11px] block mb-1.5">Şifre Tekrar</label>
                <div className={`border-b pb-2.5 flex items-center gap-2 ${errors.confirmPassword ? 'border-red-500/60' : 'border-[#1E3050]'}`}>
                  <svg className="w-4 h-4 text-[#253545] flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8}
                      d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"/>
                  </svg>
                  <input
                    name="confirmPassword" type="password" autoComplete="new-password"
                    value={form.confirmPassword} onChange={handleFormChange}
                    placeholder="••••••••"
                    className="bg-transparent flex-1 text-[#8AA0BC] text-[13px] outline-none placeholder-[#253545]"
                  />
                </div>
                {errors.confirmPassword && <p className="mt-1 text-[11px] text-red-400">{errors.confirmPassword}</p>}
              </div>

              <button
                type="submit" disabled={loading}
                className="w-full bg-eco-green text-[#0B1120] py-3 rounded-md text-[13px] font-medium hover:bg-green-400 transition-colors disabled:opacity-60 flex items-center justify-center gap-2"
              >
                {loading
                  ? <span className="w-4 h-4 border-2 border-[#0B1120] border-t-transparent rounded-full animate-spin" />
                  : 'Doğrulama Kodu Gönder'
                }
              </button>

              <p className="text-center text-[#3A5070] text-xs">
                Zaten hesabın var mı?{' '}
                <Link to="/login" className="text-eco-green hover:text-green-400 transition-colors">
                  Giriş Yap
                </Link>
              </p>
            </form>
          )}

          {/* ── ADIM 2: Kod Doğrulama ───────────────────────────────────── */}
          {step === 2 && (
            <div className="flex flex-col gap-5">
              {/* Geri butonu */}
              <button
                onClick={() => { setStep(1); setCode(''); setCodeError(''); setApiError('') }}
                className="flex items-center gap-1.5 text-[#3A5070] hover:text-white transition-colors text-xs w-fit"
              >
                <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                </svg>
                Geri
              </button>

              {/* Bilgi kutusu */}
              <div className="p-3 rounded-md bg-eco-green/5 border border-eco-green/20">
                <p className="text-xs text-eco-green font-medium mb-0.5">Kod gönderildi</p>
                <p className="text-[11px] text-[#3A5070]">
                  <span className="text-[#8AA0BC]">{form.email}</span> adresine 6 haneli kod gönderildi. 10 dakika geçerlidir.
                </p>
              </div>

              <form onSubmit={handleVerify} noValidate className="flex flex-col gap-5">
                {/* Doğrulama Kodu */}
                <div>
                  <label className="text-[#4A6080] text-[11px] block mb-1.5">Doğrulama Kodu</label>
                  <div className={`border-b pb-2.5 ${codeError ? 'border-red-500/60' : 'border-[#1E3050]'}`}>
                    <input
                      type="text" inputMode="numeric" autoComplete="one-time-code"
                      value={code}
                      onChange={e => {
                        const v = e.target.value.replace(/\D/g, '').slice(0, 6)
                        setCode(v)
                        if (codeError) setCodeError('')
                        if (apiError) setApiError('')
                      }}
                      placeholder="000000" maxLength={6}
                      className="bg-transparent w-full text-[#8AA0BC] text-xl font-mono tracking-[0.4em] outline-none placeholder-[#253545] text-center"
                    />
                  </div>
                  {codeError && <p className="mt-1 text-[11px] text-red-400">{codeError}</p>}
                </div>

                <button
                  type="submit" disabled={loading || code.length !== 6}
                  className="w-full bg-eco-green text-[#0B1120] py-3 rounded-md text-[13px] font-medium hover:bg-green-400 transition-colors disabled:opacity-60 flex items-center justify-center gap-2"
                >
                  {loading
                    ? <span className="w-4 h-4 border-2 border-[#0B1120] border-t-transparent rounded-full animate-spin" />
                    : 'Doğrula ve Kayıt Ol'
                  }
                </button>
              </form>

              <p className="text-center text-[11px] text-[#3A5070]">
                Kodu almadın mı?{' '}
                {countdown > 0 ? (
                  <span className="text-[#1E3050]">{countdown} sn sonra tekrar gönder</span>
                ) : (
                  <button
                    onClick={handleResend} disabled={loading}
                    className="text-eco-green hover:text-green-400 transition-colors disabled:opacity-50"
                  >
                    Kodu tekrar gönder
                  </button>
                )}
              </p>
            </div>
          )}

        </div>
      </div>
    </div>
  )
}
