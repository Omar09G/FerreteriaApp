import { lazy, Suspense, type ComponentType, type ReactNode } from "react";
import type { RouteObject } from "react-router-dom";

import { spinners } from "@/components/router-utils";

/**
 * Helper para envolver un componente lazy en Suspense + fallback consistente.
 * Centraliza el fallback (skeleton spinner) para no repetir <Suspense fallback={spinners.full}>
 * en cada ruta. Si una feature necesita skeleton propio, puede pasarlo via
 * withFallback(Component, skeleton).
 */
export function withFallback<P extends object>(
  factory: () => Promise<{ default: ComponentType<P> }>,
  fallback: ReactNode = spinners.full,
): ComponentType<P> {
  const Lazy = lazy(factory);
  const Wrapped = (props: P) => (
    <Suspense fallback={fallback}>{Lazy && <Lazy {...(props as object & P)} />}</Suspense>
  );
  Wrapped.displayName = "withFallback";
  return Wrapped as ComponentType<P>;
}

/**
 * Convierte un path a una RouteObject lista para createBrowserRouter.
 * Uso:
 *   const routes = [
 *     { path: "/login", element: <Login /> },
 *     ...children
 *   ];
 *   export const authRoutes = routes;
 */
export function defineRoutes(...routes: RouteObject[]): RouteObject[] {
  return routes;
}