# syntax=docker/dockerfile:1.7
# ============================================================================
# Ferretería "El Tornillo Feliz" — Frontend (Vite/React 19)
# ----------------------------------------------------------------------------
# Multi-stage: bun para build → nginx alpine para servir + proxy /api/.
#
# Tamaños esperados:
#   - build stage:  ~600 MB (bun + node_modules)
#   - runtime:      ~50 MB (nginx alpine + dist)
#
# Notas:
#   - Vite bakea las variables VITE_* en build time (no se necesitan en runtime).
#   - El baseURL por defecto es `/api/v1` (relativo); nginx en este contenedor
#     hace reverse-proxy a ferreteria-backend:8080 dentro de la red Docker.
# ============================================================================

# ----------------------------------------------------------------------------
# Stage 1: build
# ----------------------------------------------------------------------------
FROM oven/bun:1-alpine AS build
WORKDIR /app

# ─── Manejo del .env ────────────────────────────────────────────────────────
# Vite lee automáticamente `.env` al ejecutar `bun run build`. Las variables
# con prefijo VITE_* se BAKEAN en el bundle JS (es decir, viajan al browser).
# NO se necesitan en runtime: el stage final (nginx) solo recibe /app/dist.
#
# Por eso .env está explícitamente en la primera capa: si lo editas, se
# invalida esta capa y se rebuildea el bundle sin invalidar las capas de
# dependencias (que son las más pesadas). Más rápido de iterar.
#
# Producción/Docker: las build args VITE_OTEL_ENABLED=true +
# VITE_OTEL_EXPORTER_OTLP_ENDPOINT activan la telemetría del browser. Si no
# se pasan, se usan los valores del .env (que por defecto tiene OTel apagado
# en dev local).
# ─────────────────────────────────────────────────────────────────────────────
ARG VITE_OTEL_ENABLED=false
ARG VITE_OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
ARG VITE_APP_VERSION=0.0.0
ENV VITE_OTEL_ENABLED=$VITE_OTEL_ENABLED \
    VITE_OTEL_EXPORTER_OTLP_ENDPOINT=$VITE_OTEL_EXPORTER_OTLP_ENDPOINT \
    VITE_APP_VERSION=$VITE_APP_VERSION

COPY package.json bun.lock .env* ./
RUN --mount=type=cache,target=/root/.bun/install/cache \
    bun install --frozen-lockfile

# Capa 2: el resto del código fuente. Esta capa se invalida ante CUALQUIER
# cambio de archivo, pero las anteriores quedan cacheadas.
COPY . .

# Build de producción. Importante: el .env se bakea aquí; lo que no esté
# como prefijo VITE_* no llega al bundle.
RUN bun run build

# Validación: el bundle tiene que existir (falla rápido si vite no produjo nada)
RUN test -f dist/index.html || (echo "ERROR: dist/index.html no se generó" && exit 1)

# ----------------------------------------------------------------------------
# Stage 2: runtime (nginx)
# ----------------------------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

# wget para el healthcheck, tzdata para logs en hora local
RUN apk add --no-cache wget tzdata && \
    cp /usr/share/zoneinfo/America/Mexico_City /etc/localtime && \
    echo "America/Mexico_City" > /etc/timezone

# Quitamos la config default de nginx (welcome page)
RUN rm -f /etc/nginx/conf.d/default.conf

# Reverse proxy + SPA fallback
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Artefactos del build
COPY --from=build /app/dist /usr/share/nginx/html

# Nginx ya corre como usuario nginx (no root). No necesitamos crear user.

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=5 \
  CMD wget -qO /dev/null http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
