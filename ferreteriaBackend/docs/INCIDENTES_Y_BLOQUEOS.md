# Registro de Incidencias y Bloqueos — Backend Ferretería

> Mapeo de errores reales ocurridos durante M0 y la integración BD↔backend.
> Propósito: ante un síntoma parecido, consultar aquí la causa raíz probada y su
> solución antes de depurar de cero. Formato: **Síntoma → Causa raíz → Solución → Prevención**.

---

## A. Integración BD canónica ↔ Flyway/backend

### INC-01 · Flyway: `syntax error at or near "\"`
| | |
|---|---|
| **Síntoma** | `V1__base.sql failed … syntax error at or near "\"` |
| **Causa raíz** | Meta-comandos psql que Flyway (JDBC) no soporta: `\connect` (línea completa), `\ir` (include) y `\gexec` **pegado al final de un SELECT** — este último invisible para un `grep '^\\'`. El `\gexec` actuaba como terminador del SELECT; al eliminar solo el comando quedaba una sentencia sin `;` que arrastraba el siguiente statement |
| **Solución** | Tarea `generateMigrations`: elimina `\connect`, empata el contenido de `vistas_core.sql` en lugar del `\ir`, neutraliza `\g(exec|set)` en línea y **elimina el bloque completo** `SELECT format('CREATE DATABASE…')\gexec` (bajo Flyway ya se corre dentro de la BD objetivo) |
| **Prevención** | Prohibido añadir meta-comandos psql a los scripts canónicos. Si algún día se necesita lógica condicional, usar PL/pgSQL (`DO $$ … $$`) que JDBC sí ejecuta |

### INC-02 · `trigger "trg_audit_producto" already exists` en Flyway
| | |
|---|---|
| **Síntoma** | V1 fallaba por trigger duplicado aunque el archivo lo definía UNA sola vez; además sobrevivían triggers entre corridas |
| **Causa raíz** | **Falso positivo de doble ejecución**: el reset manual era `DROP SCHEMA public CASCADE`, pero las tablas viven en los esquemas `cat/cfg/seg/inv/com/ven/fin/fis` — el "reset" dejaba toda la BD anterior intacta y Flyway (sin `flyway_schema_history`) re-ejecutaba DDL sobre objetos existentes |
| **Solución** | Reset real: `dropdb + createdb`. Además se blindó el canon: los 30 `CREATE TRIGGER` de `02_tablas.sql` llevan hoy `DROP TRIGGER IF EXISTS … ;` previo (idempotencia real) |
| **Prevención** | Para descartes usar SIEMPRE dropdb/createdb, nunca solo `public`. Los IT de Testcontainers arrancan contenedores nuevos (inmunes). Regla: si un error dice "already exists", primero verificar QUÉ esquema se reseteó |

### INC-03 · Acentos divergentes entre delta y original
| | |
|---|---|
| **Síntoma** | Al integrar ERRCODEs salían 11/12 parcheados sin explicación |
| **Causa raíz** | El original decía `'ya esta cerrado'` (sin tilde) y el delta generado desde la BD viva `'ya está cerrado'`; el patrón de búsqueda usaba tilde |
| **Solución** | Alternancia de patrones con ambas grafías + parche por línea |
| **Prevención** | Al generar deltas desde `pg_get_functiondef`, normalizar comparaciones sin acentos o buscar por subcadenas estables. Verificación final siempre por CONTEO (esperado N), nunca "pareció que quedó bien" |

### INC-04 · Perl insertó `$1/$2/$3` literales y se perdieron 2 triggers
| | |
|---|---|
| **Síntoma** | `syntax error at or near "$1"` al aplicar el script; inventario de triggers 28≠30 |
| **Causa raíz** | Escapado `\$1` dentro de comillas dobles de bash → perl recibió texto literal; y un `[^;]*` con DOTALL devoraba statements vecinos al capturar argumentos multilínea |
| **Solución** | Restaurar desde backup conocido-bueno y re-aplicar con **script Python máquina-de-estados línea a línea** (nunca cruza límites de statement); conteos asertados 12/12 + diff de inventario de triggers contra backup |
| **Prevención** | Transformaciones de SQL con regex multi-statement → preferir Python/estado explícito; SIEMPRE respaldar antes (`cp 02_backup.sql`) y verificar con `diff` estructural posterior |

