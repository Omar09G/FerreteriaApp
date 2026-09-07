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
import { Outlet, createBrowserRouter } from "react-router-dom";
import { Navigate, useLocation } from "react-router-dom";

import { NotFound } from "@/components/errors/PageStates";
import { AppShell } from "@/components/layout/AppShell";
import { useAutenticado } from "@/store/auth";
import { ToastProvider } from "@/components/ui/Toast";

import { publicRoutes, privateRoutes } from "./registry";

/**
 * Layout raiz: Toast + AppShell. AppShell ya contiene su propio <Outlet />
 * dentro de <main> (linea 551), por lo que aqui no se añade otro Outlet.
 * Suspense para lazy pages vive dentro de AppShell alrededor de su Outlet.
 */
function ShellPrivada() {
  return (
    <ToastProvider>
      <AppShell />
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

export const router = createBrowserRouter([
  // Rutas publicas (login, redirect /) — sin AppShell, sin auth
  ...publicRoutes,
  // Rutas privadas: layout de auth -> Shell (AppShell + Suspense) -> rutas
  {
    element: <RequiereAuth />,
    children: [
      {
        element: <ShellPrivada />,
        children: privateRoutes,
      },
    ],
  },
  { path: "*", element: <NotFound /> },
]);