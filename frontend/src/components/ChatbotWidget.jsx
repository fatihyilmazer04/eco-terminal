import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'
import { sendMessage, intentLabel, intentColor } from '../api/chatbot'

/**
 * Floating chatbot widget — mount edildiği layout (PassengerPage) sayesinde
 * yalnızca authenticated yolcu sayfalarında görünür.
 *
 * Features (Step 5.3):
 *   - Mesaj geçmişi + timestamp
 *   - Intent badge (Rota / Uçuş / Yoğunluk / Sadakat)
 *   - Adım adım rota kartı (intent=route_request + routeSteps)
 *   - "Haritada Göster" → /passenger/heatmap + location state
 *   - Sources debug (yalnızca DEV modunda)
 *   - Yazıyor… animasyonu
 */
export default function ChatbotWidget() {
  const navigate  = useNavigate()
  const [isOpen, setIsOpen]   = useState(false)
  const [messages, setMessages] = useState([
    {
      role: 'assistant',
      content: 'Merhaba! ✈ Eco-Terminal asistanıyım. Rota, uçuş, yoğunluk veya genel bilgi sorularınıza yardımcı olabilirim.',
      intent: 'general_info',
      confidence: 1.0,
      routeSteps: [],
      timestamp: new Date(),
    },
  ])
  const [input, setInput]     = useState('')
  const [loading, setLoading] = useState(false)
  const scrollRef = useRef(null)

  // Auto-scroll on new message or open
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight
    }
  }, [messages, isOpen, loading])

  async function handleSend() {
    const text = input.trim()
    if (!text || loading) return

    const userMsg = { role: 'user', content: text, timestamp: new Date() }
    setMessages(prev => [...prev, userMsg])
    setInput('')
    setLoading(true)

    try {
      // İlk denemede hata alınırsa 2 saniye bekleyip bir kez daha dene
      let res
      try {
        res = await sendMessage(text)
      } catch (firstErr) {
        console.warn('chatbot_first_attempt_failed, retrying...', firstErr?.response?.status)
        await new Promise(r => setTimeout(r, 2000))
        res = await sendMessage(text)
      }
      setMessages(prev => [
        ...prev,
        {
          role:        'assistant',
          content:     res.reply,
          intent:      res.intent,
          confidence:  res.confidence,
          routeSteps:  res.routeSteps,
          sourcesUsed: res.sourcesUsed,
          provider:    res.provider,
          hasRoute:    res.hasRoute,
          timestamp:   new Date(),
        },
      ])
    } catch (err) {
      console.error('chatbot_error', err)
      toast.error('Asistan şu an yanıt veremedi.')
      setMessages(prev => [
        ...prev,
        {
          role:      'assistant',
          content:   'Üzgünüm, şu an bağlantı sorunu yaşıyorum. Lütfen tekrar deneyin.',
          intent:    'error',
          timestamp: new Date(),
        },
      ])
    } finally {
      setLoading(false)
    }
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  function handleShowOnMap(routeSteps) {
    navigate('/passenger/heatmap', { state: { routeFromChatbot: routeSteps } })
    setIsOpen(false)
  }

  // ── Kapalı durum: yüzen buton ─────────────────────────────────────────────
  if (!isOpen) {
    return (
      <button
        onClick={() => setIsOpen(true)}
        className="fixed bottom-6 right-6 z-50 flex items-center justify-center w-14 h-14
                   bg-eco-green hover:bg-green-400 text-gray-900 rounded-full shadow-lg
                   shadow-eco-green/30 transition-all hover:scale-105 focus:outline-none
                   focus:ring-2 focus:ring-eco-green/50 focus:ring-offset-2 focus:ring-offset-gray-900"
        aria-label="Chatbot'u aç"
      >
        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
            d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
        </svg>
      </button>
    )
  }

  // ── Açık durum: chat paneli ───────────────────────────────────────────────
  return (
    <div
      className="fixed bottom-6 right-6 z-50 w-96 max-w-[calc(100vw-1.5rem)]
                 bg-gray-800 border border-gray-700 rounded-2xl shadow-2xl
                 flex flex-col overflow-hidden"
      style={{ height: 'min(600px, calc(100dvh - 5rem))' }}
    >
      {/* Başlık */}
      <div className="flex items-center justify-between px-4 py-3 bg-gray-800 border-b border-gray-700 flex-shrink-0">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-full bg-eco-green/20 border border-eco-green/40
                          flex items-center justify-center text-base">
            🤖
          </div>
          <div>
            <p className="text-white text-sm font-semibold leading-tight">Eco Asistan</p>
          </div>
        </div>
        <button
          onClick={() => setIsOpen(false)}
          className="text-gray-500 hover:text-gray-300 transition-colors w-8 h-8
                     flex items-center justify-center rounded-full hover:bg-gray-700"
          aria-label="Kapat"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      {/* Mesajlar */}
      <div
        ref={scrollRef}
        className="flex-1 overflow-y-auto px-3 py-3 space-y-3 min-h-0 bg-gray-900/30"
      >
        {messages.map((msg, idx) => (
          <MessageBubble key={idx} msg={msg} onShowOnMap={handleShowOnMap} />
        ))}
        {loading && <TypingIndicator />}
      </div>

      {/* Input alanı */}
      <div className="border-t border-gray-700 p-3 bg-gray-800 flex-shrink-0">
        <div className="flex gap-2">
          <input
            type="text"
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Bir şey sorun..."
            disabled={loading}
            maxLength={500}
            className="flex-1 bg-gray-900 border border-gray-600 rounded-xl px-3 py-2
                       text-white text-sm placeholder-gray-500
                       focus:border-eco-green/50 focus:outline-none focus:ring-1 focus:ring-eco-green/30
                       disabled:opacity-50 transition-colors"
          />
          <button
            onClick={handleSend}
            disabled={loading || !input.trim()}
            className="w-9 h-9 rounded-xl bg-eco-green flex items-center justify-center
                       text-gray-900 hover:bg-green-400 transition-colors
                       disabled:opacity-40 disabled:cursor-not-allowed flex-shrink-0"
            aria-label="Gönder"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5}
                d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  )
}

