/** Orden 'asc' | 'desc' por defecto según el endpoint. */
export interface PagerState {
  page: number
  size: number
  sort?: string
}

export function pagerParams(p: PagerState): Record<string, string | number> {
  const out: Record<string, string | number> = {
    page: p.page,
    size: p.size,
  }
  if (p.sort) out.sort = p.sort
  return out
}

export function clampSize(size: number): number {
  return Math.min(Math.max(size, 1), 100)
}