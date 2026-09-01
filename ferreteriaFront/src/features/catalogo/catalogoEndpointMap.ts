import type { FilaCatalogo } from '@/lib/api/types'

/**
 * Conecta el CRUD genérico del front (descriptor-driven) con los endpoints
 * individuales por tabla del backend. Cada catálogo declara:
 *
 *  - path:        ruta individual de su CRUD (ej. "/estados")
 *  - campos:      mapa descriptor → campo DTO en ambos sentidos (snake_case ↔ camelCase)
 *  - pkDTO:       nombre del campo PK en el DTO de respuesta (ej. "estadoId")
 *  - REQ / RES:   contratos tipados del catálogo
 *
 * El frente sigue siendo genérico: la página usa los campos del descriptor y
 * este mapa traduce a los nombres que espera el DTO tipado de cada tabla.
 */
/** Descriptor de conexión de un catálogo con su endpoint individual. */
export interface CatalogoEndpoint {
  path: string
  /** descriptor (ej. "estado_id") → DTO (ej. "estadoId") */
  campo: Record<string, string>
  /** DTO (ej. "estadoId") → descriptor (ej. "estado_id") */
  campoInverso: Record<string, string>
  pkDTO: string
}

type Mapa = {
  estados: CatalogoEndpoint
  ciudades: CatalogoEndpoint
  puestos: CatalogoEndpoint
  motivos_movimiento: CatalogoEndpoint
  tipos_gasto: CatalogoEndpoint
  formas_pago: CatalogoEndpoint
  impuestos: CatalogoEndpoint
  tasas_impuesto: CatalogoEndpoint
  regimenes_fiscales: CatalogoEndpoint
  usos_cfdi: CatalogoEndpoint
  formas_pago_sat: CatalogoEndpoint
  metodos_pago_sat: CatalogoEndpoint
  unidades_sat: CatalogoEndpoint
  claves_prod_serv: CatalogoEndpoint
  configuracion: CatalogoEndpoint
  folios: CatalogoEndpoint
}

