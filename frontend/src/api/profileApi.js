import axiosInstance from './axiosInstance'

export const profileApi = {
  getProfile()                    { return axiosInstance.get('/api/users/profile') },
  updateProfile(data)             { return axiosInstance.put('/api/users/profile', data) },
  updatePreferences(data)         { return axiosInstance.put('/api/users/preferences', data) },
}
