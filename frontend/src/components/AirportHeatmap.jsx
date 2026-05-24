import React, { useState, useCallback } from 'react'

// SVG boyutları
const W = 1000
const H = 600

// Density → renk (#RRGGBB, opacity)
const ZONE_COLORS = {
  FULL:     { fill: '#EF4444', opacity: 0.85 },   // kırmızı
  BUSY:     { fill: '#F59E0B', opacity: 0.75 },   // sarı
  MODERATE: { fill: '#3B82F6', opacity: 0.65 },   // mavi
  EMPTY:    { fill: '#10B981', opacity: 0.55 },   // yeşil
}

// Nokta koordinat dönüşümü (% → SVG px)
const tx = pct => (pct / 100) * W
const ty = pct => (pct / 100) * H

// Zone tipi → kısa etiket (SVG içinde gösterim)
function shortLabel(zone) {
  const n = zone.zoneName || ''
  if (n.startsWith('Gate '))    return n.replace('Gate ', '')
  if (n.startsWith('Security')) return n.replace('Security-', 'Sec ')
  if (n.startsWith('CheckIn'))  return n.replace('CheckIn-', 'CI ')
  if (n.startsWith('Lounge'))   return n.replace('Lounge-', 'L ')
  return n
}

// Trend oku
function TrendArrow({ trend }) {
  if (trend === 'INCREASING') return <tspan fill="#EF4444"> ↑</tspan>
  if (trend === 'DECREASING') return <tspan fill="#10B981"> ↓</tspan>
  return <tspan fill="#9CA3AF"> →</tspan>
}

// Zone rect bileşeni
function ZoneRect({ zone, onClick, isSelected }) {
  const [hovered, setHovered] = useState(false)
  const { fill, opacity } = ZONE_COLORS[zone.status] ?? ZONE_COLORS.EMPTY

  const x = tx(zone.posX ?? 0)
  const y = ty(zone.posY ?? 0)
  const w = tx(zone.width ?? 10)
  const h = ty(zone.height ?? 10)
  const density = zone.currentDensity ?? 0
  const pct = Math.round(density * 100)
  const label = shortLabel(zone)

  return (
    <g
      style={{ cursor: 'pointer' }}
      onClick={() => onClick(zone.zoneId)}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      {/* Pulse ring — FULL zones */}
      {zone.status === 'FULL' && (
        <rect
          x={x - 3} y={y - 3}
          width={w + 6} height={h + 6}
          rx={5} ry={5}
          fill="none"
          stroke="#EF4444"
          strokeWidth={2}
          opacity={0.5}
          style={{ animation: 'pulse-ring 2s ease-in-out infinite' }}
        />
      )}

      {/* Ana kare */}
      <rect
        x={x} y={y}
        width={w} height={h}
        rx={4} ry={4}
        fill={fill}
        fillOpacity={hovered ? Math.min(opacity + 0.15, 1) : opacity}
        stroke={isSelected ? '#FFFFFF' : fill}
        strokeWidth={isSelected ? 2.5 : 1}
        strokeOpacity={isSelected ? 1 : 0.5}
      />

      {/* Doluluk bar (alt şerit) */}
      <rect
        x={x} y={y + h - 4}
        width={w * density} height={4}
        rx={0} ry={0}
        fill={fill}
        fillOpacity={0.95}
      />

      {/* Etiket: kısa isim */}
      <text
        x={x + w / 2} y={y + h / 2 - 6}
        textAnchor="middle"
        fontSize={Math.min(12, w / label.length * 1.5)}
        fill="#FFFFFF"
        fontWeight="600"
        style={{ userSelect: 'none', pointerEvents: 'none' }}
      >
        {label}
      </text>

      {/* Doluluk yüzdesi + trend */}
      <text
        x={x + w / 2} y={y + h / 2 + 10}
        textAnchor="middle"
        fontSize={11}
        fill="#FFFFFF"
        fillOpacity={0.9}
        style={{ userSelect: 'none', pointerEvents: 'none' }}
      >
        {pct}%
        <TrendArrow trend={zone.trend} />
      </text>
    </g>
  )
}

