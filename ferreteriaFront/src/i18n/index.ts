import { useCallback } from 'react'

import { useUiStore, type Idioma } from '@/store/ui'

import esDict from './es'
import enDict from './en'

type Diccionario = Record<string, unknown>

function resolver(diccionario: Diccionario, clave: string): string | undefined {
  let valor: unknown = diccionario
  for (const parte of clave.split('.')) {
    if (valor === null || typeof valor !== 'object') return undefined
    valor = (valor as Diccionario)[parte]
    if (valor === undefined) return undefined
  }
  return typeof valor === 'string' ? valor : undefined
}

function interpolar(texto: string, vars?: Record<string, string | number>): string {
  if (!vars) return texto
  let out = texto
  for (const [k, v] of Object.entries(vars)) {
    out = out.replaceAll(`{{${k}}}`, String(v))
  }
  return out
}

const DICCIONARIOS: Record<Idioma, Diccionario> = { es: esDict as Diccionario, en: enDict as Diccionario }

/** Traducción fuera de componentes (lee la preferencia del store). */
export function tFuera(clave: string, vars?: Record<string, string | number>): string {
  const idioma = useUiStore.getState().idioma
  let texto = resolver(DICCIONARIOS[idioma], clave)
  if (texto === undefined) texto = resolver(DICCIONARIOS.es, clave)
  if (texto === undefined) return clave
  return interpolar(texto, vars)
}

/** Hook de traducción reactivo al idioma. Uso: const t = useT(); t('comun.guardar'). */
export function useT() {
  const idioma = useUiStore((s) => s.idioma)
  return useCallback(
    (clave: string, vars?: Record<string, string | number>): string => tFueraClave(idioma, clave, vars),
    [idioma],
  )
}

function tFueraClave(idioma: Idioma, clave: string, vars?: Record<string, string | number>): string {
  let texto = resolver(DICCIONARIOS[idioma], clave)
  if (texto === undefined) texto = resolver(DICCIONARIOS.es, clave)
  if (texto === undefined) return clave
  return interpolar(texto, vars)
}