import { useState, useEffect, useCallback } from 'react'
import toast from 'react-hot-toast'
import { loyaltyApi } from '../api/loyaltyApi'

export function useLoyalty() {
  const [wallet, setWallet]           = useState(null)
  const [transactions, setTransactions] = useState([])
  const [rewards, setRewards]         = useState([])
  const [loading, setLoading]         = useState(true)
  const [error, setError]             = useState(null)

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
      return data
    } catch (err) {
      const msg = err.response?.data?.message || 'İşlem başarısız'
      toast.error(msg)
      throw err
    }
  }, [fetchAll])

  const earnPoints = useCallback(async (action) => {
    try {
      const res = await loyaltyApi.earn(action)
      const data = res.data.data
      toast.success(`🌿 Puan kazandınız! Toplam: ${data.currentBalance}`)
      setWallet(data)
      return data
    } catch (err) {
      toast.error(err.response?.data?.message || 'Puan kazanılamadı')
      throw err
    }
  }, [])

  useEffect(() => { fetchAll() }, [fetchAll])

  return { wallet, transactions, rewards, loading, error, spendPoints, earnPoints, refetch: fetchAll }
}
