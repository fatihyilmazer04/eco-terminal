import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import LoginPage from '../../pages/auth/LoginPage'

// ── Mock'lar ─────────────────────────────────────────────────────────────────

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

const mockLogin = vi.fn()
vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ login: mockLogin }),
}))

vi.mock('react-hot-toast', () => ({
  default: { success: vi.fn(), error: vi.fn() },
}))

// ── Test Yardımcıları ─────────────────────────────────────────────────────────

function renderLoginPage() {
  return render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>
  )
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('LoginPage', () => {

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders_loginForm_withEmailAndPasswordFields', () => {
    renderLoginPage()
    expect(screen.getByLabelText(/e-posta/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/şifre/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /giriş yap/i })).toBeInTheDocument()
  })

  it('submit_withEmptyFields_showsValidationErrors', async () => {
    renderLoginPage()
    fireEvent.click(screen.getByRole('button', { name: /giriş yap/i }))

    await waitFor(() => {
      expect(screen.getByText(/e-posta zorunludur/i)).toBeInTheDocument()
      expect(screen.getByText(/şifre zorunludur/i)).toBeInTheDocument()
    })
    expect(mockLogin).not.toHaveBeenCalled()
  })

  it('submit_withInvalidEmail_showsEmailError', async () => {
    renderLoginPage()
    fireEvent.change(screen.getByLabelText(/e-posta/i), {
      target: { name: 'email', value: 'gecersiz-email' },
    })
    fireEvent.change(screen.getByLabelText(/şifre/i), {
      target: { name: 'password', value: 'pass123' },
    })
    fireEvent.click(screen.getByRole('button', { name: /giriş yap/i }))

    await waitFor(() => {
      expect(screen.getByText(/geçerli bir e-posta/i)).toBeInTheDocument()
    })
    expect(mockLogin).not.toHaveBeenCalled()
  })

  it('submit_withValidCredentials_callsLoginApi', async () => {
    mockLogin.mockResolvedValue('USER')
    renderLoginPage()

    fireEvent.change(screen.getByLabelText(/e-posta/i), {
      target: { name: 'email', value: 'test@eco.com' },
    })
    fireEvent.change(screen.getByLabelText(/şifre/i), {
      target: { name: 'password', value: 'pass123' },
    })
    fireEvent.click(screen.getByRole('button', { name: /giriş yap/i }))

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith('test@eco.com', 'pass123')
    })
  })

  it('submit_onApiError_showsErrorMessage', async () => {
    mockLogin.mockRejectedValue({
      response: { data: { message: 'Geçersiz e-posta veya şifre' } },
    })
    renderLoginPage()

    fireEvent.change(screen.getByLabelText(/e-posta/i), {
      target: { name: 'email', value: 'test@eco.com' },
    })
    fireEvent.change(screen.getByLabelText(/şifre/i), {
      target: { name: 'password', value: 'wrongpass' },
    })
    fireEvent.click(screen.getByRole('button', { name: /giriş yap/i }))

    await waitFor(() => {
      expect(screen.getByText(/geçersiz e-posta veya şifre/i)).toBeInTheDocument()
    })
  })

  it('submit_onSuccess_adminRole_redirectsToAdminDashboard', async () => {
    mockLogin.mockResolvedValue('ADMIN')
    renderLoginPage()

    fireEvent.change(screen.getByLabelText(/e-posta/i), {
      target: { name: 'email', value: 'admin@eco.com' },
    })
    fireEvent.change(screen.getByLabelText(/şifre/i), {
      target: { name: 'password', value: 'admin123' },
    })
    fireEvent.click(screen.getByRole('button', { name: /giriş yap/i }))

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/admin/dashboard', { replace: true })
    })
  })

  it('submit_onSuccess_userRole_redirectsToPassengerDashboard', async () => {
    mockLogin.mockResolvedValue('USER')
    renderLoginPage()

    fireEvent.change(screen.getByLabelText(/e-posta/i), {
      target: { name: 'email', value: 'passenger@eco.com' },
    })
    fireEvent.change(screen.getByLabelText(/şifre/i), {
      target: { name: 'password', value: 'pass123' },
    })
    fireEvent.click(screen.getByRole('button', { name: /giriş yap/i }))

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/passenger/dashboard', { replace: true })
    })
  })
})
