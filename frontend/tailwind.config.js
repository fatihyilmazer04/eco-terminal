/** @type {import('tailwindcss').Config} */
export default {
  // class stratejisi: <html class="dark"> ile toggle edilir
  darkMode: 'class',
  content: [
    './index.html',
    './src/**/*.{js,jsx}',
  ],
  theme: {
    extend: {
      colors: {
        // Eco-Terminal marka renkleri
        eco: {
          green:    '#2ECC71',   // Düşük yoğunluk / vurgu
          orange:   '#F39C12',   // Orta yoğunluk
          red:      '#E74C3C',   // Yüksek yoğunluk / kritik
          darkbg:   '#111827',   // bg-gray-900 eş değeri
          card:     '#1F2937',   // bg-gray-800
          border:   '#374151',   // border-gray-700
          text:     '#F9FAFB',   // text-gray-50
          muted:    '#9CA3AF',   // text-gray-400
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'monospace'],
      },
      animation: {
        'pulse-slow': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
      },
    },
  },
  plugins: [],
}
