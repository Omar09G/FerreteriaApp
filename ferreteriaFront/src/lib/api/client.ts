import axios, { type AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'

import { tFuera } from '@/i18n'
import { useAuthStore } from '@/store/auth'
import type { ApiErrorBody, TokenResponse } from '@/lib/api/types'

export class ApiError extends Error {
  readonly codigo: string
  readonly status: number
  readonly details?: ApiErrorBody['details']
  readonly requestId?: string
  readonly instance?: string

  constructor(body: ApiErrorBody) {
    const ui = body.errorMessage || body.codigo || tFuera('errores.desconocido')
    super(ui)
    this.name = 'ApiError'
    this.codigo = body.codigo || 'ERROR_INTERNO'
    this.status = body.errorCode ?? 0
    this.details = body.details
    this.requestId = body.requestId
    this.instance = body.instance
  }

  /** Mensaje amigable mostrado en toasts, incluye referencia de soporte si existe. */
  mensajeParaUsuario(): string {
    const base = this.message
    if (this.requestId || this.instance) {
      const ref = this.requestId ? ` (folio: ${this.requestId})` : this.instance
      return `${base}${ref}`
    }
    return base
  }
}

export function esApiError(e: unknown): e is ApiError {
  return e instanceof ApiError
}

/** Mensaje para toasts: usa el error del backend (ApiError) o un genérico del front. */
export function mensajeError(e: unknown): string {
  if (esApiError(e)) return e.mensajeParaUsuario()
  if (e instanceof Error && e.message) return e.message
  return tFuera('errores.generico')
}

function nuevoRequestId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`
}

// DEV sin proxy: flag en .env que SOLO en desarrollo hace que el front llame
// directo al backend (baseURL absoluta) sin pasar por el proxy de Vite.
const devSinProxy = import.meta.env.DEV && import.meta.env.VITE_DEV_SIN_PROXY === 'true'
const apiUrl = import.meta.env.VITE_API_URL as string | undefined
const proxyHost = (import.meta.env.VITE_API_PROXY as string | undefined) || 'http://localhost:8080'
const BASE_URL = apiUrl || (devSinProxy ? `${proxyHost}/api/v1` : '/api/v1')

/** Instancia sin interceptores: para refrescar tokens (evita recursión). */
const rawHttp: AxiosInstance = axios.create({ baseURL: BASE_URL })

const http: AxiosInstance = axios.create({ baseURL: BASE_URL })

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  config.headers.set('X-Request-Id', nuevoRequestId())
  const access = useAuthStore.getState().accessToken
  if (access) {
    config.headers.set('Authorization', `Bearer ${access}`)
  }
  return config
})

let refreshing: Promise<string> | null = null

async function refreshAccess(): Promise<string> {
  const refreshToken = useAuthStore.getState().refreshToken
  if (!refreshToken) throw new Error(tFuera('errores.sesion'))

  if (!refreshing) {
    refreshing = (async () => {
      try {
        const { data } = await rawHttp.post<{ data: TokenResponse }>('/auth/refresh', { refreshToken })
        const t = data.data
        useAuthStore.getState().setTokens(t.accessToken, t.refreshToken)
        return t.accessToken
      } finally {
        refreshing = null
      }
    })()
  }
  return refreshing
}

http.interceptors.response.use(
  (res) => res,
  async (error: AxiosError<ApiErrorBody>) => {
    const original = error.config
    if (!original) throw transformar(error)

    const es401 =
      error.response?.status === 401 ||
      error.response?.data?.codigo === 'TOKEN_EXPIRADO' ||
      error.response?.data?.codigo === 'CREDENCIALES_INVALIDAS'

    const puedeReintentar =
      es401 && !original.url?.includes('/auth/refresh') && !original.url?.includes('/auth/login')

    if (puedeReintentar && !(original as InternalAxiosRequestConfig & { _retried?: boolean })._retried) {
      const config = original as InternalAxiosRequestConfig & { _retried?: boolean }
      config._retried = true
      try {
        const nuevoToken = await refreshAccess()
        config.headers.set('Authorization', `Bearer ${nuevoToken}`)
        return await http(config)
      } catch {
        useAuthStore.getState().clearSession()
        return Promise.reject(
          new ApiError({
            success: false,
            data: null,
            errorCode: 401,
            codigo: 'TOKEN_EXPIRADO',
            errorMessage: tFuera('errores.sesionExpirada'),
          }),
        )
      }
    }
    throw transformar(error)
  },
)

function transformar(error: AxiosError<ApiErrorBody>): Error {
  if (error.response?.data && typeof error.response.data.codigo === 'string') {
    return new ApiError(error.response.data)
  }
  if (!error.response) {
    return new Error(tFuera('errores.servidor'))
  }
  return new Error(tFuera('errores.inesperado', { status: error.response.status }))
}

export default http