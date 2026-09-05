/**
 * Ensamblador del React Router.
 *
 * Las rutas estan registradas en `src/router/registry.ts` por feature. Este archivo
 * solo combina los arrays (publicas + privadas con AppShell), configura el layout
 * raiz (AppShell + ToastProvider + Suspense), y exporta el router.
 *
 * Fast Refresh desactivado: el elemento raiz combina varios boundary providers.
 */
/* eslint-disable react-refresh/only-export-components */
import { Suspense } from "react";
import { Outlet, createBrowserRouter, type RouteObject } from "react-router-dom";
import { Navigate, useLocation } from "react-router-dom";

import { spinners } from "@/components/router-utils";
import { NotFound } from "@/components/errors/PageStates";
import { AppShell } from "@/components/layout/AppShell";
import { useAutenticado } from "@/store/auth";
import { ToastProvider } from "@/components/ui/Toast";

import { publicRoutes, privateRoutes } from "./registry";

/**
 * Layout raiz: Toast + AppShell + Suspense para cualquier ruta privada.
 * AppShell no acepta children, asi que envolvemos su contenido en un wrapper
 * Outlet-friendly.
 */
function ShellPrivada() {
  return (
    <ToastProvider>
      <AppShell />
      {/* AppShell renderiza su propio sidebar; las rutas se montan via outlet en
          su layout interno. Suspense fuera de AppShell para no romper su chrome. */}
      <Suspense fallback={spinners.full}>
        <Outlet />
      </Suspense>
    </ToastProvider>
  );
}

/** Layout route: gate de autenticacion antes de cualquier ruta privada. */
function RequiereAuth() {
  const autenticado = useAutenticado();
  const location = useLocation();
  if (!autenticado) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <Outlet />;
}

/** Convierte una ruta en layout-route protegido (route.element como children). */
function protegida<T extends RouteObject>(ruta: T): T {
  return { ...ruta, element: <Outlet /> } as unknown as T;
}

export const router = createBrowserRouter([
  // Rutas publicas (login, redirect /) — sin AppShell, sin auth
  ...publicRoutes,
  // Rutas privadas: layout de auth -> Shell (AppShell + Suspense) -> rutas
  {
    element: <RequiereAuth />,
    children: [
      {
        element: <ShellPrivada />,
        children: privateRoutes.map(protegida),
      },
    ],
  },
  { path: "*", element: <NotFound /> },
]);