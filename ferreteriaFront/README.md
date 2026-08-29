# ferreteria-front — SPA de la Ferretería (React 19 + Vite 8 + TS)

Frontend del Sistema Integral de Ferretería: SPA de escritorio interna (caja, inventario,
compras, reportes) responsive para tablet. Documento fuente: [`docs/PLAN_FRONTEND.md`](docs/PLAN_FRONTEND.md).
Backend: [`../ferreteriaBackend`](../ferreteriaBackend) · Base de datos: [`../ferreteriaDB`](../ferreteriaDB).

## Stack

React 19 + TypeScript 6 + Vite 8 (React Compiler activo) · react-router-dom v7 ·
@tanstack/react-query v5 · axios (interceptors) · zustand + persist · Tailwind CSS v4 ·
lucide-react · recharts.

## Requisitos

Node (con `bun` opcional) y el backend corriendo (default `http://localhost:8080`).

## Arranque rápido

```bash
npm install        # o: bun install
npm run dev        # Vite, http://localhost:5173 — proxy /api → http://localhost:8080 (sin CORS en backend)
npm run build      # tsc -b && vite build → dist/
npm run lint       # ESLint
npm run preview    # sirve dist/ localmente
```

### Variables de entorno

| Variable | Default | Uso |
|---|---|---|
| `VITE_API_URL` | `/api` | Base de la API (todas las rutas parten de `/api/v1`) |
| Vite dev proxy | `/api → http://localhost:8080` | En dev no se configura CORS en el backend |

## Arquitectura

```
src/
├─ features/       # módulos por dominio: pos, caja, ventas, compras, inventario,
│                  #   catalogo, reportes, rrhh, seguridad, fiscal, dashboard, auth
├─ lib/api/        # clientes axios por módulo + endpoints.ts + types.ts (contratos)
├─ router/         # rutas con guards por rol (roles: ADMINISTRADOR, GERENTE, ...)
├─ store/          # zustand: sesión (tokens en localStorage) y estado de UI
├─ components/     # UI kit propio (design system de ferretería + Tailwind)
└─ hooks/          # useToast, useDocumentTitle, etc.
```

## Contratos con el backend (resumen)

- Envelope: éxito `{ success, data?, meta? }`, error `{ success, errorCode, codigo,
  errorMessage, details? }`. `success===false` → `ApiError`; `CREDENCIALES_INVALIDAS` /
  `TOKEN_EXPIRADO` manejan sesión; el resto → toast.
- Enviar `X-Request-Id` (UUID) en cada request; el backend lo ecoa en `errorMessage`.
- Auth: `POST /api/v1/auth/login` + refresh rotativo (access 8h / refresh 72h). El
  interceptor refresca con mutex y reintenta una vez; si falla → logout local →
  `/login?expired=1`.
- Fechas: `LocalDate` `yyyy-MM-dd` (input `date`); moneda: `Intl.NumberFormat("es-MX", MXN)`.
- Reportes y cortes por rango de fechas (default = hoy).