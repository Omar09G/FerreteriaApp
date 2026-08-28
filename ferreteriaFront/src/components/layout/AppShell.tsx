import { useState, type ReactNode } from 'react'
import { Boxes, BarChart3, Building2, CalendarCheck2, FileText, HardHat, Languages, LayoutDashboard, LogOut, Menu, Monitor, Moon, ReceiptText, Settings, ShoppingCart, Sun, Truck, Undo2, UserCog, Users2, Warehouse, X } from 'lucide-react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'

import { useAuthStore, tieneRol } from '@/store/auth'
import { useUiStore, type Tema } from '@/store/ui'
import { useT } from '@/i18n'
import { Button } from '@/components/ui/Button'

interface Item {
  clave: string
  a: string
  icono: ReactNode
  roles?: string[]
}

const GRUPOS: { clave: string; items: Item[] }[] = [
  {
    clave: 'operacion',
    items: [
      { clave: 'inicio', a: '/dashboard', icono: <LayoutDashboard className="h-4 w-4" /> },
      { clave: 'puntoVenta', a: '/pos', icono: <ReceiptText className="h-4 w-4" />, roles: ['VENDEDOR', 'ENCARGADO_CAJA', 'GERENTE', 'ADMINISTRADOR'] },
    ],
  },
  {
    clave: 'catalogos',
    items: [
      { clave: 'productos', a: '/catalogo/productos', icono: <Boxes className="h-4 w-4" /> },
      { clave: 'clientes', a: '/catalogo/clientes', icono: <Users2 className="h-4 w-4" /> },
      { clave: 'proveedores', a: '/compras/proveedores', icono: <Truck className="h-4 w-4" /> },
    ],
  },
  {
    clave: 'inventario',
    items: [
      { clave: 'existencias', a: '/inventario/stock', icono: <Warehouse className="h-4 w-4" /> },
      { clave: 'movimientos', a: '/inventario/movimientos', icono: <CalendarCheck2 className="h-4 w-4" /> },
      { clave: 'traslados', a: '/inventario/traslados', icono: <HardHat className="h-4 w-4" /> },
      { clave: 'conteos', a: '/inventario/conteos', icono: <Boxes className="h-4 w-4" /> },
    ],
  },
  {
    clave: 'comercial',
    items: [
      { clave: 'cobranza', a: '/ventas/cobranza', icono: <Building2 className="h-4 w-4" /> },
      { clave: 'cotizaciones', a: '/ventas/cotizaciones', icono: <FileText className="h-4 w-4" /> },
      { clave: 'rentas', a: '/ventas/rentas', icono: <HardHat className="h-4 w-4" /> },
      { clave: 'devoluciones', a: '/ventas/devoluciones', icono: <Undo2 className="h-4 w-4" /> },
      { clave: 'compras', a: '/compras/compras', icono: <ShoppingCart className="h-4 w-4" /> },
      { clave: 'cuentasPagar', a: '/compras/cuentas-pagar', icono: <BarChart3 className="h-4 w-4" /> },
    ],
  },
  {
    clave: 'administracion',
    items: [
      { clave: 'caja', a: '/caja/cajas', icono: <Building2 className="h-4 w-4" /> },
      { clave: 'gastos', a: '/caja/gastos', icono: <FileText className="h-4 w-4" /> },
      { clave: 'nomina', a: '/rrhh/nomina', icono: <Users2 className="h-4 w-4" /> },
      { clave: 'empleados', a: '/rrhh/empleados', icono: <Users2 className="h-4 w-4" />, roles: ['ADMINISTRADOR'] },
      { clave: 'usuarios', a: '/seguridad/usuarios', icono: <UserCog className="h-4 w-4" />, roles: ['ADMINISTRADOR'] },
      { clave: 'roles', a: '/seguridad/roles', icono: <Settings className="h-4 w-4" />, roles: ['ADMINISTRADOR'] },
      { clave: 'facturas', a: '/fiscal/facturas', icono: <FileText className="h-4 w-4" /> },
    ],
  },
]

const TEMA_SIGUIENTE: Record<Tema, Tema> = { light: 'dark', dark: 'system', system: 'light' }
const ICONO_TEMA = { light: Sun, dark: Moon, system: Monitor }