### INC-05 · Elección de ERRCODE: evitar `P0001`
| | |
|---|---|
| **Contexto** | Contrato ERRCODE clase P0 (PLAN §4.3): P0100 stock, P0200/P0201 crédito, P030x turno, P040x promo, P0999 kardex |
| **Decisión** | `P0001` queda vetado: es el SQLSTATE default de todo `RAISE EXCEPTION` de PL/pgSQL — usarlo para "stock" colisionaría con cualquier raise sin contrato |
| **Prevención** | Nuevas reglas de negocio en BD deben reservar código del rango P0 y sumarlo a `DbErrorTranslator.BY_SQLSTATE` + bundles i18n + tabla §4.3 del PLAN |

---

## B. Runtime Spring (M0)

### INC-06 · Header `X-Request-Id` nunca aparecía en respuestas
| | |
|---|---|
| **Síntoma** | Health 200 OK pero sin header de correlación |
| **Causa raíz** | El filtro fijaba el header en `finally`, DESPUÉS de `chain.doFilter()` — la respuesta ya estaba commiteada y Tomcat ignora headers tardíos |
| **Solución** | Fijar `res.setHeader(...)` ANTES de invocar la cadena (el valor ya se conoce al entrar) |
| **Prevención** | Regla servlet: todo header de respuesta se escribe antes de tocar el body/chain |

### INC-07 · Mensaje de error en inglés siendo el default español
| | |
|---|---|
| **Síntoma** | STRICT sin `Accept-Language` respondía `"Missing X-Request-Id header…"` |
| **Causa raíz** | En filtro (fuera de MVC) `LocaleContextHolder` aún no existe → caía a `Locale.getDefault()` de la JVM (host en inglés) |
| **Solución** | `resolveLocale(request)`: `Accept-Language` explícito fuerza idioma; ausente ⇒ **es-MX fijo**, nunca JVM default |
| **Prevención** | Nunca depender de `Locale.getDefault()` en código de producto |

### INC-08 · `codigo` vacío en problema RFC 7807 escrito desde el filtro
| | |
|---|---|
| **Síntoma** | Body JSON sin la propiedad `codigo` |
| **Causa raíz** | Serializar `ProblemDetail` con un `ObjectMapper` plano: el aplanado de `getProperties()` depende del renderizador de Spring MVC, no ocurre fuera de él |
| **Solución** | Construir el mapa RFC7807 explícito (`LinkedHashMap`) en el filtro |
| **Prevención** | Objetos de framework serializados "a mano" requieren verificar anotaciones Jackson aplicables; si hay duda, mapa explícito |

### INC-09 · Scaffold previo chocando con el nuevo proyecto
| | |
|---|---|
| **Síntoma** | `gradle wrapper` intentaba aplicar plugin Spring Boot **4.1.1** desde un `build.gradle` Groovy que nadie de la sesión había escrito |
| **Causa raíz** | Directorio contenía un andamiaje anterior (Boot 4/Groovy + application.properties) conviviendo con los nuevos `.kts` — Gradle tomó el Groovy |
| **Solución** | Respaldo del scaffold viejo fuera del árbol y consolidación al `.kts` del plan |
| **Prevención** | Antes de scaffolding: listar `*.gradle*` y `src/main/resources/application.properties` residuales |

### INC-10 · Gradle incorrecto en PATH (9.7.1 en vez de 8.10.2)
| | |
|---|---|
| **Causa raíz** | La extensión vscjava/vscode-gradle del editor expone otro binario/daemon; el wrapper quedó apuntando a 9.7.1 |
| **Solución** | Generar wrapper invocando binario absoluto `/tmp/.../gradle-8.10.2/bin/gradle` y verificar `distributionUrl` + `./gradlew --version` |
| **Prevención** | Tras generar wrapper: `grep distributionUrl gradle/wrapper/gradle-wrapper.properties` SIEMPRE |

