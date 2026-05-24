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
          dark:     '#0F1419',
          amber:    '#F59E0B',
          blue:     '#3B82F6',
          emerald:  '#10B981',
        },
        // Heatmap renkleri
        'eco-green':   '#2ECC71',
        'eco-dark':    '#0F1419',
        'eco-card':    '#1A1F2E',
        'eco-border':  '#2A3040',
        'eco-red':     '#EF4444',
        'eco-amber':   '#F59E0B',
        'eco-blue':    '#3B82F6',
        'eco-emerald': '#10B981',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'monospace'],
      },
      animation: {
        'pulse-slow': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'fade-in':    'fadeIn 0.3s ease-in-out',
        'spin-slow':  'spin 2s linear infinite',
      },
      keyframes: {
        fadeIn: {
          '0%':   { opacity: '0', transform: 'translateY(4px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
      },
    },
  },
  plugins: [],
}
