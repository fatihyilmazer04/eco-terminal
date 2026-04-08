import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import OccupancyCard from '../OccupancyCard'

// ── Test Yardımcısı ──────────────────────────────────────────────────────────

function renderCard(overrides = {}) {
  const defaultProps = {
    zoneId: 1,
    zoneName: 'Test Gate',
    type: 'GATE',
    currentCount: 45,
    maxCapacity: 200,
    densityPct: 0.45,
    densityLevel: 'LOW',
    colorCode: '#2ECC71',
    criticalThreshold: 0.85,
    lastUpdated: null,
  }
  return render(<OccupancyCard {...defaultProps} {...overrides} />)
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe('OccupancyCard', () => {

  it('renders_withLowDensity_showsGreenBadge', () => {
    renderCard({ densityLevel: 'LOW', densityPct: 0.20, currentCount: 40 })

    // Badge metnini kontrol et
    expect(screen.getByText('Düşük')).toBeInTheDocument()
    // Pulsing indicator GÖSTERİLMEMELİ (LOW/MEDIUM için)
    const badge = screen.getByText('Düşük')
    expect(badge.className).toContain('green')
  })

  it('renders_withHighDensity_showsOrangeBadge', () => {
    renderCard({
      densityLevel: 'HIGH',
      densityPct: 0.87,
      currentCount: 174,
      colorCode: '#E67E22',
    })

    expect(screen.getByText('Yüksek')).toBeInTheDocument()
    const badge = screen.getByText('Yüksek')
    expect(badge.className).toContain('orange')
  })

  it('renders_withCriticalDensity_showsCriticalBadge', () => {
    renderCard({
      densityLevel: 'CRITICAL',
      densityPct: 0.96,
      currentCount: 192,
      colorCode: '#E74C3C',
    })

    expect(screen.getByText('Kritik')).toBeInTheDocument()
  })

  it('progressBar_widthMatchesDensityPct', () => {
    const { container } = renderCard({ densityPct: 0.65, currentCount: 130 })

    // Progress bar style'ını kontrol et
    const progressBar = container.querySelector('[style*="width: 65%"]')
    expect(progressBar).toBeInTheDocument()
  })

  it('renders_zoneName_andCapacity', () => {
    renderCard({ zoneName: 'Gate-A1', currentCount: 80, maxCapacity: 200 })

    expect(screen.getByText('Gate-A1')).toBeInTheDocument()
    expect(screen.getByText(/200 kişi/)).toBeInTheDocument()
  })

  it('renders_correctPercentage', () => {
    renderCard({ densityPct: 0.65 })
    expect(screen.getByText('%65')).toBeInTheDocument()
  })

  it('renders_withMediumDensity_showsMediumBadge', () => {
    renderCard({ densityLevel: 'MEDIUM', densityPct: 0.70, colorCode: '#F39C12' })
    expect(screen.getByText('Orta')).toBeInTheDocument()
  })
})
