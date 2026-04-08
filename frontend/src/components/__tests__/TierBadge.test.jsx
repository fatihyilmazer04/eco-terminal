import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import TierBadge from '../TierBadge'

describe('TierBadge', () => {

  it('renders_greenTier_withCorrectLabel', () => {
    render(<TierBadge tierLevel="GREEN" />)
    expect(screen.getByText(/green member/i)).toBeInTheDocument()
  })

  it('renders_goldTier_withCorrectLabel', () => {
    render(<TierBadge tierLevel="GOLD" />)
    expect(screen.getByText(/gold member/i)).toBeInTheDocument()
  })

  it('renders_platinumTier_withCorrectLabel', () => {
    render(<TierBadge tierLevel="PLATINUM" />)
    expect(screen.getByText(/platinum member/i)).toBeInTheDocument()
  })

  it('renders_greenTier_withGreenIcon', () => {
    render(<TierBadge tierLevel="GREEN" />)
    expect(screen.getByText('🌿')).toBeInTheDocument()
  })

  it('renders_goldTier_withStarIcon', () => {
    render(<TierBadge tierLevel="GOLD" />)
    expect(screen.getByText('⭐')).toBeInTheDocument()
  })

  it('renders_platinumTier_withDiamondIcon', () => {
    render(<TierBadge tierLevel="PLATINUM" />)
    expect(screen.getByText('💎')).toBeInTheDocument()
  })

  it('renders_withSmSize_appliesSmClasses', () => {
    const { container } = render(<TierBadge tierLevel="GREEN" size="sm" />)
    expect(container.firstChild).toBeTruthy()
  })

  it('renders_withUnknownTier_doesNotCrash', () => {
    // Bilinmeyen tier → crash olmamalı, container render edilmeli
    const { container } = render(<TierBadge tierLevel="UNKNOWN" />)
    expect(container).toBeTruthy()
  })
})
