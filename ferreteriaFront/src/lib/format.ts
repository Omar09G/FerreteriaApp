import { useUiStore } from '@/store/ui'

const MONEDA = new Intl.NumberFormat('es-MX', {
  style: 'currency',
  currency: 'MXN',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const cacheNumerico = new Map<string, Intl.NumberFormat>()
const cacheFechaDia = new Map<string, Intl.DateTimeFormat>()
const cacheFechaCompleta = new Map<string, Intl.DateTimeFormat>()

function localeActual(): string {
  return useUiStore.getState().idioma === 'es' ? 'es-MX' : 'en-US'
}

function numerico(): Intl.NumberFormat {
  const l = localeActual()
  let f = cacheNumerico.get(l)
  if (!f) {
    f = new Intl.NumberFormat(l, { maximumFractionDigits: 2 })
    cacheNumerico.set(l, f)
  }
  return f
}

function fechaDia(): Intl.DateTimeFormat {
  const l = localeActual()
  let f = cacheFechaDia.get(l)
  if (!f) {
    f = new Intl.DateTimeFormat(l, { year: 'numeric', month: '2-digit', day: '2-digit' })
    cacheFechaDia.set(l, f)
  }
  return f
}

function fechaCompleta(): Intl.DateTimeFormat {
  const l = localeActual()
  let f = cacheFechaCompleta.get(l)
  if (!f) {
    f = new Intl.DateTimeFormat(l, {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
    cacheFechaCompleta.set(l, f)
  }
  return f
}

/** Formatea un monto como moneda MXN. Ej: $1,234.50 */
export function formatoMoneda(valor: number | string | null | undefined): string {
  if (valor === null || valor === undefined || valor === '') return '—'
  const n = typeof valor === 'string' ? Number(valor) : valor
  if (Number.isNaN(n)) return '—'
  return MONEDA.format(n)
}

/** Formatea un número (cantidades, unidades) según el idioma. */
export function formatoNumero(valor: number | string | null | undefined): string {
  if (valor === null || valor === undefined || valor === '') return '—'
  const n = typeof valor === 'string' ? Number(valor) : valor
  if (Number.isNaN(n)) return '—'
  return numerico().format(n)
}

/** Formatea una fecha ISO local (yyyy-MM-dd) como día/mes/año según el idioma. */
export function formatoFecha(iso: string | null | undefined): string {
  if (!iso) return '—'
  const [y, m, d] = iso.slice(0, 10).split('-')
  if (!y || !m || !d) return iso
  const fecha = new Date(Number(y), Number(m) - 1, Number(d))
  if (Number.isNaN(fecha.getTime())) return iso
  return fechaDia().format(fecha)
}

/** Formatea un Instant ISO-8601 (UTC, termina en Z) a fecha/hora local del idioma. */
export function formatoFechaHora(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return fechaCompleta().format(d)
}

/** Date o dato hacia yyyy-MM-dd (contrato LocalDate del backend). */
export function aLocalDate(fecha: Date): string {
  const y = fecha.getFullYear()
  const m = String(fecha.getMonth() + 1).padStart(2, '0')
  const d = String(fecha.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function hoyLocal(): string {
  return aLocalDate(new Date())
}

/** Porcentaje con un decimal. */
export function formatoPorcentaje(valor: number | string | null | undefined): string {
  if (valor === null || valor === undefined || valor === '') return '—'
  const n = typeof valor === 'string' ? Number(valor) : valor
  if (Number.isNaN(n)) return '—'
  return `${n.toFixed(1)}%`
}