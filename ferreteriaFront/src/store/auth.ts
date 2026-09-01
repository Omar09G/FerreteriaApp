import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'

import type { MeResponse, TokenResponse } from '@/lib/api/types'

interface AuthState {
  /**
   * Marcador de "estoy autenticado". El access y refresh tokens viven en
   * cookies HttpOnly (browser-only) y JS NO puede leerlos: aquí solo
   * guardamos lo que necesitamos para el UX (perfil + estado de actividad).
   */
  autenticado: boolean
  usuario: MeResponse | null
  /** Marca de la última interacción del usuario (epoch ms). */
  lastActivityAt: number
  setSession: (token: TokenResponse) => void
  setTokens: (accessToken: string | null, refreshToken: string | null) => void
  clearSession: () => void
  pingActivity: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      autenticado: false,
      usuario: null,
      lastActivityAt: Date.now(),
      setSession: (token) =>
        set({
          autenticado: Boolean(token.accessToken),
          usuario: token.usuario,
          lastActivityAt: Date.now(),
        }),
      // Los tokens no se persisten: viajan en cookies HttpOnly. Este método
      // solo actualiza el marcador de autenticación para compatibilidad con
      // flujos que lo invocan tras refresh.
      setTokens: (accessToken) =>
        set({ autenticado: Boolean(accessToken), lastActivityAt: Date.now() }),
      clearSession: () =>
        set({ autenticado: false, usuario: null, lastActivityAt: Date.now() }),
      pingActivity: () => set({ lastActivityAt: Date.now() }),
    }),
    {
      name: 'ferreteria-auth',
      storage: createJSONStorage(() => localStorage),
      // Solo persistimos `usuario` (perfil cacheado) y `autenticado` como
      // pista de UX. El access y refresh tokens NO se persisten: viven en
      // cookies HttpOnly y se revalidan contra el backend en cada mount.
      // Al recargar, si la cookie `at` sigue vigente, /auth/me responde 200
      // y `setSession` reactiva el flag; si expiró, /auth/me responde 401
      // y `clearSession` lo limpia.
      partialize: (state) => ({
        autenticado: state.autenticado,
        usuario: state.usuario,
      }),
    },
  ),
)

/** True si el store marca sesión activa. La fuente de verdad real es la
 * cookie HttpOnly: usar también un endpoint /auth/me para verificar. */
export function useAutenticado(): boolean {
  return useAuthStore((s) => s.autenticado)
}

export function tieneRol(roles: string[] | null | undefined, requerido?: string[]): boolean {
  if (!roles || roles.length === 0) return false
  if (!requerido || requerido.length === 0) return true
  return roles.some((r) => requerido.includes(r))
}

export function useTieneRol(requerido?: string[]): boolean {
  return tieneRol(useAuthStore((s) => s.usuario?.roles), requerido)
}
