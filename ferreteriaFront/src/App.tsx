import { useEffect } from 'react'
import { RouterProvider } from 'react-router-dom'

import { useUiStore } from '@/store/ui'
import { router } from './router/router'

/** Aplica tema (claro/oscuro/sistema) e idioma al root del documento. */
function SincronizarUI() {
  const tema = useUiStore((s) => s.tema)
  const idioma = useUiStore((s) => s.idioma)

  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const aplicar = () => {
      const oscuro = tema === 'dark' || (tema === 'system' && media.matches)
      document.documentElement.classList.toggle('dark', oscuro)
    }
    aplicar()
    if (tema === 'system') {
      media.addEventListener('change', aplicar)
      return () => media.removeEventListener('change', aplicar)
    }
  }, [tema])

  useEffect(() => {
    document.documentElement.lang = idioma === 'en' ? 'en' : 'es-MX'
  }, [idioma])

  return null
}

export default function App() {
  return (
    <>
      <SincronizarUI />
      <RouterProvider router={router} />
    </>
  )
}