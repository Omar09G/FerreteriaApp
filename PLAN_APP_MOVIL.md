# Plan: Front como App (Android / iPhone) — Ferretería El Tornillo Feliz

> Estado actual (validado 2026-09-11): el front es SPA web pura. Único rastro
> "app": `theme-color` en `ferreteriaFront/index.html:7`. No hay manifest,
> service worker, iconos PNG ni wrapper nativo.
> Skills revisados: no existe skill PWA/móvil/Capacitor en el catálogo
> (solo `ios-icon-gen`, útil como apoyo para generar iconos). Ruta derivada de
> evidencia del repo + restricciones de plataforma.

## Decisión de ruta

| | PWA instalable (recomendada) | Capacitor (APK/IPA) |
|---|---|---|
| Instalación | Desde el navegador, sin tiendas | APK directo / Play / App Store |
| Cuentas/costos | $0 | Play (una vez) / Apple USD 99 por año |
| Requiere Mac | No | Sí, para iOS |
| Updates | Automáticos al abrir | Reinstalar / tienda |
| Offline | App shell (datos siempre a red) | Igual |
| Hardware (cámara BT, impresora) | Web APIs según navegador | Plugins nativos |
| Bloqueador LAN | HTTPS local (mkcert) | Mismo + ajustes CORS/cookies |

**Ruta elegida: PWA + HTTPS local.** Cero costo, un solo código,
funciona en Android y iPhone. Capacitor solo si aparece requisito de
tienda, push nativo o hardware.

---

## FASE 0 — Puntos a validar ANTES de implementar (checklist)

- [ ] **V0. Inventario de dispositivos**: modelos Android/iOS y versiones
      en caja. Mínimo viable: Android 8+ (Chrome), iOS 16.4+ (Safari).
- [ ] **V1. Uso real en móvil**: ¿qué pantallas se usarán en teléfono
      (cobro, consulta stock) vs qué queda en PC (reportes, compras)?
      Define el alcance responsive.
- [ ] **V2. Red**: IP fija o reserva DHCP para `192.168.110.30`
      (hoy es dinámica; si cambia, la app instalada apunta a IP vieja).
- [ ] **V3. Decisión HTTPS**: instalar CA `mkcert` en cada dispositivo
      (2 min por equipo) SÍ/NO. Sin esto no hay PWA instalable,
      solo marcador web.
- [ ] **V4. Iconos fuente**: logo en alta resolución (SVG/PNG ≥1024)
      para generar 192/512/maskable/180. Si no existe, generarlo primero
      (apoyo: skill `ios-icon-gen`).
- [ ] **V5. Impresora de tickets**: ¿se imprime desde el móvil?
      Si sí, definir cómo (impresora WiFi con página de impresión web
      vs plugin nativo → eso forzaría Capacitor).

Si V3 = NO: abortar PWA, quedarse en web + "Agregar a inicio" como
marcador (sin standalone/offline). Si V5 = plugin nativo: ir a Ruta B.

---

## FASE 1 — PWA (pasos)

1. **Manifest** `ferreteriaFront/public/manifest.webmanifest`
   (`name`, `short_name`, `start_url: /`, `display: standalone`,
   `theme_color #c2410c`, iconos 192/512 + maskable) y
   `<link rel="manifest">` en `index.html`.
2. **Iconos**: `icon-192.png`, `icon-512.png`, `icon-maskable-512.png`,
   `apple-touch-icon-180.png` en `public/`.
3. **Service worker** con `vite-plugin-pwa` (estrategia):
   precache del app-shell; **`/api/*` = NetworkOnly**
   (datos de caja/cortes jamás desde caché); `/assets/*` cache-first.
4. **Validar que `/api` no se cachea**: prueba = vender sin red debe
   FALLAR con error de red controlado, nunca registrar venta vieja.
5. **nginx** (`ferreteriaFront/nginx.conf`): content-type del manifest,
   `Service-Worker-Allowed: /`, SW con `Cache-Control: no-cache`.
   Reconstruir imagen frontend y redesplegar.
6. **HTTPS LAN con mkcert**:
   `mkcert -install` (solo servidor) + `mkcert 192.168.110.30` →
   cert/key a nginx (puerto 443), redirigir 8080→443.
   Instalar `rootCA.pem` en cada Android/iPhone (una vez por equipo).
7. **Criterios de aceptación**:
   - Chrome Android muestra "Instalar app" y abre standalone sin URL.
   - Safari iOS (Compartir → pantalla de inicio) abre standalone.
   - Lighthouse PWA ≥ 90 (Chrome DevTools, contra la URL LAN https).
   - Login + venta + corte funcionan instalados; sin red la app abre
     y la API falla controlado (sin ventas fantasma).
8. **Rollback**: quitar `<link rel="manifest">` + rebuild = vuelta a web
   pura. La CA mkcert solo afecta equipos donde se instaló.

## FASE 2 — Ruta B Capacitor (solo si FASE 0 lo exige)

1. `npm i @capacitor/core @capacitor/cli`, `android`/`ios`,
   `capacitor.config.ts` con `server.url=https://192.168.110.30`.
2. Backend `CORS_ALLOWED_ORIGINS` += `capacitor://localhost`.
   Si se usa bundle local + API absoluta: además `VITE_API_URL`,
   y revisar cookies (`SameSite=None; Secure` + HTTPS obligatorio).
3. Android: `usesCleartextTraffic` (o mejor HTTPS ya resuelto),
   keystore, APK firmado → distribuir directo o Play.
4. iOS (requiere Mac + Xcode + Apple Developer):
   excepción `NSAppTransportSecurity` para la IP, TestFlight/App Store.
5. Aceptación: login con cookies en WebView, mismo flujo de caja que web.

---

## Orden de ejecución sugerido

V0→V5 (1 h, decisiones) → FASE 1 pasos 1–5 en HTTP (PWA lista pero no
instalable) → V3/mkcert (paso 6, la llave) → paso 7 aceptación.
