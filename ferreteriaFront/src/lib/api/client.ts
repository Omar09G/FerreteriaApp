import axios, { type AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'

import { tFuera } from '@/i18n'
import { useAuthStore } from '@/store/auth'
import { apiRefresh } from '@/lib/api/endpoints'
import { env } from '@/config/env'
import type { ApiErrorBody } from '@/lib/api/types'

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

const apiUrl = env.apiUrl
const proxyHost = env.apiProxy
const BASE_URL = apiUrl || (env.devSinProxy ? `${proxyHost}/api/v1` : '/api/v1')

const http: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  // both access and refresh tokens live in HttpOnly cookies; the browser
  // attaches them automatically on same-origin requests.
  withCredentials: true,
  // Timeout por intento individual: sin esto, una conexión colgada
  // bloquearía el ciclo de reintentos indefinidamente.
  timeout: env.apiTimeoutMs,
})

/** Lee el valor de una cookie por nombre. Devuelve null si no existe. */
function readCookie(name: string): string | null {
  if (typeof document === 'undefined') return null
  const prefix = `${encodeURIComponent(name)}=`
  const parts = document.cookie ? document.cookie.split(';') : []
  for (const raw of parts) {
    const c = raw.trim()
    if (c.startsWith(prefix)) {
      return decodeURIComponent(c.substring(prefix.length))
    }
  }
  return null
}

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const MUTATING_METHODS = new Set(['post', 'put', 'patch', 'delete'])

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  config.headers.set('X-Request-Id', nuevoRequestId())
  // CSRF double-submit: para métodos que mutan estado, copiamos el valor de
  // la cookie XSRF-TOKEN (no HttpOnly, JS-readable) en el header
  // X-XSRF-TOKEN. Spring lo valida contra la cookie.
  const method = (config.method ?? 'get').toLowerCase()
  if (MUTATING_METHODS.has(method)) {
    const csrf = readCookie(CSRF_COOKIE)
    if (csrf) {
      config.headers.set(CSRF_HEADER, csrf)
    }
  }
  return config
})

let refreshing: Promise<string> | null = null

async function refreshAccess(): Promise<string> {
  if (!refreshing) {
    refreshing = (async () => {
      try {
        const t = await apiRefresh()
        return t.accessToken
      } finally {
        refreshing = null
      }
    })()
  }
  return refreshing
}

interface RetryMeta {
  /** cuántos reintentos van consumidos en esta request */
  retries: number
  /** ya se intentó refresh+retry una vez para esta request */
  refreshed: boolean
  /** ya se intentó re-leer el CSRF token una vez para esta request */
  csrfRefreshed: boolean
}

function sleep(ms: number): Promise<void> {
  return new Promise((res) => window.setTimeout(res, ms))
}

/**
 * ¿Tiene sentido reintentar este error?
 * - Sin respuesta: red caída / timeout / CORS → sí.
 * - 5xx: backend sobrecargado / caído → sí.
 * - 4xx: input inválido / sin permisos → no, reintentar solo retrasa el
 *   feedback sin cambiar el resultado (excepto el caso 401 manejado abajo).
 */
function isRetryable(error: AxiosError): boolean {
  if (!error.response) return true
  return error.response.status >= 500
}

http.interceptors.response.use(
  (res) => res,
  async (error: AxiosError<ApiErrorBody>) => {
    const original = error.config as
      | (InternalAxiosRequestConfig & { _retry?: RetryMeta })
      | undefined
    if (!original) throw transformar(error)

    const meta: RetryMeta = original._retry ?? {
      retries: 0,
      refreshed: false,
      csrfRefreshed: false,
    }

    // ── 0) CSRF mismatch: re-leer el token una vez y reintentar ──
    // El XSRF-TOKEN puede haber rotado entre requests; si la cookie aún
    // tiene valor, copiar el nuevo al header suele bastar. Si no, el caller
    // debe llamar /csrf-init antes del próximo mutating call.
    const esCsrf =
      (error.response?.status === 403 &&
        error.response?.data?.codigo === 'CSRF_TOKEN_INVALID') ||
      // Spring suele devolver 403 sin cuerpo para CSRF; cualquier 403 en
      // un mutating method sin mensaje específico lo tratamos como CSRF.
      (error.response?.status === 403 && MUTATING_METHODS.has(
        (original.method ?? 'get').toLowerCase()))

    if (esCsrf && !meta.csrfRefreshed) {
      meta.csrfRefreshed = true
      original._retry = meta
      const csrf = readCookie(CSRF_COOKIE)
      if (csrf) {
        original.headers.set(CSRF_HEADER, csrf)
        return http(original)
      }
    }

    // ── 1) Reintento con backoff exponencial (red / 5xx) ──
    if (isRetryable(error) && meta.retries < env.apiMaxRetries) {
      meta.retries += 1
      original._retry = meta
      const backoff = env.apiRetryBackoffMs * Math.pow(2, meta.retries - 1)
      await sleep(backoff)
      return http(original)
    }

    // ── 2) 401 / token expirado: refresh + un reintento ──
    const es401 =
      error.response?.status === 401 ||
      error.response?.data?.codigo === 'TOKEN_EXPIRADO' ||
      error.response?.data?.codigo === 'CREDENCIALES_INVALIDAS'

    const puedeRefreshear =
      es401 &&
      !meta.refreshed &&
      !original.url?.includes('/auth/refresh') &&
      !original.url?.includes('/auth/login')

    if (puedeRefreshear) {
      meta.refreshed = true
      original._retry = meta
      try {
        await refreshAccess()
        // El browser ya rotó la cookie `at` automáticamente. Re-leemos
        // CSRF por si también rotó (defensivo).
        const csrf = readCookie(CSRF_COOKIE)
        if (csrf) original.headers.set(CSRF_HEADER, csrf)
        return http(original)
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

/**
 * Garantiza que la cookie XSRF-TOKEN exista. Llamar al montar la app antes
 * del primer mutating request (incluido /auth/login). Si el token aún no
 * está, hace un GET a /csrf-init que el backend aprovecha para emitir la
 * cookie.
 */
export async function ensureCsrfCookie(): Promise<void> {
  if (readCookie(CSRF_COOKIE)) return
  try {
    await http.get('/auth/csrf-init')
  } catch {
    // best-effort: si falla, el siguiente mutating request obtendrá 403 y
    // el caller verá el error. Pero no bloqueamos el arranque por esto.
  }
}