### INC-11 · Errores de compilación Kotlin DSL
| Síntoma | Corrección |
|---|---|
| `Unresolved reference: useJUnitPlatform` (con `withType<Test>()` dentro de `dependencies{}`) | Mover configuración de tareas FUERA de dependencies y usar `tasks.test { }` |
| `classesDirs.matching {}` inexistente en Kotlin DSL | `fileTree("build/classes/java/main") { exclude(...) }` en jacoco report/verification |
| XML de JaCoCo no se generaba | `reports { xml.required = true }` (Gradle 8 lo trae apagado) |

### INC-12 · Gate JaCoCo bloqueaba el build (esperado, pero por causas sutiles)
| Caso | Lección |
|---|---|
| `common.error` 55% | Los métodos `handle*` del advice no se cubren solos: probarlos invocándolos directo con `MockHttpServletRequest` |
| `common.i18n` clavado en 83% pese a tests nuevos | Clase `.class` OBSOLETA en `build/classes` tras mover `I18nConfig.java` **sin actualizar su declaración `package`** — el archivo cambió de carpeta pero seguía compilando al paquete viejo. Corregir package + `clean` |
| Regla general | Cobertura baja persistente ⇒ revisar XML de JaCoCo por método antes de "agregar tests a ciegas"; clases de cableado (`common/config/**`, `demo/**`, `*Application*`) están EXCLUIDAS por diseño |

### INC-13 · Compilación de tests: APIs mal recordadas
| Error | Corrección |
|---|---|
| `DescribedPredicate is not a functional interface` (ArchUnit) | Subclase anónima con `@Override test(...)` |
| `JavaCall` no encontrado | Paquete real: `com.tngtech.archunit.core.domain.JavaCall` |
| `getRawArgumentTypes()` no existe | `call.getTarget().getRawParameterTypes()` |
| `trimStart()` en Java | Es Kotlin; Java = `stripLeading()` |
| `BASE_URI/slug` inaccesibles entre paquetes | Elevar a `public static` cuando son parte del contrato compartido |

### INC-14 · Sesión de shell: procesos y CWD
| Síntoma | Causa | Práctica adoptada |
|---|---|---|
| `Unable to access jarfile` intermitente | Cada llamada puede iniciar en CWD base de sesión, no en el último usado | Rutas absolutas o parámetro `workdir` explícito en TODO comando sensible |
| Comandos morían a los 60/300 s sin salida | `pkill -f <patrón>` matcheó el PROPIO bash (el patrón vive en su cmdline) y mató la sesión | Patrón con corchete `"[j]ava.*SNAPSHOT.jar"` o matar por PID guardado en archivo (`app.pid`) |
| App background moría al terminar el comando | Sin `setsid`/`disown`, el grupo de procesos moría con la sesión | `( export …; java -jar … >log 2>&1 & echo $! > pid )` + `disown` |

### INC-15 · Contenedor postgres: permisos y stdin
| Síntoma | Causa | Solución |
|---|---|---|
| `initdb: cannot be run as root` aun con `-u postgres` | Podman ROOTLESS: uid mapeados; directorios creados por "root del contenedor" no escribibles por uid 999 | Preparar dirs como root + `chown postgres:postgres` + ejecutar vía `gosu postgres` |
| psql no recibía el script | Falta flag `-i` en `podman exec` (stdin cerrado) | Siempre `podman exec -i` para pipelines/heredocs |

---

## D. Envelope response (fin M1)

