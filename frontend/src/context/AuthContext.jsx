import React, { createContext, useContext, useReducer, useEffect } from 'react'
import { authApi } from '../api/authApi'

const AuthContext = createContext(null)

const initialState = {
  user: null,           // { userId, email, role, fullName }
  accessToken: null,
  isAuthenticated: false,
  isLoading: true,      // Başlangıçta localStorage kontrol ediliyor
}

function authReducer(state, action) {
  switch (action.type) {
    case 'LOGIN_SUCCESS':
      return {
        ...state,
        user: action.payload.user,
        accessToken: action.payload.accessToken,
        isAuthenticated: true,
        isLoading: false,
      }
    case 'LOGOUT':
      return { ...initialState, isLoading: false }
    case 'INIT_COMPLETE':
      return { ...state, isLoading: false }
    case 'TOKEN_REFRESHED':
      return { ...state, accessToken: action.payload.accessToken }
    default:
      return state
  }
}

export function AuthProvider({ children }) {
  const [state, dispatch] = useReducer(authReducer, initialState)

  // Sayfa yenilendiğinde localStorage'dan state restore et
  useEffect(() => {
    const token = localStorage.getItem('accessToken')
    const userStr = localStorage.getItem('user')

    if (token && userStr) {
      try {
        const user = JSON.parse(userStr)
        dispatch({ type: 'LOGIN_SUCCESS', payload: { user, accessToken: token } })
      } catch {
        clearStorage()
        dispatch({ type: 'INIT_COMPLETE' })
      }
    } else {
      dispatch({ type: 'INIT_COMPLETE' })
    }
  }, [])

  /**
   * API'den dönen AuthResponse ile state'i günceller.
   * LoginPage ve RegisterPage tarafından çağrılır.
   */
  async function login(email, password) {
    const res = await authApi.login(email, password)
    const { accessToken, refreshToken, role, userId, fullName } = res.data.data

    const user = { userId, email, role, fullName }

    localStorage.setItem('accessToken', accessToken)
    localStorage.setItem('refreshToken', refreshToken)
    localStorage.setItem('user', JSON.stringify(user))

    dispatch({ type: 'LOGIN_SUCCESS', payload: { user, accessToken } })
    return role  // Yönlendirme için role döndür
  }

  async function register(email, password, fullName) {
    const res = await authApi.register(email, password, fullName)
    const { accessToken, refreshToken, role, userId } = res.data.data

    const user = { userId, email, role, fullName }

    localStorage.setItem('accessToken', accessToken)
    localStorage.setItem('refreshToken', refreshToken)
    localStorage.setItem('user', JSON.stringify(user))

    dispatch({ type: 'LOGIN_SUCCESS', payload: { user, accessToken } })
    return role
  }

  function logout() {
    clearStorage()
    dispatch({ type: 'LOGOUT' })
  }

  function isAdmin() {
    return state.user?.role === 'ADMIN'
  }

  return (
    <AuthContext.Provider value={{ ...state, login, logout, register, isAdmin }}>
      {children}
    </AuthContext.Provider>
  )
}

function clearStorage() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('user')
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
