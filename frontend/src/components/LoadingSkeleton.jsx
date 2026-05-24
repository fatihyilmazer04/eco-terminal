import React from 'react'

/**
 * Heatmap ve genel kart yükleme skeleton'ı.
 * @param {'heatmap' | 'card' | 'list'} variant
 * @param {number} count - Kaç adet gösterilsin (card/list varyantları için)
 */
export default function LoadingSkeleton({ variant = 'card', count = 4 }) {
  if (variant === 'heatmap') {
    return (
      <div className="w-full rounded-xl border border-gray-700 bg-gray-800 animate-pulse overflow-hidden"
        style={{ aspectRatio: '1000/600' }}>
        <div className="w-full h-full flex items-center justify-center">
          <div className="space-y-4 w-3/4">
            <div className="h-8 w-48 bg-gray-700 rounded mx-auto" />
            <div className="grid grid-cols-3 gap-4">
              {[1,2,3].map(i => (
                <div key={i} className="h-20 bg-gray-700 rounded-lg" />
              ))}
            </div>
            <div className="grid grid-cols-5 gap-3">
              {[1,2,3,4,5].map(i => (
                <div key={i} className="h-16 bg-gray-700 rounded" />
              ))}
            </div>
          </div>
        </div>
      </div>
    )
  }

  if (variant === 'list') {
    return (
      <div className="space-y-2 animate-pulse">
        {Array.from({ length: count }).map((_, i) => (
          <div key={i} className="h-14 bg-gray-800 rounded-lg border border-gray-700" />
        ))}
      </div>
    )
  }

  // card (default)
  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 animate-pulse">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="rounded-xl border border-gray-700 bg-gray-800 p-4">
          <div className="h-8 w-12 bg-gray-700 rounded mb-2" />
          <div className="h-4 w-16 bg-gray-700 rounded" />
        </div>
      ))}
    </div>
  )
}