### INC-16 · Envelope inconsistente: GlobalExceptionHandler devolvía ProblemDetail mientras filtros usaban envelope
| | |
|---|---|
| **Síntoma** | 7 tests fallan tras rewrite del envelope: `AuthControllerTest` (5), `RestAuthEntryPointTest` (2). `EnvelopeAdvice` envolvía `ProblemDetail` de error en `{success:true, data:<error>}` |
| **Causa raíz** | Rewriting parcial: `RestAuthEntryPoint` y `RequestIdFilter` cambiaron a envelope directo en el stream, pero `GlobalExceptionHandler` seguía devolviendo `ResponseEntity<ProblemDetail>`. `EnvelopeAdvice` (ResponseBodyAdvice) interceptaba el ProblemDetail y lo envolvía en `{success:true, data:…}` porque `ProblemDetail` no es `Map` — no matcheaba la guarda `instanceof Map` |
| **Solución** | Reescritura completa de `GlobalExceptionHandler.build()` para devolver `Map<String, Object>` con envelope (`{success:false, errorCode, codigo, errorMessage, requestId, instance}`). Ahora `EnvelopeAdvice` lo reconoce como Map con "success" y lo deja pasar. `RequestIdFilter.writeProblem()` también cambia a envelope. `RestAuthEntryPoint` limpiado (eliminado `resolveLocale` duplicado). `EnvelopeAdviceTest` nuevo cubre ramas Map/Page/null |
| **Prevención** | Cuando se define un formato de respuesta estándar (ADR-005), TODOS los puntos de salida deben cambiarse en un solo commit: `GlobalExceptionHandler`, `RequestIdFilter`, `RestAuthEntryPoint`, y tests. Nunca hacer rewrite parcial |

### INC-17 · JaCoCo: `common.web` cae a 82% por ramas no cubiertas en EnvelopeAdvice
| | |
|---|---|
| **Síntoma** | `jacocoTestCoverageVerification` falla: `common.web` instructions 82% < 85% mínimo |
| **Causa raíz** | `EnvelopeAdvice.beforeBodyWrite` tenía 3 ramas (Map-con-success, Page, default) y solo la default estaba cubierta. Las ramas de `instanceof Map` e `instanceof Page` no se ejercitaban |
| **Solución** | Nuevo `EnvelopeAdviceTest` con 4 tests: wrapsPlainObject, errorMapPassesThrough, pageExtractsContentAndMeta, nullBodyWrapped |
| **Prevención** | Al agregar Advice con ramas, crear test unitario que ejecute cada rama del `beforeBodyWrite` |

---

## E. Inventario (M3)

### INC-18 · Kardex append-only: backend solo INSERT, nunca UPDATE/DELETE
| | |
|---|---|
| **Síntoma** | Si un servicio de inventario intenta UPDATE/DELETE sobre `inv.movimientos_inventario`, PostgreSQL lanza ERRCODE `P0999` → 409 `KARDEX_APPEND_ONLY` |
| **Causa raíz** | Invariante de negocio: el kardex es historial inmutable. Un trigger en BD lanza `RAISE EXCEPTION USING ERRCODE = 'P0999'` si detecta UPDATE/DELETE |
| **Solución** | `MovimientoService` solo tiene `create()` (INSERT). No existe `update()` ni `delete()`. El controlador expone `POST /movimientos` y `GET /movimientos`. Los tests verifican que el servicio solo llama `repo.save()` |
| **Prevención** | Nunca agregar métodos de UPDATE/DELETE a `MovimientoInventarioRepository` expuestos vía servicio. Si se necesita corrección, crear un nuevo movimiento de tipo ENTRADA/SALIDA con nota explicativa |

### INC-19 · Traslados requieren 2 movimientos en misma transacción
| | |
|---|---|
| **Síntoma** | Transferir producto de almacén A a B requiere SALIDA de A + ENTRADA de B atómicamente |
| **Causa raíz** | Si una falla deja solo la SALIDA, el stock queda inconsistente entre almacenes |
| **Solución** | `TrasladoService.create()` es `@Transactional`: guarda Traslado + detalles + 2 MovimientoInventario (SALIDA del origen, ENTRADA al destino) en una sola transacción |
| **Prevención** | Nunca separar la creación de ambos movimientos en transacciones distintas |

---

## F. POS (M4)