// Tooltip
function Tooltip({ zone, x, y }) {
  if (!zone) return null
  const density = zone.currentDensity ?? 0
  const pct = Math.round(density * 100)
  const statusTr = { FULL: 'Dolu', BUSY: 'Yoğun', MODERATE: 'Normal', EMPTY: 'Boş' }

  return (
    <g>
      <rect
        x={x + 8} y={y - 10}
        width={180} height={90}
        rx={6} ry={6}
        fill="#1F2937"
        stroke="#374151"
        strokeWidth={1}
        opacity={0.97}
      />
      <text x={x + 16} y={y + 10} fontSize={12} fill="#F9FAFB" fontWeight="600">
        {zone.zoneName}
      </text>
      <text x={x + 16} y={y + 26} fontSize={10} fill="#9CA3AF">
        Durum: <tspan fill="#F9FAFB">{statusTr[zone.status] ?? zone.status}</tspan>
      </text>
      <text x={x + 16} y={y + 40} fontSize={10} fill="#9CA3AF">
        Doluluk: <tspan fill="#F9FAFB">{pct}%</tspan>
        {' '}({zone.peopleCount ?? 0}/{zone.capacity ?? '?'} kişi)
      </text>
      <text x={x + 16} y={y + 54} fontSize={10} fill="#9CA3AF">
        Risk: <tspan fill={zone.riskLevel === 'HIGH' ? '#EF4444' : zone.riskLevel === 'MEDIUM' ? '#F59E0B' : '#10B981'}>
          {zone.riskLevel ?? 'LOW'}
        </tspan>
      </text>
      {zone.predictedLoad != null && (
        <text x={x + 16} y={y + 68} fontSize={10} fill="#9CA3AF">
          AI Tahmin: <tspan fill="#F9FAFB">{Math.round(zone.predictedLoad * 100)}%</tspan>
        </text>
      )}
    </g>
  )
}

/**
 * AirportHeatmap — SVG tabanlı İstanbul Havalimanı terminal yoğunluk haritası.
 *
 * @param {Array}    zones          - ZoneCrowdStatusResponse listesi (posX/posY dahil)
 * @param {Function} onZoneClick    - (zoneId) => void
 * @param {number}   selectedZoneId - Seçili zone ID'si
 */
