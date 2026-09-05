

import { withFallback } from "@/router/builder";

const ProductosPage = withFallback(() => import("@/features/catalogo/ProductosPage"));
const ClientesPage = withFallback(() => import("@/features/catalogo/ClientesPage"));
const PromocionesPage = withFallback(() => import("@/features/catalogo/PromocionesPage"));
const ProveedoresPage = withFallback(() => import("@/features/catalogo/ProveedoresPage"));
const CatalogosIndexPage = withFallback(() => import("@/features/catalogo/CatalogosIndexPage"));

export const catalogoChildren = [
  { index: true, element: <CatalogosIndexPage /> },
  { path: "productos", element: <ProductosPage /> },
  { path: "clientes", element: <ClientesPage /> },
  { path: "promociones", element: <PromocionesPage /> },
  { path: "proveedores", element: <ProveedoresPage /> },
];

export const catalogoRoutes = [{ path: "catalogo", children: catalogoChildren }];