### INC-20 · Checkout POS: backend solo INSERT, BD calcula todo
| | |
|---|---|
| **Síntoma** | El backend inserta venta header + detalles + pagos, pero los totales (subtotal/IVA/total), folio, cuenta_cobrar, y validación de crédito los calcula PostgreSQL via triggers |
| **Causa raíz** | Arquitectura "regla de oro": Java orquesta, BD posee invariantes. Los triggers `fn_recalc_totales_venta`, `fn_detalle_valida_stock`, `fn_detalle_genera_salida`, `fn_pago_cliente_post` ejecutan toda la lógica de negocio |
| **Solución** | `VentaService.checkout()` ejecuta: 1) validate almacen/formaPago existen, 2) save venta header, 3) save detalles (trigger inserta kardex SALIDA + decrementa stock), 4) save pagos (trigger actualiza cuenta_cobrar), 5) flush + re-read para obtener totales calculados |
| **Prevención** | Nunca calcular subtotal/IVA/total en Java. Siempre hacer flush + re-read para retornar valores calculados por BD. Los errores P0100 (stock insuficiente) y P0200/P0201 (crédito excedido/no disponible) son capturados por DbErrorTranslator como HTTP 409/422 |

### INC-21 · Cotizaciones: conversión a venta es UPDATE, no re-creación
| | |
|---|---|
| **Síntoma** | Convertir cotización a venta solo actualiza `estado=CONVERTIDA` en la cotización; la venta se crea por separado con el ID de cotización como referencia |
| **Causa raíz** | Las cotizaciones y ventas son entidades independientes. La FK circular `cotizacion.venta_generada_id` ↔ `venta.cotizacion_id` se resuelve con ALTER TABLE tardío |
| **Solución** | `CotizacionService.convertirAVenta()` solo cambia el estado. El flujo de venta POST acepta un `cotizacionId` opcional para referencia |
| **Prevención** | No intentar insertar venta + cotización en la misma transacción. La cotización es un presupuesto, no un commitment |

### INC-22 · Devoluciones re-ingresan stock vía trigger
| | |
|---|---|
| **Síntoma** | Insertar en `ven.devolucion_detalles` automáticamente incrementa stock en `inv.inventario` sin que Java haga nada |
| **Causa raíz** | Trigger `fn_devolucion_detalle_post` llama a `inv.fn_registrar_movimiento` con tipo=ENTRADA, motivo=DEVOLUCION_VENTA |
| **Solución** | `DevolucionService.create()` solo inserta cabecera + detalles. El stock se re-ingresa automáticamente. Java no necesita lógica de inventario para devoluciones |
| **Prevención** | Nunca duplicar la lógica de re-ingreso de stock en Java |

---

## G. Mapa rápido síntoma → incidencia

| Si ves esto… | Revisa primero |
|---|---|
| `syntax error at or near "\"` en migración | INC-01 |
| `… already exists` en Flyway | INC-02 (reset incompleto) antes que duplicados reales |
| Conteido de parches/regex ≠ esperado | INC-03/04 (acentos, escapado, multilinea) |
| Header/cookie "no aparece" en respuesta HTTP | INC-06 (post-commit) |
| Idioma incorrecto en errores | INC-07 |
| Gate JaCoCo imposible de subir | INC-12/17 (clase obsoleta / ramas no cubiertas) |
| Build falla "sin razón" tras abrir el IDE | INC-09/10 (artefactos o toolchain del editor) |
| Comandos de shell se matan a sí mismos | INC-14 (patrones pkill / CWD) |
| Permisos raros dentro de contenedores podman | INC-15 (rootless + gosu) |
| Tests fallan tras cambio de formato de respuesta | INC-16 (rewrite parcial del envelope) |
| EnvelopeAdvice envuelve errores en success:true | INC-16 (GlobalExceptionHandler devuelve ProblemDetail) |
| Kardex modificado/eliminado → 409 KARDEX_APPEND_ONLY | INC-18 (solo INSERT permitido) |
| Stock inconsistente entre almacenes tras traslado | INC-19 (SALIDA+ENTRADA deben ser atómicas) |
| Checkout retorna totales en cero | INC-20 (hacer flush + re-read post-insert) |
| Venta crea duplicate de cotización | INC-21 (convertir solo cambia estado) |
| Devolución no incrementa stock | INC-22 (trigger hace E+ENTRADA automáticamente) |

---
*Mantenido por el equipo backend. Nueva incidencia = nueva fila en su sección + entrada en el mapa C.*