// ── MessageBubble ─────────────────────────────────────────────────────────────

function MessageBubble({ msg, onShowOnMap }) {
  const isUser = msg.role === 'user'
  const time   = formatTime(msg.timestamp)

  if (isUser) {
    return (
      <div className="flex justify-end">
        <div className="max-w-[82%]">
          <div className="bg-eco-green text-gray-900 font-medium px-3 py-2
                          rounded-2xl rounded-tr-md text-sm leading-relaxed">
            {msg.content}
          </div>
          <p className="text-[10px] text-gray-600 mt-1 text-right">{time}</p>
        </div>
      </div>
    )
  }

  // Asistan mesajı
  return (
    <div className="flex justify-start">
      <div className="flex gap-2 max-w-[90%] w-full">
        {/* Avatar */}
        <div className="w-6 h-6 rounded-full bg-eco-green/20 border border-eco-green/40
                        flex items-center justify-center flex-shrink-0 mt-0.5 text-xs">
          🤖
        </div>

        <div className="flex-1 min-w-0">
          {/* Mesaj balonu */}
          <div className="bg-gray-700 text-gray-100 px-3 py-2
                          rounded-2xl rounded-tl-md text-sm leading-relaxed">
            {msg.content.split('\n').map((line, i, arr) => (
              <span key={i}>
                {line}
                {i < arr.length - 1 && <br />}
              </span>
            ))}
          </div>

          {/* Intent badge + güven skoru */}
          {msg.intent && msg.intent !== 'unknown' && msg.intent !== 'general_info' && msg.intent !== 'error' && (
            <div className="mt-1.5 flex items-center gap-1.5">
              <span className={`text-[10px] px-2 py-0.5 rounded-full font-medium ${intentColor(msg.intent)}`}>
                {intentLabel(msg.intent)}
              </span>
              {typeof msg.confidence === 'number' && msg.confidence > 0 && (
                <span className="text-[10px] text-gray-500">
                  %{Math.round(msg.confidence * 100)} eminlik
                </span>
              )}
            </div>
          )}

          {/* Rota kartı */}
          {msg.hasRoute && msg.routeSteps && msg.routeSteps.length > 0 && (
            <RouteCard steps={msg.routeSteps} onShowOnMap={onShowOnMap} />
          )}

          {/* Sources (yalnızca DEV modunda) */}
          {import.meta.env.DEV && msg.sourcesUsed && msg.sourcesUsed.length > 0 && (
            <details className="mt-1.5 text-[10px] text-gray-600">
              <summary className="cursor-pointer hover:text-gray-400 transition-colors">
                🔍 Kaynaklar ({msg.sourcesUsed.length})
              </summary>
              <ul className="mt-1 pl-2 space-y-0.5">
                {msg.sourcesUsed.map((s, i) => (
                  <li key={i} className="font-mono text-[9px] text-gray-500">• {s}</li>
                ))}
              </ul>
            </details>
          )}

          <p className="text-[10px] text-gray-600 mt-1">{time}</p>
        </div>
      </div>
    </div>
  )
}

