import React from 'react'

/**
 * AI Özet Kutusu — Flask AI servisinden gelen Türkçe önerileri gösterir.
 */
export default function AIInsightBox({ summary, alertZones = [], suggestedZones = [] }) {
  if (!summary && alertZones.length === 0) return null

  return (
    <div className="rounded-xl border border-eco-green/30 bg-eco-green/5 p-4">
      <div className="flex items-center gap-2 mb-3">
        <div className="w-7 h-7 rounded-lg bg-eco-green/20 flex items-center justify-center">
          <span className="text-sm">🧠</span>
        </div>
        <h3 className="text-white font-semibold text-sm">AI Analizi</h3>
      </div>

      {/* Özet cümle */}
      {summary && (
        <p className="text-gray-300 text-sm mb-3 leading-relaxed">{summary}</p>
      )}

      <div className="flex flex-wrap gap-3">
        {/* Uyarı zone'ları */}
        {alertZones.length > 0 && (
          <div>
            <p className="text-xs text-gray-500 mb-1.5">Dolu Bölgeler</p>
            <div className="flex flex-wrap gap-1.5">
              {alertZones.map(name => (
                <span key={name}
                  className="px-2 py-0.5 rounded-full text-xs font-medium bg-red-500/20 text-red-400 border border-red-500/30">
                  {name}
                </span>
              ))}
            </div>
          </div>
        )}

        {/* Öneri zone'ları */}
        {suggestedZones.length > 0 && (
          <div>
            <p className="text-xs text-gray-500 mb-1.5">Alternatif Bölgeler</p>
            <div className="flex flex-wrap gap-1.5">
              {suggestedZones.slice(0, 6).map(name => (
                <span key={name}
                  className="px-2 py-0.5 rounded-full text-xs font-medium bg-eco-green/20 text-eco-green border border-eco-green/30">
                  {name}
                </span>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
