import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, Warehouse } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'

import { useDocumentTitle } from '@/hooks/useDocumentTitle'
import { esApiError } from '@/lib/api/client'
import { apiAlmacenes } from '@/lib/api/catalogo'
import { apiStock } from '@/lib/api/reportes'
import type { Inventario } from '@/lib/api/types'
import { formatoNumero } from '@/lib/format'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { DataTable, type Columna } from '@/components/ui/DataTable'
import { Pagination } from '@/components/ui/Pagination'
import { Select } from '@/components/ui/Input'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/Toast'

export default function StockPage() {
  useDocumentTitle('Existencias')
  const { error: mostrarError } = useToast()
  const [searchParams] = useSearchParams()
  const [almacenId, setAlmacenId] = useState<number | ''>(() => {
    const v = Number(searchParams.get('almacen'))
    return Number.isFinite(v) && v > 0 ? v : ''
  })
  const [soloBajoStock, setSoloBajoStock] = useState<boolean>(() => searchParams.get('soloBajoStock') === '1')
  const [page, setPage] = useState(0)

  const almacenes = useQuery({ queryKey: ['almacenes'], queryFn: apiAlmacenes })

  const { data, isLoading, error, isFetching } = useQuery({
    queryKey: ['stock', almacenId, soloBajoStock, page],
    queryFn: () => apiStock({ almacenId: almacenId || undefined, soloBajoStock: soloBajoStock || undefined, page, size: 20 }),
  })

  useEffect(() => {
    if (error) mostrarError(esApiError(error) ? error.mensajeParaUsuario() : String(error))
  }, [error, mostrarError])

  const cambiarAlmacen = (v: string) => {
    setAlmacenId(v ? Number(v) : '')
    setPage(0)
  }
  const cambiarBajoStock = (b: boolean) => {
    setSoloBajoStock(b)
    setPage(0)
  }

  const columnas: Columna<Inventario>[] = [
    { key: 'c', header: 'Código', render: (v) => <span className="font-mono text-xs text-muted">{v.productoCodigo ?? '—'}</span> },
    { key: 'p', header: 'Producto', render: (v) => <span className="font-medium text-ink">{v.productoNombre}</span> },
    { key: 'a', header: 'Almacén', render: (v) => (
        <span className="inline-flex items-center gap-1 text-sm">
          <Warehouse className="h-3.5 w-3.5 text-muted" />
          {v.almacenNombre}
        </span>
      ) },
    { key: 'stock', header: 'Existencia', align: 'right', render: (v) => <span className={v.stock <= v.stockMinimo ? 'font-medium text-amber-700' : 'tabular-nums'}>{formatoNumero(v.stock)}</span> },
    { key: 'min', header: 'Stock mín.', align: 'right', render: (v) => <span className="tabular-nums text-muted">{formatoNumero(v.stockMinimo)}</span> },
    { key: 'res', header: 'Reservado', align: 'right', render: (v) => (v.reservado ? <span className="tabular-nums">{formatoNumero(v.reservado)}</span> : '—') },
    {
      key: 'estado',
      header: 'Estado',
      render: (v) =>
        v.stock <= 0 ? (
          <Badge tone="danger">Agotado</Badge>
        ) : v.stock <= v.stockMinimo ? (
          <Badge tone="warning">Bajo stock</Badge>
        ) : (
          <Badge tone="success">Disponible</Badge>
        ),
    },
  ]

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-ink">Existencias</h1>
          <p className="text-sm text-muted">Nivel de stock por almacén y productos en riesgo de desabasto.</p>
        </div>
      </header>

      <Card>
        <div className="flex flex-wrap items-end gap-2">
          <Select label="Almacén" value={almacenId} onChange={(e) => cambiarAlmacen(e.target.value)} className="w-60">
            <option value="">Todos</option>
            {almacenes.data?.map((a) => (
              <option key={a.almacenId} value={a.almacenId}>
                {a.nombre}
              </option>
            ))}
          </Select>
          <Button
            variant={soloBajoStock ? 'primary' : 'secondary'}
            onClick={() => cambiarBajoStock(!soloBajoStock)}
            className={soloBajoStock ? '' : 'text-amber-700'}
          >
            <AlertTriangle className="h-4 w-4" /> {soloBajoStock ? 'Mostrando solo bajo stock' : 'Solo bajo stock'}
          </Button>
          {(almacenId !== '' || soloBajoStock) && (
            <Button
              variant="ghost"
              onClick={() => {
                setAlmacenId('')
                setSoloBajoStock(false)
                setPage(0)
              }}
            >
              Limpiar
            </Button>
          )}
        </div>
      </Card>

      {(isLoading || (isFetching && !data)) && <Spinner />}
      {data && (
        <Card titulo={`Filas (${data.meta.totalElements})`}>
          <DataTable columnas={columnas} items={data.data} rowKey={(v) => `${v.productoId}-${v.almacenId}`} loading={isFetching} />
          <Pagination meta={data.meta} onPage={setPage} />
        </Card>
      )}
    </div>
  )
}