const mapa: Mapa = {
  estados: {
    path: '/estados',
    pkDTO: 'estadoId',
    campo: { estado_id: 'estadoId', clave_inegi: 'claveInegi', nombre: 'nombre' },
    campoInverso: { estadoId: 'estado_id', claveInegi: 'clave_inegi', nombre: 'nombre' },
  },
  ciudades: {
    path: '/ciudades',
    pkDTO: 'ciudadId',
    campo: { ciudad_id: 'ciudadId', estado_id: 'estadoId', nombre: 'nombre' },
    campoInverso: { ciudadId: 'ciudad_id', estadoId: 'estado_id', estadoNombre: 'estado_nombre', nombre: 'nombre' },
  },
  puestos: {
    path: '/puestos',
    pkDTO: 'puestoId',
    campo: { puesto_id: 'puestoId', nombre: 'nombre', sueldo_base: 'sueldoBase', activo: 'activo' },
    campoInverso: { puestoId: 'puesto_id', nombre: 'nombre', sueldoBase: 'sueldo_base', activo: 'activo' },
  },
  motivos_movimiento: {
    path: '/motivos-movimiento',
    pkDTO: 'motivoId',
    campo: { motivo_id: 'motivoId', clave: 'clave', nombre: 'nombre', tipo_default: 'tipoDefault', activo: 'activo' },
    campoInverso: { motivoId: 'motivo_id', clave: 'clave', nombre: 'nombre', tipoDefault: 'tipo_default', activo: 'activo' },
  },
  tipos_gasto: {
    path: '/tipos-gasto',
    pkDTO: 'tipoGastoId',
    campo: { tipo_gasto_id: 'tipoGastoId', clave: 'clave', nombre: 'nombre', es_fijo: 'esFijo', activo: 'activo' },
    campoInverso: { tipoGastoId: 'tipo_gasto_id', clave: 'clave', nombre: 'nombre', esFijo: 'es_fijo', activo: 'activo' },
  },
  formas_pago: {
    path: '/formas-pago',
    pkDTO: 'formaPagoId',
    campo: {
      forma_pago_id: 'formaPagoId', clave: 'clave', nombre: 'nombre',
      es_efectivo: 'esEfectivo', requiere_referencia: 'requiereReferencia', afecta_caja: 'afectaCaja',
      forma_pago_sat: 'formaPagoSatClave', comision_pct: 'comisionPct', activo: 'activo',
    },
    campoInverso: {
      formaPagoId: 'forma_pago_id', clave: 'clave', nombre: 'nombre',
      esEfectivo: 'es_efectivo', requiereReferencia: 'requiere_referencia', afectaCaja: 'afecta_caja',
      formaPagoSatClave: 'forma_pago_sat', comisionPct: 'comision_pct', activo: 'activo',
    },
  },
  impuestos: {
    path: '/impuestos',
    pkDTO: 'impuestoId',
    campo: { impuesto_id: 'impuestoId', clave_sat: 'claveSat', nombre: 'nombre', tipo: 'tipo', activo: 'activo' },
    campoInverso: { impuestoId: 'impuesto_id', claveSat: 'clave_sat', nombre: 'nombre', tipo: 'tipo', activo: 'activo' },
  },
  tasas_impuesto: {
    path: '/tasas-impuesto',
    pkDTO: 'tasaId',
    campo: {
      tasa_id: 'tasaId', impuesto_id: 'impuestoId', tasa: 'tasa', factor: 'factor', ambito: 'ambito',
      zona_frontera: 'zonaFrontera', vigente_desde: 'vigenteDesde', vigente_hasta: 'vigenteHasta', activo: 'activo',
    },
    campoInverso: {
      tasaId: 'tasa_id', impuestoId: 'impuesto_id', tasa: 'tasa', factor: 'factor', ambito: 'ambito',
      zonaFrontera: 'zona_frontera', vigenteDesde: 'vigente_desde', vigenteHasta: 'vigente_hasta', activo: 'activo',
    },
  },
  regimenes_fiscales: {
    path: '/regimenes-fiscales',
    pkDTO: 'claveSat',
    campo: {
      clave_sat: 'claveSat', descripcion: 'descripcion',
      persona_fisica: 'personaFisica', persona_moral: 'personaMoral', activo: 'activo',
    },
    campoInverso: {
      claveSat: 'clave_sat', descripcion: 'descripcion',
      personaFisica: 'persona_fisica', personaMoral: 'persona_moral', activo: 'activo',
    },
  },
  usos_cfdi: {
    path: '/usos-cfdi',
    pkDTO: 'clave',
    campo: {
      clave: 'clave', descripcion: 'descripcion',
      aplica_fisica: 'aplicaFisica', aplica_moral: 'aplicaMoral', activo: 'activo',
    },
    campoInverso: {
      clave: 'clave', descripcion: 'descripcion',
      aplicaFisica: 'aplica_fisica', aplicaMoral: 'aplica_moral', activo: 'activo',
    },
  },
  formas_pago_sat: {
    path: '/formas-pago-sat',
    pkDTO: 'clave',
    campo: { clave: 'clave', descripcion: 'descripcion', activo: 'activo' },
    campoInverso: { clave: 'clave', descripcion: 'descripcion', activo: 'activo' },
  },
  metodos_pago_sat: {
    path: '/metodos-pago-sat',
    pkDTO: 'clave',
    campo: { clave: 'clave', descripcion: 'descripcion', activo: 'activo' },
    campoInverso: { clave: 'clave', descripcion: 'descripcion', activo: 'activo' },
  },
  unidades_sat: {
    path: '/unidades-sat',
    pkDTO: 'clave',
    campo: { clave: 'clave', descripcion: 'descripcion', activo: 'activo' },
    campoInverso: { clave: 'clave', descripcion: 'descripcion', activo: 'activo' },
  },
  claves_prod_serv: {
    path: '/claves-prod-serv',
    pkDTO: 'clave',
    campo: { clave: 'clave', descripcion: 'descripcion', incluye_iva: 'incluyeIva', ejemplo: 'ejemplo' },
    campoInverso: { clave: 'clave', descripcion: 'descripcion', incluyeIva: 'incluye_iva', ejemplo: 'ejemplo' },
  },
  configuracion: {
    path: '/configuraciones',
    pkDTO: 'clave',
    campo: { clave: 'clave', valor: 'valor', descripcion: 'descripcion' },
    campoInverso: { clave: 'clave', valor: 'valor', descripcion: 'descripcion' },
  },
  folios: {
    path: '/folios',
    pkDTO: 'tipo',
    campo: { tipo: 'tipo', prefijo: 'prefijo', consecutivo: 'consecutivo' },
    campoInverso: { tipo: 'tipo', prefijo: 'prefijo', consecutivo: 'consecutivo' },
  },
}

export function catalogoEndpoint(clave: string): CatalogoEndpoint | undefined {
  return mapa[clave as keyof Mapa]
}

/** Acceso tipado cuando el catálogo se conoce en tiempo de compilación. */
export function catalogoEndpointDe<K extends keyof Mapa>(clave: K): Mapa[K] {
  return mapa[clave]
}

/** Convierte una fila DTO (respuesta tipada) a fila descriptor para la tabla. */
export function dtoFilaAClave(clave: string, fila: Record<string, unknown>): FilaCatalogo {
  const ep = mapa[clave as keyof Mapa]
  const out: Record<string, unknown> = {}
  for (const [dto, descriptor] of Object.entries(ep.campoInverso)) {
    if (dto in fila) out[descriptor] = fila[dto as keyof typeof fila]
  }
  out.__pk = fila[ep.pkDTO]
  return out as FilaCatalogo
}

/** Convierte el payload del formulario (claves descriptor) a claves DTO para el backend. */
export function clavePayloadADTO(clave: string, cuerpo: Record<string, unknown>): Record<string, unknown> {
  const ep = mapa[clave as keyof Mapa]
  const out: Record<string, unknown> = {}
  for (const [descriptor, valor] of Object.entries(cuerpo)) {
    const dto = ep.campo[descriptor]
    if (dto !== undefined) out[dto] = valor
  }
  return out
}
