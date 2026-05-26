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
        {/* ── Arka plan: dikey bölüm sütunları (sol→sağ yolcu akışı) ─── */}
        {/* Check-in sütunu  (posX 0-20%) */}
        <rect x={0} y={10} width={200} height={H - 18}
          fill="#1E293B" fillOpacity={0.55} />
        {/* Güvenlik sütunu (posX 20-41%) */}
        <rect x={200} y={10} width={215} height={H - 18}
          fill="#1A2534" fillOpacity={0.45} />
        {/* Merkezi Terminal / Lounge sütunu (posX 41-62%) */}
        <rect x={415} y={10} width={210} height={H - 18}
          fill="#161F2E" fillOpacity={0.40} />
        {/* Gate alanı (posX 62-100%) */}
        <rect x={625} y={10} width={375} height={H - 18}
          fill="#1B2A3B" fillOpacity={0.30} />

        {/* Konkors yatay alt şeritleri
            A: posY 8-25% → y 48-150 px   (A1/A2/A3 — 3 gate)
            B: posY 40-57% → y 240-342 px  (B1/B2 — 2 gate)
            C: posY 72-89% → y 432-534 px  (C1/C2/C3 — 3 gate)          */}
        <rect x={627} y={40} width={343} height={118}
          fill="#1E3554" fillOpacity={0.38} rx={3} />
        <rect x={627} y={232} width={223} height={118}
          fill="#1E3554" fillOpacity={0.38} rx={3} />
        <rect x={627} y={424} width={343} height={118}
          fill="#1E3554" fillOpacity={0.38} rx={3} />

        {/* ── Terminal çerçevesi ──────────────────────────────────────── */}
        <rect x={4} y={4} width={W - 8} height={H - 8}
          rx={8} ry={8}
          fill="none" stroke="#334155" strokeWidth={1.5} strokeDasharray="8 4" />

        {/* ── Başlık ──────────────────────────────────────────────────── */}
        <text x={W / 2} y={14} textAnchor="middle"
          fontSize={11} fill="#64748B" fontWeight="600" letterSpacing="2">
          İSTANBUL HAVALİMANI — CANLI YOĞUNLUK HARİTASI
        </text>

        {/* ── Dikey bölüm ayırıcıları ─────────────────────────────────── */}
        <line x1={200} y1={18} x2={200} y2={H - 8}
          stroke="#334155" strokeWidth={1} strokeDasharray="5 4" />
        <line x1={415} y1={18} x2={415} y2={H - 8}
          stroke="#334155" strokeWidth={1} strokeDasharray="5 4" />
        <line x1={625} y1={18} x2={625} y2={H - 8}
          stroke="#334155" strokeWidth={1.5} />

        {/* ── Bölüm başlıkları ────────────────────────────────────────── */}
        <text x={100} y={32} textAnchor="middle"
          fontSize={9} fill="#64748B" fontWeight="700" letterSpacing="1.5">CHECK-IN</text>
        <text x={307} y={32} textAnchor="middle"
          fontSize={9} fill="#64748B" fontWeight="700" letterSpacing="1.5">GÜVENLİK</text>
        <text x={520} y={32} textAnchor="middle"
          fontSize={9} fill="#64748B" fontWeight="700" letterSpacing="1">MERKEZİ TERMİNAL</text>

        {/* Konkors başlıkları (gate alanı içinde, her sub-band üstü) */}
        <text x={798} y={30} textAnchor="middle"
          fontSize={8} fill="#475569" fontWeight="600" letterSpacing="1">A KAPILARI</text>
        <text x={738} y={222} textAnchor="middle"
          fontSize={8} fill="#475569" fontWeight="600" letterSpacing="1">B KAPILARI</text>
        <text x={798} y={414} textAnchor="middle"
          fontSize={8} fill="#475569" fontWeight="600" letterSpacing="1">C KAPILARI</text>

        {/* ── Yolcu akışı okları (sol → sağ, her CheckIn satırı hizasında) */}
        <defs>
          <marker id="arrow" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto">
            <path d="M0,0 L0,6 L6,3 z" fill="#334155" />
          </marker>
        </defs>
        {/* y=174 CheckIn-1 merkezi, y=306 CheckIn-2 merkezi, y=438 CheckIn-3 merkezi */}
        {[174, 306, 438].map(ay => (
          <g key={ay}>
            {/* CheckIn → Güvenlik */}
            <line x1={173} y1={ay} x2={196} y2={ay}
              stroke="#334155" strokeWidth={1} markerEnd="url(#arrow)" />
            {/* Güvenlik → Merkezi Terminal */}
            <line x1={373} y1={ay} x2={410} y2={ay}
              stroke="#334155" strokeWidth={1} markerEnd="url(#arrow)" />
            {/* Merkezi Terminal → Gate Alanı */}
            <line x1={573} y1={ay} x2={618} y2={ay}
              stroke="#334155" strokeWidth={1} markerEnd="url(#arrow)" />
          </g>
        ))}

        {/* ── Konkors omurgası ve yatay bağlantı dalları ──────────────── */}
        {/* Dikey omurga (gate alanı sol kenarı boyunca) */}
        <line x1={627} y1={40} x2={627} y2={H - 58}
          stroke="#334155" strokeWidth={0.8} strokeDasharray="3 3" />
        {/* A dalı: merkez y=99, A1-A3 sağ kenar x=970 */}
        <line x1={627} y1={99} x2={970} y2={99}
          stroke="#334155" strokeWidth={0.6} strokeDasharray="3 3" />
        {/* B dalı: merkez y=291, B1-B2 sağ kenar x=850 */}
        <line x1={627} y1={291} x2={850} y2={291}
          stroke="#334155" strokeWidth={0.6} strokeDasharray="3 3" />
        {/* C dalı: merkez y=483, C1-C3 sağ kenar x=970 */}
        <line x1={627} y1={483} x2={970} y2={483}
          stroke="#334155" strokeWidth={0.6} strokeDasharray="3 3" />

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