export default function AirportHeatmap({ zones = [], onZoneClick, selectedZoneId }) {
  const [tooltip, setTooltip] = useState({ zone: null, svgX: 0, svgY: 0 })

  const handleZoneHover = useCallback((zone, svgX, svgY) => {
    setTooltip({ zone, svgX, svgY })
  }, [])

  const handleZoneLeave = useCallback(() => {
    setTooltip({ zone: null, svgX: 0, svgY: 0 })
  }, [])

  // Sadece koordinatı olan zone'ları göster
  const visibleZones = zones.filter(z => z.posX != null && z.posY != null)

  return (
    <div className="w-full rounded-xl border border-gray-700 overflow-hidden bg-gray-950">
      <svg
        viewBox={`0 0 ${W} ${H}`}
        width="100%"
        style={{ display: 'block', background: '#0F172A' }}
        aria-label="İstanbul Havalimanı Terminal Yoğunluk Haritası"
      >
        {/* ── Arka plan bandları ──────────────────────────────────────── */}
        {/* CheckIn + Güvenlik bandı */}
        <rect x={0} y={15} width={W} height={80}
          fill="#1E293B" fillOpacity={0.6} />
        {/* Orta yürüyüş koridoru */}
        <rect x={0} y={120} width={W} height={40}
          fill="#0F172A" fillOpacity={0.8} />
        {/* Gate bandı */}
        <rect x={0} y={160} width={W} height={108}
          fill="#1E293B" fillOpacity={0.4} />
        {/* Alt yürüyüş koridoru */}
        <rect x={0} y={320} width={W} height={30}
          fill="#0F172A" fillOpacity={0.8} />
        {/* Lounge bandı */}
        <rect x={0} y={320} width={W} height={120}
          fill="#1E293B" fillOpacity={0.3} />

        {/* ── Terminal çerçevesi ──────────────────────────────────────── */}
        <rect x={4} y={4} width={W - 8} height={H - 8}
          rx={8} ry={8}
          fill="none" stroke="#334155" strokeWidth={1.5} strokeDasharray="8 4" />

        {/* ── Başlık ──────────────────────────────────────────────────── */}
        <text x={W / 2} y={14} textAnchor="middle"
          fontSize={11} fill="#64748B" fontWeight="600" letterSpacing="2">
          İSTANBUL HAVALİMANI — CANLI YOĞUNLUK HARİTASI
        </text>

        {/* ── Bölüm etiketleri ────────────────────────────────────────── */}
        <text x={16} y={32} fontSize={9} fill="#64748B" fontWeight="700" letterSpacing="1">CHECK-IN</text>
        <text x={500} y={32} fontSize={9} fill="#64748B" fontWeight="700" letterSpacing="1">GÜVENLİK</text>
        <text x={16} y={175} fontSize={9} fill="#64748B" fontWeight="700" letterSpacing="1">KAPILA</text>

        {/* Concourse ayırıcılar */}
        <line x1={400} y1={160} x2={400} y2={268} stroke="#334155" strokeWidth={1} strokeDasharray="4 3" />
        <line x1={650} y1={160} x2={650} y2={268} stroke="#334155" strokeWidth={1} strokeDasharray="4 3" />

        {/* Concourse etiketleri */}
        <text x={200} y={155} textAnchor="middle" fontSize={8} fill="#475569" fontWeight="600">KONKoRS A</text>
        <text x={525} y={155} textAnchor="middle" fontSize={8} fill="#475569" fontWeight="600">KONKORS B</text>
        <text x={800} y={155} textAnchor="middle" fontSize={8} fill="#475569" fontWeight="600">KONKORS C</text>

        {/* Lounge etiketi */}
        <text x={16} y={340} fontSize={9} fill="#64748B" fontWeight="700" letterSpacing="1">SALONLAR</text>

        {/* ── Yürüyüş yolu okları ─────────────────────────────────────── */}
        {[200, 400, 600, 800].map(cx => (
          <g key={cx}>
            <line x1={cx} y1={125} x2={cx} y2={155}
              stroke="#334155" strokeWidth={1} markerEnd="url(#arrow)" />
          </g>
        ))}
        <defs>
          <marker id="arrow" markerWidth="6" markerHeight="6" refX="3" refY="3" orient="auto">
            <path d="M0,0 L0,6 L6,3 z" fill="#334155" />
          </marker>
        </defs>

        {/* ── Zone rectleri ────────────────────────────────────────────── */}
        {visibleZones.map(zone => (
          <g
            key={zone.zoneId}
            onMouseMove={e => {
              const svgEl = e.currentTarget.closest('svg')
              const pt = svgEl.createSVGPoint()
              pt.x = e.clientX; pt.y = e.clientY
              const svgP = pt.matrixTransform(svgEl.getScreenCTM().inverse())
              handleZoneHover(zone, svgP.x, svgP.y)
            }}
            onMouseLeave={handleZoneLeave}
          >
            <ZoneRect
              zone={zone}
              onClick={onZoneClick}
              isSelected={selectedZoneId === zone.zoneId}
            />
          </g>
        ))}

        {/* ── Hover tooltip ────────────────────────────────────────────── */}
        {tooltip.zone && (
          <Tooltip
            zone={tooltip.zone}
            x={Math.min(tooltip.svgX, W - 200)}
            y={Math.max(tooltip.svgY - 40, 10)}
          />
        )}

        {/* ── Renk açıklaması ──────────────────────────────────────────── */}
        {[
          { color: '#EF4444', label: 'Dolu ≥85%' },
          { color: '#F59E0B', label: 'Yoğun ≥65%' },
          { color: '#3B82F6', label: 'Normal ≥40%' },
          { color: '#10B981', label: 'Boş <40%' },
        ].map((item, i) => (
          <g key={item.label} transform={`translate(${700 + i * 75}, 570)`}>
            <rect width={12} height={12} rx={2} fill={item.color} fillOpacity={0.8} />
            <text x={16} y={10} fontSize={9} fill="#64748B">{item.label}</text>
          </g>
        ))}

        {/* Pulse animasyonu FULL zone'lar için */}
        <style>{`
          @keyframes pulse-ring {
            0%   { opacity: 0.6; transform: scale(1);   }
            50%  { opacity: 0.2; transform: scale(1.04); }
            100% { opacity: 0.6; transform: scale(1);   }
          }
        `}</style>
      </svg>
    </div>
  )
}
