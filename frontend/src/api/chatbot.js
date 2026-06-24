import axiosInstance from './axiosInstance'

/**
 * Chatbot API client.
 *
 * Backend response shape (Step 5+):
 *   {
 *     reply: string,
 *     intent: string,           // route_request | flight_info | crowd_query | loyalty_query | general_info | unknown
 *     confidence: number,       // 0.0 – 1.0
 *     routeSteps: Array<{       // null for non-route queries
 *       stepNumber: number,
 *       zoneName: string,
 *       instruction: string,
 *       estimatedWalkMinutes: number
 *     }> | null,
 *     sourcesUsed: string[],    // ["classifier:hybrid", "backend:dijkstra", ...]
 *     provider: string          // "llm-service" | "cloud" | "local"
 *   }
 */

// ── Core API calls ────────────────────────────────────────────────────────────

/**
 * Send a message to the chatbot and get a rich normalized response.
 *
 * @param {string} message - User's natural language query
 * @param {object} [options]
 * @param {string} [options.sessionId]  - Conversation session ID
 * @param {string} [options.locale]     - User locale (default: tr-TR)
 * @returns {Promise<NormalizedChatbotResponse>}
 */
export async function sendMessage(message, options = {}) {
  const payload = {
    message,
    sessionId: options.sessionId || null,
    locale: options.locale || 'tr-TR',
  }
  const response = await axiosInstance.post('/api/chatbot/ask', payload)
  return normalizeResponse(response.data.data)
}

/**
 * Clear the user's chat history (server-side context reset).
 * Silently succeeds even if the endpoint doesn't exist yet.
 */
export async function clearHistory() {
  try {
    await axiosInstance.post('/api/chatbot/clear')
  } catch {
    // endpoint henüz yok olabilir — sessizce geç
  }
}

/**
 * Mevcut chatbot sağlayıcılarını ve durumlarını döner.
 * @returns {Promise<Array<{name: string, displayName: string, available: boolean}>>}
 */
export async function getProviders() {
  const res = await axiosInstance.get('/api/chatbot/providers')
  return res.data.data
}

// ── Backward-compatible alias ─────────────────────────────────────────────────

/**
 * @deprecated Yeni kod için sendMessage() kullan.
 *
 * ChatbotWidget.jsx gibi mevcut çağrıcıları bozmamak için korunmaktadır.
 * provider parametresi backend'e iletilir (override için).
 *
 * @param {string} message
 * @param {string} [provider] - "cloud" | "local" | "llm-service"
 * @returns {Promise<NormalizedChatbotResponse>}
 */
export async function askChatbot(message, provider) {
  const payload = { message }
  if (provider) payload.provider = provider
  const response = await axiosInstance.post('/api/chatbot/ask', payload)
  return normalizeResponse(response.data.data)
}

// ── Normalization ─────────────────────────────────────────────────────────────

/**
 * Normalize raw backend response into a stable frontend shape.
 * Handles both legacy responses (just `reply`) and new rich responses.
 *
 * @param {object|null} data - raw response.data.data from backend
 * @returns {NormalizedChatbotResponse}
 */
function normalizeResponse(data) {
  if (!data) {
    return {
      reply: '',
      intent: 'unknown',
      confidence: 0,
      routeSteps: [],
      sourcesUsed: [],
      provider: 'unknown',
      suggestedZones: [],
      hasRoute: false,
    }
  }

  const routeSteps = Array.isArray(data.routeSteps) ? data.routeSteps : []

  return {
    reply: data.reply || '',
    intent: data.intent || 'unknown',
    confidence: typeof data.confidence === 'number' ? data.confidence : 0,
    routeSteps: routeSteps.map((step, idx) => ({
      stepNumber: step.stepNumber ?? step.step_number ?? idx + 1,
      zoneName: step.zoneName ?? step.zone_name ?? '',
      instruction: step.instruction ?? '',
      estimatedWalkMinutes: step.estimatedWalkMinutes ?? step.estimated_walk_minutes ?? 0,
    })),
    sourcesUsed: Array.isArray(data.sourcesUsed) ? data.sourcesUsed : [],
    provider: data.provider || 'unknown',
    // legacy field — ChatbotWidget hâlâ okuyabilir
    suggestedZones: Array.isArray(data.suggestedZones) ? data.suggestedZones : [],
    hasRoute: routeSteps.length > 0,
  }
}

// ── UI Helpers ────────────────────────────────────────────────────────────────

/**
 * Returns a human-readable Turkish label for an intent (for badges in UI).
 *
 * @param {string} intent
 * @returns {string}
 */
export function intentLabel(intent) {
  const labels = {
    route_request: 'Rota',
    flight_info: 'Uçuş',
    crowd_query: 'Yoğunluk',
    loyalty_query: 'Sadakat',
    general_info: 'Bilgi',
    unknown: 'Genel',
    error: 'Hata',
  }
  return labels[intent] || intent
}

/**
 * Returns a Tailwind color class pair for intent badges.
 *
 * @param {string} intent
 * @returns {string} Tailwind class string (bg + text)
 */
export function intentColor(intent) {
  const colors = {
    route_request: 'bg-blue-100 text-blue-700',
    flight_info: 'bg-purple-100 text-purple-700',
    crowd_query: 'bg-amber-100 text-amber-700',
    loyalty_query: 'bg-emerald-100 text-emerald-700',
    general_info: 'bg-slate-100 text-slate-700',
    unknown: 'bg-slate-100 text-slate-500',
    error: 'bg-red-100 text-red-700',
  }
  return colors[intent] || 'bg-slate-100 text-slate-500'
}

/**
 * @typedef {object} NormalizedChatbotResponse
 * @property {string}   reply                - Bot's text reply
 * @property {string}   intent               - Classified intent
 * @property {number}   confidence           - Classification confidence 0–1
 * @property {Array}    routeSteps           - Step-by-step route (empty if N/A)
 * @property {string[]} sourcesUsed          - Pipeline sources used
 * @property {string}   provider             - Backend provider name
 * @property {string[]} suggestedZones       - Legacy zone suggestions
 * @property {boolean}  hasRoute             - Convenience flag: routeSteps.length > 0
 */
