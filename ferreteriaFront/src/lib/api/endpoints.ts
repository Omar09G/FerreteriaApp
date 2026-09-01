import http from './client'
import type {
  ChangePasswordRequest,
  Envelope,
  LoginRequest,
  LogoutOk,
  MeResponse,
  OperacionOk,
  PasswordOk,
  TokenResponse,
} from './types'

export async function apiLogin(payload: LoginRequest): Promise<TokenResponse> {
  const { data } = await http.post<Envelope<TokenResponse>>('/auth/login', payload)
  return data.data
}

/**
 * Refresca el access token. El refresh vive en cookie HttpOnly, así que NO
 * enviamos refreshToken en el body: el browser lo adjunta solo gracias a
 * `withCredentials: true` en el cliente axios.
 */
export async function apiRefresh(): Promise<TokenResponse> {
  const { data } = await http.post<Envelope<TokenResponse>>('/auth/refresh', {})
  return data.data
}

/**
 * Cierra la sesión. Igual que refresh: el browser envía la cookie `rt`
 * automáticamente; el body va vacío.
 */
export async function apiLogout(): Promise<LogoutOk> {
  const { data } = await http.post<Envelope<LogoutOk>>('/auth/logout', {})
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
