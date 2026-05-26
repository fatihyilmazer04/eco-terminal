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
function ZoneRect({ zone, onClick, isSelected, isRouteZone, isActiveStep, dimmed }) {
  const [hovered, setHovered] = useState(false)
  const { fill, opacity } = ZONE_COLORS[zone.status] ?? ZONE_COLORS.EMPTY

  const x = tx(zone.posX ?? 0)
  const y = ty(zone.posY ?? 0)
  const w = tx(zone.width ?? 10)
  const h = ty(zone.height ?? 10)
  const density = zone.currentDensity ?? 0
  const pct = Math.round(density * 100)
  const label = shortLabel(zone)

  // Rota modunda rota dışı zone'lar soluklaşır
  const effectiveOpacity = dimmed
    ? Math.min(opacity * 0.3, 0.22)
    : hovered ? Math.min(opacity + 0.15, 1) : opacity

  // Kenarlık: seçili > aktif adım > rota zone > normal
  const strokeColor   = isActiveStep ? '#FFFFFF'
    : isSelected  ? '#FFFFFF'
    : isRouteZone ? '#2ECC71'
    : fill
  const strokeWidth   = isActiveStep ? 3 : isSelected ? 2.5 : isRouteZone ? 2 : 1
  const strokeOpacity = (isActiveStep || isSelected || isRouteZone) ? 1 : 0.5

  return (
    <g
      style={{ cursor: 'pointer' }}
      onClick={() => onClick(zone.zoneId)}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      {/* Aktif adım parlama halkası */}
      {isActiveStep && (
        <rect
          x={x - 4} y={y - 4}
          width={w + 8} height={h + 8}
          rx={6} ry={6}
          fill="none"
          stroke="#2ECC71"
          strokeWidth={2.5}
          opacity={0.7}
          style={{ animation: 'pulse-ring 1.5s ease-in-out infinite' }}
        />
      )}

      {/* Pulse ring — FULL zones */}
      {zone.status === 'FULL' && !dimmed && (
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
        fillOpacity={effectiveOpacity}
        stroke={strokeColor}
        strokeWidth={strokeWidth}
        strokeOpacity={strokeOpacity}
      />

      {/* Doluluk bar (alt şerit) */}
      {!dimmed && (
        <rect
          x={x} y={y + h - 4}
          width={w * density} height={4}
          rx={0} ry={0}
          fill={fill}
          fillOpacity={0.95}
        />
      )}

      {/* Etiket: kısa isim */}
      <text
        x={x + w / 2} y={y + h / 2 - 6}
        textAnchor="middle"
        fontSize={Math.min(12, w / label.length * 1.5)}
        fill="#FFFFFF"
        fillOpacity={dimmed ? 0.4 : 1}
        fontWeight="600"
        style={{ userSelect: 'none', pointerEvents: 'none' }}
      >
        {label}
      </text>

      {/* Doluluk yüzdesi + trend */}
      {!dimmed && (
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
      )}
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

// ── Rota çizgisi (animasyonlu) ──────────────────────────────────────────────
function RoutePath({ steps }) {
  // Sadece koordinatı olan adımlar
  const pts = steps.filter(s => s.posX != null && s.posY != null)
  if (pts.length < 2) return null

  const pointsStr = pts.map(s => `${tx(s.posX)},${ty(s.posY)}`).join(' ')

  return (
    <g>
      {/* Glow / halo katmanı */}
      <polyline
        points={pointsStr}
        fill="none"
        stroke="#2ECC71"
        strokeWidth={12}
        strokeOpacity={0.12}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {/* Orta glow */}
      <polyline
        points={pointsStr}
        fill="none"
        stroke="#2ECC71"
        strokeWidth={6}
        strokeOpacity={0.25}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {/* Ana rota çizgisi — animasyonlu çizilme */}
      <polyline
        points={pointsStr}
        fill="none"
        stroke="#2ECC71"
        strokeWidth={3.5}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeDasharray="4000"
        strokeDashoffset="4000"
        style={{ animation: 'route-draw 1.8s ease-out 0.3s forwards' }}
      />
    </g>
  )
}

// ── Rota durak işaretçileri ─────────────────────────────────────────────────
function RouteMarkers({ steps, activeStepNumber, onStepClick }) {
  const pts = steps.filter(s => s.posX != null && s.posY != null)
  if (pts.length === 0) return null

  return (
    <g>
      {pts.map((step, i) => {
        const cx = tx(step.posX)
        const cy = ty(step.posY)
        const isFirst  = i === 0
        const isLast   = i === pts.length - 1
        const isActive = step.stepNumber === activeStepNumber

        const bgColor = isLast ? '#3B82F6' : isFirst ? '#2ECC71' : '#1D4ED8'
        const r = isActive ? 18 : 14

        return (
          <g
            key={step.stepNumber}
            style={{ cursor: 'pointer' }}
            onClick={() => onStepClick?.(step.stepNumber)}
          >
            {/* Aktif pulse halkası */}
            {isActive && (
              <circle cx={cx} cy={cy} r={r + 6}
                fill="none" stroke="#2ECC71" strokeWidth={2}
                opacity={0.6}
                style={{ animation: 'pulse-ring 1.5s ease-in-out infinite' }}
              />
            )}
            {/* İşaretçi arka planı */}
            <circle cx={cx} cy={cy} r={r}
              fill={bgColor}
              stroke="#FFFFFF" strokeWidth={isActive ? 2.5 : 1.5}
              opacity={0.95}
            />
            {/* İçerik */}
            {isFirst ? (
              <text x={cx} y={cy + 5} textAnchor="middle" fontSize={13}
                fill="#FFFFFF" fontWeight="900"
                style={{ userSelect: 'none', pointerEvents: 'none' }}>
                ▶
              </text>
            ) : isLast ? (
              <text x={cx} y={cy + 5} textAnchor="middle" fontSize={13}
                fill="#FFFFFF" fontWeight="900"
                style={{ userSelect: 'none', pointerEvents: 'none' }}>
                ★
              </text>
            ) : (
              <text x={cx} y={cy + 4} textAnchor="middle"
                fontSize={isActive ? 12 : 10}
                fill="#FFFFFF" fontWeight="700"
                style={{ userSelect: 'none', pointerEvents: 'none' }}>
                {step.stepNumber}
              </text>
            )}
          </g>
        )
      })}
    </g>
  )
}

/**
 * AirportHeatmap — SVG tabanlı İstanbul Havalimanı terminal yoğunluk haritası.
 *
 * @param {Array}    zones            - ZoneCrowdStatusResponse listesi (posX/posY dahil)
 * @param {Function} onZoneClick      - (zoneId) => void
 * @param {number}   selectedZoneId   - Seçili zone ID'si
 * @param {Array}    routeSteps       - (opsiyonel) Rota adımları [{stepNumber, zoneName, posX, posY, ...}]
 * @param {number}   activeStepNumber - (opsiyonel) Aktif/seçili adım numarası
 * @param {Function} onRouteStepClick - (opsiyonel) (stepNumber) => void
 */
export default function AirportHeatmap({
  zones = [],
  onZoneClick,
  selectedZoneId,
  routeSteps,
  activeStepNumber,
  onRouteStepClick,
}) {
  const [tooltip, setTooltip] = useState({ zone: null, svgX: 0, svgY: 0 })

  const handleZoneHover = useCallback((zone, svgX, svgY) => {
    setTooltip({ zone, svgX, svgY })
  }, [])

  const handleZoneLeave = useCallback(() => {
    setTooltip({ zone: null, svgX: 0, svgY: 0 })
  }, [])

  // Sadece koordinatı olan zone'ları göster
  const visibleZones = zones.filter(z => z.posX != null && z.posY != null)

  // Rota modu: hangi zone'lar rota üzerinde?
  const hasRoute = routeSteps && routeSteps.length > 0
  const routeZoneNames = hasRoute
    ? new Set(routeSteps.filter(s => s.posX != null).map(s => s.zoneName))
    : null

  // Aktif adımın zone adı (marker ile zone'u çapraz vurgulamak için)
  const activeZoneName = hasRoute && activeStepNumber != null
    ? routeSteps.find(s => s.stepNumber === activeStepNumber)?.zoneName
    : null

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

        {/* Konkors yatay alt şeritleri */}
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
          {hasRoute
            ? 'İSTANBUL HAVALİMANI — ROTA HARİTASI'
            : 'İSTANBUL HAVALİMANI — CANLI YOĞUNLUK HARİTASI'}
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

        {/* Konkors başlıkları */}
        <text x={798} y={30} textAnchor="middle"
          fontSize={8} fill="#475569" fontWeight="600" letterSpacing="1">A KAPILARI</text>
        <text x={738} y={222} textAnchor="middle"
          fontSize={8} fill="#475569" fontWeight="600" letterSpacing="1">B KAPILARI</text>
        <text x={798} y={414} textAnchor="middle"
          fontSize={8} fill="#475569" fontWeight="600" letterSpacing="1">C KAPILARI</text>

        {/* ── Yolcu akışı okları (yalnızca heatmap modunda) ───────────── */}
        {!hasRoute && (
          <>
            <defs>
              <marker id="arrow" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto">
                <path d="M0,0 L0,6 L6,3 z" fill="#334155" />
              </marker>
            </defs>
            {[174, 306, 438].map(ay => (
              <g key={ay}>
                <line x1={173} y1={ay} x2={196} y2={ay}
                  stroke="#334155" strokeWidth={1} markerEnd="url(#arrow)" />
                <line x1={373} y1={ay} x2={410} y2={ay}
                  stroke="#334155" strokeWidth={1} markerEnd="url(#arrow)" />
                <line x1={573} y1={ay} x2={618} y2={ay}
                  stroke="#334155" strokeWidth={1} markerEnd="url(#arrow)" />
              </g>
            ))}
          </>
        )}

        {/* ── Konkors omurgası ve bağlantı dalları ────────────────────── */}
        <line x1={627} y1={40} x2={627} y2={H - 58}
          stroke="#334155" strokeWidth={0.8} strokeDasharray="3 3" />
        <line x1={627} y1={99} x2={970} y2={99}
          stroke="#334155" strokeWidth={0.6} strokeDasharray="3 3" />
        <line x1={627} y1={291} x2={850} y2={291}
          stroke="#334155" strokeWidth={0.6} strokeDasharray="3 3" />
        <line x1={627} y1={483} x2={970} y2={483}
          stroke="#334155" strokeWidth={0.6} strokeDasharray="3 3" />

        {/* ── Zone rectleri ────────────────────────────────────────────── */}
        {visibleZones.map(zone => {
          const isRouteZone  = hasRoute ? routeZoneNames.has(zone.zoneName) : false
          const isActiveStep = zone.zoneName === activeZoneName
          const dimmed       = hasRoute && !isRouteZone

          return (
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
                onClick={onZoneClick ?? (() => {})}
                isSelected={selectedZoneId === zone.zoneId}
                isRouteZone={isRouteZone}
                isActiveStep={isActiveStep}
                dimmed={dimmed}
              />
            </g>
          )
        })}

        {/* ── Rota çizgisi (routeSteps varsa) ─────────────────────────── */}
        {hasRoute && <RoutePath steps={routeSteps} />}

        {/* ── Rota durak işaretçileri ──────────────────────────────────── */}
        {hasRoute && (
          <RouteMarkers
            steps={routeSteps}
            activeStepNumber={activeStepNumber}
            onStepClick={onRouteStepClick}
          />
        )}

        {/* ── Hover tooltip ────────────────────────────────────────────── */}
        {tooltip.zone && (
          <Tooltip
            zone={tooltip.zone}
            x={Math.min(tooltip.svgX, W - 200)}
            y={Math.max(tooltip.svgY - 40, 10)}
          />
        )}

        {/* ── Renk açıklaması ──────────────────────────────────────────── */}
        {!hasRoute && [
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

        {/* Rota modu açıklama */}
        {hasRoute && (
          <g transform="translate(600, 568)">
            <circle cx={6} cy={6} r={6} fill="#2ECC71" opacity={0.9} />
            <text x={16} y={10} fontSize={9} fill="#64748B">Başlangıç</text>
            <circle cx={86} cy={6} r={6} fill="#3B82F6" opacity={0.9} />
            <text x={96} y={10} fontSize={9} fill="#64748B">Varış Kapısı</text>
            <circle cx={186} cy={6} r={6} fill="#1D4ED8" opacity={0.9} />
            <text x={196} y={10} fontSize={9} fill="#64748B">Durak</text>
          </g>
        )}

        {/* Animasyon stilleri */}
        <style>{`
          @keyframes pulse-ring {
            0%   { opacity: 0.6; transform: scale(1);   }
            50%  { opacity: 0.2; transform: scale(1.04); }
            100% { opacity: 0.6; transform: scale(1);   }
          }
          @keyframes route-draw {
            from { stroke-dashoffset: 4000; }
            to   { stroke-dashoffset: 0; }
          }
        `}</style>
      </svg>
    </div>
  )
}
