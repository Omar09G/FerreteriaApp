import { Component, type ErrorInfo, type ReactNode } from 'react'
import { AlertTriangle } from 'lucide-react'

import { Button } from '@/components/ui/Button'
import { tFuera } from '@/i18n'

interface Props {
  children: ReactNode
}

interface State {
  error: Error | null
}

/** Atrapa errores de renderizado por debajo y ofrece recuperación. */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('Error capturado:', error, info)
  }

  render() {
    if (this.state.error) {
      return (
        <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 p-6 text-center" role="alert">
          <AlertTriangle className="h-10 w-10 text-amber-600" aria-hidden />
          <h1 className="text-lg font-semibold text-ink">{tFuera('paginas.algoFallo')}</h1>
          <p className="max-w-md text-sm text-muted">{this.state.error.message}</p>
          <Button type="button" onClick={() => this.setState({ error: null })}>
            {tFuera('paginas.reintentar')}
          </Button>
        </div>
      )
    }
    return this.props.children
  }
}