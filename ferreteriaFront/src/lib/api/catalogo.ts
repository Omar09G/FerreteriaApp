import http from './client'
import type { Almacen, AlmacenRequest, Categoria, Cliente, ClienteRequest, Envelope, Marca, PageEnvelope, Producto, ProductoRequest, Proveedor, ProveedorRequest, UnidadMedida } from './types'

export async function apiProductos(p: {
  q?: string
  categoriaId?: number
  tipo?: string
  page: number
  size: number
  sort?: string
}): Promise<PageEnvelope<Producto>> {
  const params: Record<string, string | number> = { page: p.page, size: p.size }
  if (p.q) params.q = p.q
  if (p.categoriaId) params.categoriaId = p.categoriaId
  if (p.tipo) params.tipo = p.tipo
  if (p.sort) params.sort = p.sort
  const { data } = await http.get<PageEnvelope<Producto>>('/productos', { params })
  return data
}

export async function apiCrearProducto(body: ProductoRequest): Promise<Producto> {
  const { data } = await http.post<Envelope<Producto>>('/productos', body)
  return data.data
}

export async function apiActualizarProducto(id: number, body: ProductoRequest): Promise<Producto> {
  const { data } = await http.put<Envelope<Producto>>(`/productos/${id}`, body)
  return data.data
}

export async function apiEliminarProducto(id: number): Promise<void> {
  await http.delete(`/productos/${id}`)
}

export async function apiCategoriasArbol(): Promise<Categoria[]> {
  const { data } = await http.get<Envelope<Categoria[]>>('/categorias/arbol')
  return data.data
}

export async function apiMarcas(): Promise<Marca[]> {
  const { data } = await http.get<PageEnvelope<Marca>>('/marcas', { params: { page: 0, size: 500 } })
  return data.data
}

export async function apiUnidadesMedida(): Promise<UnidadMedida[]> {
  const { data } = await http.get<PageEnvelope<UnidadMedida>>('/unidades-medida', { params: { page: 0, size: 100 } })
  return data.data
}

export async function apiClientes(p: { q?: string; page: number; size: number; sort?: string }): Promise<PageEnvelope<Cliente>> {
  const params: Record<string, string | number> = { page: p.page, size: p.size }
  if (p.q) params.q = p.q
  if (p.sort) params.sort = p.sort
  const { data } = await http.get<PageEnvelope<Cliente>>('/clientes', { params })
  return data
}

export async function apiCrearCliente(body: ClienteRequest): Promise<Cliente> {
  const { data } = await http.post<Envelope<Cliente>>('/clientes', body)
  return data.data
}

export async function apiActualizarCliente(id: number, body: ClienteRequest): Promise<Cliente> {
  const { data } = await http.put<Envelope<Cliente>>(`/clientes/${id}`, body)
  return data.data
}

export async function apiEliminarCliente(id: number): Promise<void> {
  await http.delete(`/clientes/${id}`)
}

export async function apiAlmacenes(): Promise<Almacen[]> {
  const { data } = await http.get<PageEnvelope<Almacen>>('/almacenes', { params: { page: 0, size: 50 } })
  return data.data
}

export async function apiAlmacenesTodos(): Promise<Almacen[]> {
  const { data } = await http.get<PageEnvelope<Almacen>>('/almacenes', { params: { todos: true, page: 0, size: 100 } })
  return data.data
}

export async function apiCrearAlmacen(body: AlmacenRequest): Promise<Almacen> {
  const { data } = await http.post<Envelope<Almacen>>('/almacenes', body)
  return data.data
}

export async function apiActualizarAlmacen(id: number, body: AlmacenRequest): Promise<Almacen> {
  const { data } = await http.put<Envelope<Almacen>>(`/almacenes/${id}`, body)
  return data.data
}

export async function apiActualizarEstadoAlmacen(id: number, activo: boolean): Promise<Almacen> {
  const { data } = await http.put<Envelope<Almacen>>(`/almacenes/${id}/estado`, { activo })
  return data.data
}

export async function apiProveedores(q?: string): Promise<Proveedor[]> {
  const params: Record<string, string | number> = { page: 0, size: 100 }
  if (q) params.q = q
  const { data } = await http.get<PageEnvelope<Proveedor>>('/proveedores', { params })
  return data.data
}

export async function apiProveedoresPaginado(p: { q?: string; page: number; size: number }): Promise<PageEnvelope<Proveedor>> {
  const params: Record<string, string | number> = { page: p.page, size: p.size }
  if (p.q) params.q = p.q
  const { data } = await http.get<PageEnvelope<Proveedor>>('/proveedores', { params })
  return data
}

export async function apiCrearProveedor(body: ProveedorRequest): Promise<Proveedor> {
  const { data } = await http.post<Envelope<Proveedor>>('/proveedores', body)
  return data.data
}

export async function apiActualizarProveedor(id: number, body: ProveedorRequest): Promise<Proveedor> {
  const { data } = await http.put<Envelope<Proveedor>>(`/proveedores/${id}`, body)
  return data.data
}

export async function apiEliminarProveedor(id: number): Promise<void> {
  await http.delete(`/proveedores/${id}`)
}