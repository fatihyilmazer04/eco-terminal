import axiosInstance from './axiosInstance'

export const loyaltyApi = {
  /** GET /api/loyalty/wallet → WalletResponse */
  getWallet()         { return axiosInstance.get('/api/loyalty/wallet') },

  /** GET /api/loyalty/transactions → TransactionResponse[] */
  getTransactions()   { return axiosInstance.get('/api/loyalty/transactions') },

  /** GET /api/loyalty/rewards → RewardResponse[] (canAfford flag dahil) */
  getRewards()        { return axiosInstance.get('/api/loyalty/rewards') },

  /**
   * POST /api/loyalty/spend → SpendResponse
   * body: { rewardId }
   */
  spend(rewardId)     { return axiosInstance.post('/api/loyalty/spend', { rewardId }) },

  /**
   * POST /api/loyalty/earn → WalletResponse
   * action örnekleri: "ROUTE_SELECTION" (+50), "FLIGHT_CHECKIN" (+25),
   *                   "ECO_ROUTE_USED" (+15), "LOUNGE_CHECKIN" (+20), "QUIET_ZONE_WAIT" (+10)
   * body: { action }
   */
  earn(action)        { return axiosInstance.post('/api/loyalty/earn', { action }) },

  /** GET /api/loyalty/my-redemptions → RedemptionResponse[] */
  getMyRedemptions()  { return axiosInstance.get('/api/loyalty/my-redemptions') },
}