// ── RouteCard ─────────────────────────────────────────────────────────────────

function RouteCard({ steps, onShowOnMap }) {
  const totalMinutes = steps.reduce((sum, s) => sum + (s.estimatedWalkMinutes || 0), 0)

  return (
    <div className="mt-2 bg-gray-800 border border-eco-blue/30 rounded-xl overflow-hidden">
      {/* Kart başlığı */}
      <div className="px-3 py-2 bg-eco-blue/10 border-b border-eco-blue/20
                      flex items-center justify-between">
        <div className="flex items-center gap-1.5 text-sm font-semibold text-gray-200">
          <span>📍</span>
          <span>Önerilen Rota</span>
        </div>
        {totalMinutes > 0 && (
          <span className="text-[10px] font-medium text-eco-green bg-eco-green/10
                           border border-eco-green/20 px-2 py-0.5 rounded-full">
            ~{totalMinutes} dk
          </span>
        )}
      </div>

      {/* Adımlar */}
      <ol className="px-3 py-2 space-y-2">
        {steps.map((step, idx) => (
          <li key={idx} className="flex items-start gap-2 text-sm">
            <span className="flex-shrink-0 w-5 h-5 bg-gray-700 border border-eco-blue/40
                             text-eco-blue rounded-full flex items-center justify-center
                             text-[10px] font-bold mt-0.5">
              {step.stepNumber || idx + 1}
            </span>
            <div className="flex-1 min-w-0">
              <p className="font-medium text-gray-200 leading-tight">{step.zoneName}</p>
              {step.instruction && (
                <p className="text-[11px] text-gray-500 mt-0.5 leading-snug">
                  {step.instruction}
                </p>
              )}
            </div>
            {step.estimatedWalkMinutes > 0 && (
              <span className="flex-shrink-0 text-[10px] text-gray-500 mt-0.5 whitespace-nowrap">
                ~{step.estimatedWalkMinutes} dk
              </span>
            )}
          </li>
        ))}
      </ol>

      {/* Haritada Göster */}
      <button
        onClick={() => onShowOnMap(steps)}
        className="w-full px-3 py-2 bg-eco-green/10 hover:bg-eco-green/20
                   border-t border-eco-green/20 text-eco-green text-xs font-medium
                   flex items-center justify-center gap-1.5 transition-colors"
      >
        <span>🗺️</span>
        <span>Haritada Göster</span>
      </button>
    </div>
  )
}

// ── TypingIndicator ───────────────────────────────────────────────────────────

function TypingIndicator() {
  return (
    <div className="flex gap-2 justify-start">
      <div className="w-6 h-6 rounded-full bg-eco-green/20 border border-eco-green/40
                      flex items-center justify-center flex-shrink-0 text-xs">
        🤖
      </div>
      <div className="bg-gray-700 rounded-2xl rounded-tl-none px-4 py-3 flex gap-1 items-center">
        {[0, 1, 2].map(i => (
          <span
            key={i}
            className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce"
            style={{ animationDelay: `${i * 150}ms` }}
          />
        ))}
      </div>
    </div>
  )
}

// ── Yardımcı ─────────────────────────────────────────────────────────────────

function formatTime(date) {
  try {
    const d = date instanceof Date ? date : new Date(date)
    return d.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' })
  } catch {
    return ''
  }
}
