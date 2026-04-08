import axiosInstance from './axiosInstance'

export const loyaltyApi = {
  getWallet()              { return axiosInstance.get('/api/loyalty/wallet') },
  getTransactions()        { return axiosInstance.get('/api/loyalty/transactions') },
  getRewards()             { return axiosInstance.get('/api/loyalty/rewards') },
  spend(rewardId)          { return axiosInstance.post('/api/loyalty/spend', { rewardId }) },
  earn(action)             { return axiosInstance.post('/api/loyalty/earn', { action }) },
}