function SidebarNav() {
  const roles = useAuthStore((s) => s.usuario?.roles)
  const t = useT()
  return (
    <nav aria-label={t('appshell.navegacion')} className="flex-1 space-y-4 overflow-y-auto px-3 py-3">
      {GRUPOS.map((grupo) => {
        const visibles = grupo.items.filter((i) => tieneRol(roles, i.roles))
        if (visibles.length === 0) return null
        return (
          <div key={grupo.clave}>
            <p className="px-2 pb-1 text-[11px] font-semibold uppercase tracking-wider text-orange-200/70">
              {t(`appshell.grupos.${grupo.clave}`)}
            </p>
            <ul className="space-y-0.5">
              {visibles.map((i) => (
                <li key={i.a}>
                  <NavLink
                    to={i.a}
                    className={({ isActive }) =>
                      `flex items-center gap-2 rounded-md px-2 py-1.5 text-sm transition-colors ${
                        isActive ? 'bg-orange-900/60 font-medium text-white' : 'text-orange-100 hover:bg-orange-900/40'
                      }`
                    }
                  >
                    {i.icono}
                    {t(`appshell.items.${i.clave}`)}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        )
      })}
    </nav>
  )
}

function Preferencias() {
  const t = useT()
  const tema = useUiStore((s) => s.tema)
  const idioma = useUiStore((s) => s.idioma)
  const setTema = useUiStore((s) => s.setTema)
  const setIdioma = useUiStore((s) => s.setIdioma)
  const IconoTema = ICONO_TEMA[tema]

  return (
    <div className="flex items-center gap-1.5 rounded-md border border-line bg-surface p-1 text-muted shadow-sm">
      <button
        type="button"
        onClick={() => setTema(TEMA_SIGUIENTE[tema])}
        className="rounded p-1.5 hover:bg-warmbg hover:text-primary"
        aria-label={t('auth.tema.cambiar')}
        title={t(`auth.tema.${tema}`)}
      >
        <IconoTema className="h-4 w-4" />
      </button>
      <button
        type="button"
        onClick={() => setIdioma(idioma === 'es' ? 'en' : 'es')}
        className="inline-flex items-center gap-1 rounded px-2 py-1 text-xs font-semibold uppercase hover:bg-warmbg hover:text-primary"
        aria-label={t('auth.idioma.cambiar')}
        title={t('auth.idioma.cambiar')}
      >
        <Languages className="h-3.5 w-3.5" />
        {idioma === 'es' ? 'EN' : 'ES'}
      </button>
    </div>
  )
}

export function AppShell() {
  const [menuAbierto, setMenuAbierto] = useState(false)
  const t = useT()
  const usuario = useAuthStore((s) => s.usuario)
  const clearSession = useAuthStore((s) => s.clearSession)
  const navigate = useNavigate()

  const cerrarSesion = () => {
    clearSession()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex min-h-screen">
      {/* Sidebar escritorio */}
      <aside className="fixed inset-y-0 left-0 z-40 hidden w-60 flex-col bg-primary lg:flex">
        <div className="flex h-14 items-center gap-2 border-b border-orange-900/40 px-4">
          <span className="flex h-7 w-7 items-center justify-center rounded bg-white text-lg font-black text-primary" aria-hidden>
            T
          </span>
          <div className="leading-tight">
            <p className="text-sm font-semibold text-white">{t('appshell.marca')}</p>
            <p className="text-[11px] text-orange-200">{t('appshell.subtitulo')}</p>
          </div>
        </div>
        <SidebarNav />
        <div className="border-t border-orange-900/40 p-3">
          <div className="flex items-center gap-2 text-sm text-orange-100">
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-orange-900/60 font-semibold uppercase" aria-hidden>
              {usuario?.empleado?.nombreCompleto?.charAt(0) ?? usuario?.username.charAt(0) ?? '?'}
            </span>
            <div className="min-w-0 leading-tight">
              <p className="truncate font-medium text-white">{usuario?.empleado?.nombreCompleto ?? usuario?.username}</p>
              <p className="truncate text-[11px] text-orange-200">{usuario?.roles.join(', ')}</p>
            </div>
          </div>
          <Button variant="ghost" size="sm" onClick={cerrarSesion} className="mt-2 w-full text-orange-100 hover:bg-orange-900/40">
            <LogOut className="h-4 w-4" /> {t('appshell.cerrarSesion')}
          </Button>
        </div>
      </aside>

      {/* Topbar móvil */}
      <header className="fixed inset-x-0 top-0 z-40 flex h-14 items-center justify-between bg-primary px-3 lg:hidden">
        <button type="button" onClick={() => setMenuAbierto(true)} aria-label={t('appshell.abrirMenu')} className="rounded p-1 text-white">
          <Menu className="h-6 w-6" />
        </button>
        <p className="text-sm font-semibold text-white">{t('appshell.marca')}</p>
        <button type="button" onClick={cerrarSesion} aria-label={t('appshell.cerrarSesion')} className="rounded p-1 text-orange-100">
          <LogOut className="h-5 w-5" />
        </button>
      </header>

      {/* Drawer móvil */}
      {menuAbierto && (
        <div className="fixed inset-0 z-50 flex lg:hidden" role="dialog" aria-modal="true">
          <div className="flex-1 bg-black/40" onClick={() => setMenuAbierto(false)} aria-hidden />
          <aside className="flex w-72 flex-col bg-primary">
            <div className="flex h-14 items-center justify-between border-b border-orange-900/40 px-4">
              <p className="text-sm font-semibold text-white">{t('appshell.menu')}</p>
              <button type="button" onClick={() => setMenuAbierto(false)} aria-label={t('appshell.cerrarMenu')} className="rounded p-1 text-orange-100">
                <X className="h-5 w-5" />
              </button>
            </div>
            <SidebarNav />
          </aside>
        </div>
      )}

      <main className="min-w-0 flex-1 px-4 pb-10 pt-16 lg:ml-60 lg:pt-3">
        <div className="mx-auto mb-3 flex max-w-[1400px] items-center justify-end">
          <Preferencias />
        </div>
        <div className="mx-auto max-w-[1400px]">
          <Outlet />
        </div>
      </main>
    </div>
  )
}