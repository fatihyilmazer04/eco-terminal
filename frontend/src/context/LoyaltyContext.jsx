import React, { createContext, useContext, useState, useEffect, useCallback } from 'react'
import { loyaltyApi } from '../api/loyaltyApi'
import { useAuth } from './AuthContext'

const LoyaltyContext = createContext({
  balance:        null,
  tierLevel:      'GREEN',
  refreshWallet:  () => {},
})

/**
 * Tüm yolcu sayfaları için merkezi eko-puan durumu.
 * Navbar, EcoPointsCard ve puan değişim noktaları bu context'i kullanır.
 * refreshWallet() çağrısı → tüm tüketiciler anında güncellenir.
 */
export function LoyaltyProvider({ children }) {
  const { isAuthenticated } = useAuth()
  const [balance,   setBalance]   = useState(null)
  const [tierLevel, setTierLevel] = useState('GREEN')

  const refreshWallet = useCallback(async () => {
    try {
      const res  = await loyaltyApi.getWallet()
      const data = res.data.data
      setBalance(data?.currentBalance ?? 0)
      setTierLevel(data?.tierLevel ?? 'GREEN')
    } catch {
      // Sessiz başarısızlık — 401 (oturum yok) veya ağ hatası
    }
  }, [])

  // Oturum açıldığında ilk yükleme
  useEffect(() => {
    if (isAuthenticated) refreshWallet()
  }, [isAuthenticated, refreshWallet])

  return (
    <LoyaltyContext.Provider value={{ balance, tierLevel, refreshWallet }}>
      {children}
    </LoyaltyContext.Provider>
  )
}

export function useLoyaltyContext() {
  return useContext(LoyaltyContext)
}
