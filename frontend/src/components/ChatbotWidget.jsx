import React, { useState, useRef, useEffect, useCallback } from 'react'
import { askChatbot } from '../api/chatbot'

const QUICK_QUESTIONS = [
  'En sakin lounge nerede?',
  'Uçuş kapım nerede?',
  'Genel yoğunluk durumu',
  'Nereye gideyim?',
]

const BOT_WELCOME = 'Merhaba! ✈ Terminal asistanınızım. Yoğunluk, lounge önerisi veya uçuş bilgisi konularında yardımcı olabilirim.'

function Message({ msg }) {
  const isBot = msg.role === 'bot'

  return (
    <div className={`flex gap-2 ${isBot ? 'justify-start' : 'justify-end'}`}>
      {isBot && (
        <div className="w-7 h-7 rounded-full bg-eco-green/20 border border-eco-green/40
                        flex items-center justify-center flex-shrink-0 mt-0.5">
          <span className="text-eco-green text-xs">🤖</span>
        </div>
      )}
      <div className={`max-w-[78%] rounded-2xl px-3 py-2 text-sm leading-relaxed
        ${isBot
          ? 'bg-gray-700 text-gray-100 rounded-tl-none'
          : 'bg-eco-green text-gray-900 font-medium rounded-tr-none'
        }`}
      >
        {/* Satır kırılmalarını koruyarak göster */}
        {msg.text.split('\n').map((line, i) => (
          <span key={i}>
            {line}
            {i < msg.text.split('\n').length - 1 && <br />}
          </span>
        ))}

        {/* Önerilen bölge chip'leri */}
        {isBot && msg.suggestedZones?.length > 0 && (
          <div className="flex flex-wrap gap-1 mt-2">
            {msg.suggestedZones.map(zone => (
              <span key={zone}
                    className="inline-block px-2 py-0.5 rounded-full text-[10px] font-semibold
                               bg-eco-green/20 border border-eco-green/40 text-eco-green">
                📍 {zone}
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function TypingIndicator() {
  return (
    <div className="flex gap-2 justify-start">
      <div className="w-7 h-7 rounded-full bg-eco-green/20 border border-eco-green/40
                      flex items-center justify-center flex-shrink-0">
        <span className="text-eco-green text-xs">🤖</span>
      </div>
      <div className="bg-gray-700 rounded-2xl rounded-tl-none px-4 py-3 flex gap-1 items-center">
        {[0, 1, 2].map(i => (
          <span key={i}
                className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce"
                style={{ animationDelay: `${i * 150}ms` }} />
        ))}
      </div>
    </div>
  )
}

export default function ChatbotWidget() {
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState([
    { id: 0, role: 'bot', text: BOT_WELCOME, suggestedZones: [] }
  ])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [showQuick, setShowQuick] = useState(true)
  const [unread, setUnread] = useState(0)
  const bottomRef = useRef(null)
  const inputRef = useRef(null)
  const msgIdRef = useRef(1)
  const isMounted = useRef(true)

  useEffect(() => {
    isMounted.current = true
    return () => { isMounted.current = false }
  }, [])

  // Yeni mesaj geldiğinde aşağı kaydır
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

  // Chat açıldığında input'a fokus
  useEffect(() => {
    if (open) {
      setUnread(0)
      setTimeout(() => inputRef.current?.focus(), 100)
    }
  }, [open])

  const sendMessage = useCallback(async (text) => {
    const trimmed = (text ?? input).trim()
    if (!trimmed || loading) return

    setInput('')
    setShowQuick(false)

    const userMsg = { id: msgIdRef.current++, role: 'user', text: trimmed }
    setMessages(prev => [...prev, userMsg])
    setLoading(true)

    try {
      const res = await askChatbot(trimmed)
      if (!isMounted.current) return
      const botMsg = {
        id: msgIdRef.current++,
        role: 'bot',
        text: res.reply ?? 'Yanıt alınamadı.',
        suggestedZones: res.suggestedZones ?? [],
      }
      setMessages(prev => [...prev, botMsg])
      if (!open) setUnread(u => u + 1)
    } catch {
      if (!isMounted.current) return
      setMessages(prev => [...prev, {
        id: msgIdRef.current++,
        role: 'bot',
        text: 'Bağlantı hatası oluştu. Lütfen tekrar deneyin.',
        suggestedZones: [],
      }])
    } finally {
      if (isMounted.current) setLoading(false)
    }
  }, [input, loading, open])

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  return (
    <>
      {/* ── Chat Penceresi ──────────────────────────────────────────────────── */}
      {open && (
        <div
          className="fixed bottom-20 right-4 z-50 w-80 sm:w-96
                     bg-gray-800 border border-gray-700 rounded-2xl shadow-2xl
                     flex flex-col overflow-hidden"
          style={{ maxHeight: 'min(520px, calc(100dvh - 90px))' }}
        >
          {/* Başlık */}
          <div className="flex items-center gap-2.5 px-4 py-3 border-b border-gray-700
                          bg-gray-800/90 backdrop-blur-sm">
            <div className="w-8 h-8 rounded-full bg-eco-green/20 border border-eco-green/40
                            flex items-center justify-center flex-shrink-0">
              <span className="text-eco-green text-sm">🤖</span>
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-white text-sm font-semibold leading-tight">Terminal Asistanı</p>
              <p className="text-eco-green text-xs">Çevrimiçi · Gerçek zamanlı veri</p>
            </div>
            <button
              onClick={() => setOpen(false)}
              className="text-gray-500 hover:text-gray-300 transition-colors p-1"
              aria-label="Kapat"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          {/* Mesaj Alanı */}
          <div className="flex-1 overflow-y-auto px-3 py-3 space-y-3 min-h-0">
            {messages.map(msg => (
              <Message key={msg.id} msg={msg} />
            ))}
            {loading && <TypingIndicator />}
            <div ref={bottomRef} />
          </div>

          {/* Hızlı Sorular */}
          {showQuick && !loading && (
            <div className="px-3 pb-2 flex flex-wrap gap-1.5">
              {QUICK_QUESTIONS.map(q => (
                <button
                  key={q}
                  onClick={() => sendMessage(q)}
                  className="text-[11px] px-2.5 py-1 rounded-full border border-eco-green/30
                             text-eco-green bg-eco-green/5 hover:bg-eco-green/15 transition-colors"
                >
                  {q}
                </button>
              ))}
            </div>
          )}

          {/* Input */}
          <div className="flex gap-2 px-3 py-3 border-t border-gray-700">
            <input
              ref={inputRef}
              type="text"
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Bir şey sorun..."
              maxLength={500}
              disabled={loading}
              className="flex-1 bg-gray-900 border border-gray-600 rounded-xl px-3 py-2
                         text-white text-sm placeholder-gray-500
                         focus:border-eco-green/50 focus:outline-none
                         disabled:opacity-50 transition-colors"
            />
            <button
              onClick={() => sendMessage()}
              disabled={!input.trim() || loading}
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
      )}

      {/* ── Yüzen Buton ────────────────────────────────────────────────────── */}
      <button
        onClick={() => setOpen(prev => !prev)}
        className="fixed bottom-4 right-4 z-50 w-13 h-13 rounded-full
                   bg-eco-green shadow-lg shadow-eco-green/30
                   flex items-center justify-center
                   hover:bg-green-400 hover:scale-105 transition-all duration-200
                   focus:outline-none focus:ring-2 focus:ring-eco-green/50 focus:ring-offset-2
                   focus:ring-offset-gray-900"
        aria-label={open ? 'Chatbot kapat' : 'Chatbot aç'}
        style={{ width: '3.25rem', height: '3.25rem' }}
      >
        {open ? (
          <svg className="w-5 h-5 text-gray-900" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M6 18L18 6M6 6l12 12" />
          </svg>
        ) : (
          <svg className="w-5 h-5 text-gray-900" fill="currentColor" viewBox="0 0 24 24">
            <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 12H6v-2h12v2zm0-3H6V9h12v2zm0-3H6V6h12v2z"/>
          </svg>
        )}
        {/* Okunmamış badge */}
        {!open && unread > 0 && (
          <span className="absolute -top-1 -right-1 w-5 h-5 rounded-full bg-red-500
                           flex items-center justify-center text-white text-[10px] font-bold">
            {unread}
          </span>
        )}
      </button>
    </>
  )
}
