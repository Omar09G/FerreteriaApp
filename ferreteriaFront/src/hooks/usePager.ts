import { useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'

import { clampSize } from '@/lib/pager'

export interface UsePager {
  page: number
  size: number
  sort: string | undefined
  q: string | undefined
  setPage: (p: number) => void
  setSize: (s: number) => void
  setSort: (s: string | undefined) => void
  setQ: (q: string | undefined) => void
  params: Record<string, string | number>
}

/** Paginación, orden y búsqueda compartidos, espejados en la URL. */
export function usePager(): UsePager {
  const [sp, setSp] = useSearchParams()

  const page = Math.max(Number(sp.get('page') ?? '0'), 0)
  const size = clampSize(Number(sp.get('size') ?? '20'))
  const sort = sp.get('sort') ?? undefined
  const q = sp.get('q') ?? undefined

  const setPage = useCallback(
    (p: number) => {
      const next = new URLSearchParams(sp)
      if (p > 0) next.set('page', String(p))
      else next.delete('page')
      setSp(next, { replace: true })
    },
    [sp, setSp],
  )

  const setSize = useCallback(
    (s: number) => {
      const next = new URLSearchParams(sp)
      next.set('size', String(s))
      next.delete('page')
      setSp(next, { replace: true })
    },
    [sp, setSp],
  )

  const setSort = useCallback(
    (s: string | undefined) => {
      const next = new URLSearchParams(sp)
      if (s) next.set('sort', s)
      else next.delete('sort')
      setSp(next, { replace: true })
    },
    [sp, setSp],
  )

  const setQ = useCallback(
    (val: string | undefined) => {
      const next = new URLSearchParams(sp)
      if (val) next.set('q', val)
      else next.delete('q')
      next.delete('page')
      setSp(next, { replace: true })
    },
    [sp, setSp],
  )

  const params: Record<string, string | number> = { page, size }
  if (sort) params.sort = sort
  if (q) params.q = q

  return { page, size, sort, q, setPage, setSize, setSort, setQ, params }
}