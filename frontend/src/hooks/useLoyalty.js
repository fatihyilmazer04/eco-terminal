import { useState, useEffect, useCallback } from 'react'
import toast from 'react-hot-toast'
import { loyaltyApi } from '../api/loyaltyApi'
import { useLoyaltyContext } from '../context/LoyaltyContext'

// Aksiyon türlerine göre kazanılacak puan (backend ile senkron)
export const ACTION_POINTS = {
  ROUTE_SELECTION:  50,
  FLIGHT_CHECKIN:   25,
  LOUNGE_CHECKIN:   20,
  ECO_ROUTE_USED:   15,
  QUIET_ZONE_WAIT:  10,
}

export function useLoyalty() {
  const { refreshWallet: refreshNavbar } = useLoyaltyContext()
  const [wallet, setWallet]             = useState(null)
  const [transactions, setTransactions] = useState([])
  const [rewards, setRewards]           = useState([])
  const [loading, setLoading]           = useState(true)
  const [error, setError]               = useState(null)

  const fetchAll = useCallback(async () => {
    try {
      const [wRes, tRes, rRes] = await Promise.all([
        loyaltyApi.getWallet(),
        loyaltyApi.getTransactions(),
        loyaltyApi.getRewards(),
      ])
      setWallet(wRes.data.data)
      setTransactions(tRes.data.data ?? [])
      setRewards(rRes.data.data ?? [])
      setError(null)
    } catch (err) {
      setError(err.response?.data?.message || 'Loyalty verisi alınamadı')
    } finally {
      setLoading(false)
    }
  }, [])

  const spendPoints = useCallback(async (rewardId) => {
    try {
      const res = await loyaltyApi.spend(rewardId)
      const data = res.data.data
      toast.success(`🎉 "${data.rewardTitle}" kullanıldı! Kalan: ${data.remainingBalance} puan`)
      await fetchAll()
      refreshNavbar()   // Navbar bakiyesini anında güncelle
      return data
    } catch (err) {
      const msg = err.response?.data?.message || 'İşlem başarısız'
      toast.error(msg)
      throw err
    }
  }, [fetchAll, refreshNavbar])

  /**
   * action: "ROUTE_SELECTION" | "FLIGHT_CHECKIN" | "ECO_ROUTE_USED" | "QUIET_ZONE_WAIT" | "LOUNGE_CHECKIN"
   * Dönen WalletResponse ile setWallet güncellenir — tam re-fetch gerek yok.
   */
  const earnPoints = useCallback(async (action) => {
    try {
      const res = await loyaltyApi.earn(action)
      const data = res.data.data   // WalletResponse
      const gained = ACTION_POINTS[action] ?? 5
      toast.success(`🌿 +${gained} Eko-Puan kazandınız! Toplam: ${data.currentBalance}`)
      setWallet(data)
      refreshNavbar()   // Navbar bakiyesini anında güncelle
      return data
    } catch (err) {
      toast.error(err.response?.data?.message || 'Puan kazanılamadı')
      throw err
    }
  }, [refreshNavbar])

  useEffect(() => { fetchAll() }, [fetchAll])

  return { wallet, transactions, rewards, loading, error, spendPoints, earnPoints, refetch: fetchAll }
}
