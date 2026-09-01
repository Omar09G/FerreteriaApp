import type { ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { useAutenticado, tieneRol, useAuthStore } from '@/store/auth'
import { AccessDenied } from '@/components/errors/PageStates'

/** Requiere sesión activa; si no, redirige a /login conservando el destino. */
export function RequiereAuth() {
  const autenticado = useAutenticado()
  const location = useLocation()
  if (!autenticado) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return <Outlet />
}

/** Requiere que el usuario tenga al menos uno de los roles indicados. */
export function RequiereRol({ roles, children }: { roles: string[]; children?: ReactNode }) {
  const rolesUsuario = useAuthStore((s) => s.usuario?.roles)
  if (!tieneRol(rolesUsuario, roles)) {
    return children ?? <AccessDenied />
  }
  return children ?? <Outlet />
}

/** /login redirige al dashboard si ya hay sesión. */
export function RedirigirSiAutenticado({ children }: { children: ReactNode }) {
  const autenticado = useAutenticado()
  if (autenticado) return <Navigate to="/dashboard" replace />
  return children
}
