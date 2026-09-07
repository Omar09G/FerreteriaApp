

import { Navigate } from "react-router-dom";

import { withFallback } from "@/router/builder";

const ProductosPage = withFallback(() => import("@/features/catalogo/ProductosPage"));
const ClientesPage = withFallback(() => import("@/features/catalogo/ClientesPage"));
const PromocionesPage = withFallback(() => import("@/features/catalogo/PromocionesPage"));
const ProveedoresPage = withFallback(() => import("@/features/catalogo/ProveedoresPage"));
const CatalogosIndexPage = withFallback(() => import("@/features/catalogo/CatalogosIndexPage"));
const CatalogoCrudPage = withFallback(() => import("@/features/catalogo/CatalogoCrudPage"));

export const catalogoChildren = [
  { path: "productos", element: <ProductosPage /> },
  { path: "clientes", element: <ClientesPage /> },
  { path: "promociones", element: <PromocionesPage /> },
  { path: "proveedores", element: <ProveedoresPage /> },
];

export const catalogosChildren = [
  { index: true, element: <CatalogosIndexPage /> },
  { path: ":clave", element: <CatalogoCrudPage /> },
];

export const catalogoRoutes = [
  { path: "catalogo", children: catalogoChildren },
  { path: "catalogos", children: catalogosChildren },
  // Alias legacy: AppShell apuntaba a /compras/proveedores antes de 2026-09-07
  { path: "compras/proveedores", element: <Navigate to="/catalogo/proveedores" replace /> },
];