import { AlertTriangle, FileQuestion } from 'lucide-react'
import { Link, isRouteErrorResponse, useRouteError } from 'react-router-dom'

import { useT } from '@/i18n'
import { Button } from '@/components/ui/Button'

export function NotFound() {
  const t = useT()
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-20 text-center">
      <FileQuestion className="h-12 w-12 text-line" aria-hidden />
      <h1 className="text-xl font-semibold text-ink">{t('paginas.noEncontrada')}</h1>
      <p className="text-sm text-muted">{t('paginas.noEncontradaDesc')}</p>
      <Link
        to="/dashboard"
        className="inline-flex items-center justify-center gap-2 rounded-md bg-primary px-3.5 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-hover"
      >
        {t('paginas.irInicio')}
      </Link>
    </div>
  )
}

export function AccessDenied() {
  const t = useT()
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-20 text-center">
      <h1 className="text-xl font-semibold text-ink">{t('paginas.accesoDenegado')}</h1>
      <p className="max-w-md text-sm text-muted">{t('paginas.accesoDenegadoDesc')}</p>
    </div>
  )
}

export function ErrorPagina() {
  const t = useT()
  const error = useRouteError()
  const mensaje = isRouteErrorResponse(error)
    ? `${error.status} ${error.statusText}`
    : error instanceof Error
      ? error.message
      : t('paginas.inesperado')
  return (
    <div className="flex min-h-[50vh] flex-col items-center justify-center gap-3 p-6 text-center" role="alert">
      <AlertTriangle className="h-10 w-10 text-amber-600" aria-hidden />
      <h1 className="text-lg font-semibold text-ink">{t('paginas.noSePudoMostrar')}</h1>
      <p className="max-w-md text-sm text-muted">{mensaje}</p>
      <Button type="button" onClick={() => history.go(0)}>
        {t('paginas.reintentar')}
      </Button>
    </div>
  )
}