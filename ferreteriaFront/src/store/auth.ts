import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'

import type { MeResponse, TokenResponse } from '@/lib/api/types'

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  usuario: MeResponse | null
  setSession: (token: TokenResponse) => void
  setTokens: (accessToken: string, refreshToken: string) => void
  clearSession: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      usuario: null,
      setSession: (token) =>
        set({ accessToken: token.accessToken, refreshToken: token.refreshToken, usuario: token.usuario }),
      setTokens: (accessToken, refreshToken) => set({ accessToken, refreshToken }),
      clearSession: () => set({ accessToken: null, refreshToken: null, usuario: null }),
    }),
    {
      name: 'ferreteria-auth',
      storage: createJSONStorage(() => localStorage),
    },
  ),
)

export function tieneRol(roles: string[] | null | undefined, requerido?: string[]): boolean {
  if (!roles || roles.length === 0) return false
  if (!requerido || requerido.length === 0) return true
  return roles.some((r) => requerido.includes(r))
}

export function useTieneRol(requerido?: string[]): boolean {
  return tieneRol(useAuthStore((s) => s.usuario?.roles), requerido)
}