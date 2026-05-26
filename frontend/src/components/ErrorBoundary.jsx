import React from 'react'

/**
 * Uygulama genelinde beklenmedik render hatalarını yakalar.
 * Yakalanan hata tüm uygulamayı çöktürmek yerine kullanıcı dostu
 * bir hata ekranı gösterir.
 */
export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props)
    this.state = { hasError: false, message: null }
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, message: error?.message ?? 'Bilinmeyen hata' }
  }

  componentDidCatch(error, info) {
    // Production'da bir hata izleme servisi (Sentry vb.) çağrılabilir
    console.error('[ErrorBoundary]', error, info.componentStack)
  }

  handleReset = () => {
    this.setState({ hasError: false, message: null })
    window.location.reload()
  }

  render() {
    if (!this.state.hasError) return this.props.children

    return (
      <div className="min-h-screen bg-gray-900 flex items-center justify-center p-6">
        <div className="bg-gray-800 border border-red-500/30 rounded-2xl p-8 max-w-md w-full text-center shadow-2xl">
          <div className="w-14 h-14 rounded-xl bg-red-500/10 border border-red-500/30 flex items-center justify-center mx-auto mb-5">
            <svg className="w-7 h-7 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
          </div>
          <h2 className="text-white text-xl font-bold mb-2">Bir hata oluştu</h2>
          <p className="text-gray-400 text-sm mb-6">
            Beklenmedik bir sorun yaşandı. Sayfayı yenileyerek tekrar deneyin.
          </p>
          {this.state.message && (
            <p className="text-red-400/70 text-xs font-mono bg-gray-900/60 rounded-lg px-3 py-2 mb-5 text-left break-all">
              {this.state.message}
            </p>
          )}
          <button
            onClick={this.handleReset}
            className="w-full py-2.5 rounded-xl bg-eco-green text-gray-900 font-bold text-sm
                       hover:bg-green-400 transition-colors"
          >
            Sayfayı Yenile
          </button>
        </div>
      </div>
    )
  }
}
