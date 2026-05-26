import axiosInstance from './axiosInstance'

/**
 * Chatbot'a soru gönderir.
 * @param {string} message
 * @returns {Promise<{reply: string, suggestedZones?: string[], data?: any}>}
 */
export async function askChatbot(message) {
  const res = await axiosInstance.post('/api/chatbot/ask', { message })
  return res.data.data  // ApiResponse<ChatbotResponse> → .data
}
