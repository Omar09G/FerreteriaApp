import http from './client'
import type {
  ChangePasswordRequest,
  Envelope,
  LoginRequest,
  LogoutOk,
  MeResponse,
  OperacionOk,
  PasswordOk,
  RefreshRequest,
  TokenResponse,
} from './types'

export async function apiLogin(payload: LoginRequest): Promise<TokenResponse> {
  const { data } = await http.post<Envelope<TokenResponse>>('/auth/login', payload)
  return data.data
}

export async function apiRefresh(payload: RefreshRequest): Promise<TokenResponse> {
  const { data } = await http.post<Envelope<TokenResponse>>('/auth/refresh', payload)
  return data.data
}

export async function apiLogout(refreshToken: string): Promise<LogoutOk> {
  const { data } = await http.post<Envelope<LogoutOk>>('/auth/logout', { refreshToken })
  return data.data
}

export async function apiCambiarPassword(body: ChangePasswordRequest): Promise<PasswordOk> {
  const { data } = await http.post<Envelope<PasswordOk>>('/auth/change-password', body)
  return data.data
}

export async function apiMe(): Promise<MeResponse> {
  const { data } = await http.get<Envelope<MeResponse>>('/auth/me')
  return data.data
}

export async function apiEliminar(_path: string): Promise<OperacionOk> {
  const { data } = await http.delete<Envelope<OperacionOk>>(_path)
  return data.data
}