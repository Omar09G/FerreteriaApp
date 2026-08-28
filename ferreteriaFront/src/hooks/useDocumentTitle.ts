import { useEffect } from 'react'

const SUFIJO = 'El Tornillo Feliz'

/** Actualiza document.title con el segmento de la ruta. */
export function useDocumentTitle(segmento?: string): void {
  useEffect(() => {
    document.title = segmento ? `${segmento} │ ${SUFIJO}` : SUFIJO
    return () => {
      document.title = SUFIJO
    }
  }, [segmento])
}