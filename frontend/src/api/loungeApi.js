import axiosInstance from './axiosInstance'

export const loungeApi = {
  getQuietLounges() { return axiosInstance.get('/api/lounges') },
  getBestLounge()   { return axiosInstance.get('/api/lounges/best') },
}
