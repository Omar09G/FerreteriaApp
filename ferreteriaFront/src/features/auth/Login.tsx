import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Languages, Lock, Monitor, Moon, Sun, User } from 'lucide-react'

import http, { mensajeError } from '@/lib/api/client'
import { apiLogin } from '@/lib/api/endpoints'
import type { Envelope, TokenResponse } from '@/lib/api/types'
import { useAuthStore } from '@/store/auth'
import { useUiStore, type Tema } from '@/store/ui'
import { useT } from '@/i18n'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { useDocumentTitle } from '@/hooks/useDocumentTitle'

const TEMA_SIGUIENTE: Record<Tema, Tema> = { light: 'dark', dark: 'system', system: 'light' }
const ICONO_TEMA = { light: Sun, dark: Moon, system: Monitor }

export default function Login() {
  const t = useT()
  useDocumentTitle(t('auth.titulo'))
  const navigate = useNavigate()
  const location = useLocation()
  const setSession = useAuthStore((state) => state.setSession)
  const tema = useUiStore((s) => s.tema)
  const idioma = useUiStore((s) => s.idioma)
  const setTema = useUiStore((s) => s.setTema)
  const setIdioma = useUiStore((s) => s.setIdioma)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(false)

  const registro = location.state as { from?: string } | null
  const destino = registro?.from ?? '/dashboard'

  const IconoTema = ICONO_TEMA[tema]

  const enviar = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setCargando(true)
    try {
      const token = await apiLogin({ username, password })
      setSession(token)
      // Sesión única: efectuamos el refresh rotativo de forma transparente.
      if (token.refreshToken) {
        void http.post<Envelope<TokenResponse>>('/auth/refresh', { refreshToken: token.refreshToken })
      }
      navigate(destino, { replace: true })
    } catch (err) {
      setError(mensajeError(err))
    } finally {
      setCargando(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-orange-700 via-primary to-orange-900 p-4">
      <form
        onSubmit={enviar}
        className="w-full max-w-sm rounded-xl border border-white/20 bg-surface p-6 shadow-2xl"
        aria-label={t('auth.ariaForm')}
      >
        <div className="mb-6 flex items-start justify-between">
          <div className="flex-1 text-center">
            <span className="mb-2 inline-flex h-12 w-12 items-center justify-center rounded-lg bg-primary text-2xl font-black text-white" aria-hidden>
              T
            </span>
            <h1 className="text-lg font-bold text-ink">{t('auth.marca')}</h1>
            <p className="text-sm text-muted">{t('auth.sistema')}</p>
          </div>
          <div className="flex flex-col items-end gap-1.5">
            <button
              type="button"
              onClick={() => setTema(TEMA_SIGUIENTE[tema])}
              className="rounded-md border border-line bg-surface p-1.5 text-muted hover:bg-warmbg"
              aria-label={t('auth.tema.cambiar')}
              title={t(`auth.tema.${tema}`)}
            >
              <IconoTema className="h-4 w-4" />
            </button>
            <button
              type="button"
              onClick={() => setIdioma(idioma === 'es' ? 'en' : 'es')}
              className="rounded-md border border-line bg-surface px-2 py-1 text-xs font-semibold text-muted hover:bg-warmbg"
              aria-label={t('auth.idioma.cambiar')}
              title={t('auth.idioma.cambiar')}
            >
              <span className="inline-flex items-center gap-1">
                <Languages className="h-3.5 w-3.5" />
                {idioma === 'es' ? 'EN' : 'ES'}
              </span>
            </button>
          </div>
        </div>
        {error && <p className="mb-3 rounded-md bg-red-50 p-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-400">{error}</p>}
        <div className="space-y-3">
          <Input
            label={t('auth.usuario')}
            icono={<User className="h-4 w-4 text-muted" />}
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            required
            autoFocus
          />
          <Input
            label={t('auth.contrasena')}
            type="password"
            icono={<Lock className="h-4 w-4 text-muted" />}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </div>
        <Button type="submit" size="lg" className="mt-5 w-full" disabled={cargando}>
          {cargando ? t('auth.ingresando') : t('auth.ingresar')}
        </Button>
      </form>
    </div>
  )
}