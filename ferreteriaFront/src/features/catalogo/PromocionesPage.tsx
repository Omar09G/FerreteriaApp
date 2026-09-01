import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Edit, Percent, Tag, Trash2 } from "lucide-react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { useT } from "@/i18n";
import { apiCategoriasArbol } from "@/lib/api/catalogo";
import { apiProductos } from "@/lib/api/catalogo";
import { esApiError } from "@/lib/api/client";
import {
  apiActualizarPromocion,
  apiCrearPromocion,
  apiEliminarPromocion,
  apiPromocion,
  apiPromociones,
  type PromocionesFiltros,
} from "@/lib/api/promociones";
import type {
  Categoria,
  EstadoPromocion,
  Promocion,
  PromocionRequest,
  TipoPromocion,
} from "@/lib/api/types";
import { formatoFechaHora } from "@/lib/format";
import { useTieneRol } from "@/store/auth";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { Dialog } from "@/components/ui/Dialog";
import { Input, Select } from "@/components/ui/Input";
import { Pagination } from "@/components/ui/Pagination";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";

const TIPOS: { value: TipoPromocion; label: string; ayuda: string }[] = [
  {
    value: "DESCUENTO_PRODUCTO",
    label: "Descuento por producto",
    ayuda: "Aplica valor_pct o valor_monto al producto",
  },
  {
    value: "DESCUENTO_TOTAL_VENTA",
    label: "Descuento al total",
    ayuda: "Aplica valor_pct o valor_monto al subtotal",
  },
  {
    value: "POR_CANTIDAD",
    label: "Por cantidad mínima",
    ayuda: "Requiere compra_min_cantidad + valor",
  },
  { value: "NXM", label: "Lleva N paga M", ayuda: "Define lleva y paga" },
  {
    value: "PRECIO_ESPECIAL",
    label: "Precio especial",
    ayuda: "Precio fijo independiente",
  },
];

const ESTADOS: EstadoPromocion[] = [
  "ACTIVA",
  "PROGRAMADA",
  "FINALIZADA",
  "CANCELADA",
];

const DIAS = [
  { d: 1, label: "L" },
  { d: 2, label: "M" },
  { d: 3, label: "M" },
  { d: 4, label: "J" },
  { d: 5, label: "V" },
  { d: 6, label: "S" },
  { d: 7, label: "D" },
];

const estadoTone = (
  e: EstadoPromocion,
): "success" | "warning" | "danger" | "default" => {
  if (e === "ACTIVA") return "success";
  if (e === "PROGRAMADA") return "warning";
  if (e === "FINALIZADA") return "default";
  return "danger";
};

/** Aplana árbol de categorías en una lista ordenada para mostrar en el formulario. */
function aplanarCategorias(arbol: Categoria[]): Categoria[] {
  const out: Categoria[] = [];
  const visitar = (c: Categoria) => {
    out.push(c);
    c.hijos?.forEach(visitar);
  };
  arbol.forEach(visitar);
  return out;
}

/** Convierte "yyyy-MM-ddTHH:mm" del input datetime-local a ISO con timezone. */
function datetimeLocalAiso(s: string | undefined | null): string | undefined {
  if (!s) return undefined;
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return undefined;
  return d.toISOString();
}

function vacioARequest(): PromocionRequest {
  return {
    nombre: "",
    descripcion: undefined,
    tipo: "DESCUENTO_PRODUCTO",
    valorPct: undefined,
    valorMonto: undefined,
    precioEspecial: undefined,
    compraMinTotal: undefined,
    compraMinCantidad: undefined,
    lleva: undefined,
    paga: undefined,
    maxUsosTotal: undefined,
    maxUsosCliente: undefined,
    vigenciaDesde: new Date().toISOString(),
    vigenciaHasta: undefined,
    diasSemana: [1, 2, 3, 4, 5, 6, 7],
    horaDesde: undefined,
    horaHasta: undefined,
    soloMayoristas: false,
    estado: "ACTIVA",
    productos: [],
    categorias: [],
  };
}

function PromocionForm({
  guardando,
  enGuardar,
  cerrar,
  inicial,
}: {
  guardando: boolean;
  enGuardar: (body: PromocionRequest) => void;
  cerrar: () => void;
  inicial?: Promocion;
}) {
  const t = useT();
  const esEdicion = !!inicial;
  const [body, setBody] = useState<PromocionRequest>(() => {
    if (inicial) {
      return {
        nombre: inicial.nombre,
        descripcion: inicial.descripcion ?? undefined,
        tipo: inicial.tipo,
        valorPct: inicial.valorPct,
        valorMonto: inicial.valorMonto,
        precioEspecial: inicial.precioEspecial,
        compraMinTotal: inicial.compraMinTotal,
        compraMinCantidad: inicial.compraMinCantidad,
        lleva: inicial.lleva,
        paga: inicial.paga,
        maxUsosTotal: inicial.maxUsosTotal,
        maxUsosCliente: inicial.maxUsosCliente,
        vigenciaDesde: inicial.vigenciaDesde,
        vigenciaHasta: inicial.vigenciaHasta,
        diasSemana: inicial.diasSemana,
        horaDesde: inicial.horaDesde,
        horaHasta: inicial.horaHasta,
        soloMayoristas: inicial.soloMayoristas,
        estado: inicial.estado,
        productos: inicial.productos,
        categorias: inicial.categorias,
      };
    }
    return vacioARequest();
  });
  const [vigenciaDesdeStr, setVigenciaDesdeStr] = useState<string>(
    inicial?.vigenciaDesde ? inicial.vigenciaDesde.slice(0, 16) : "",
  );
  const [vigenciaHastaStr, setVigenciaHastaStr] = useState<string>(
    inicial?.vigenciaHasta ? inicial.vigenciaHasta.slice(0, 16) : "",
  );
  const [intento, setIntento] = useState(false);

  const categorias = useQuery({
    queryKey: ["categorias-promo"],
    queryFn: () => apiCategoriasArbol(),
  });
  const [busquedaProducto, setBusquedaProducto] = useState("");
  const productos = useQuery({
    queryKey: ["productos-promo", busquedaProducto],
    queryFn: () =>
      apiProductos({
        q: busquedaProducto || undefined,
        page: 0,
        size: 50,
        sort: "nombre",
      }),
  });

  const categoriasList = useMemo(
    () => (categorias.data ? aplanarCategorias(categorias.data) : []),
    [categorias.data],
  );

  const setField = <K extends keyof PromocionRequest>(
    k: K,
    v: PromocionRequest[K],
  ) => setBody((b) => ({ ...b, [k]: v }));

  const toggleDia = (d: number) =>
    setBody((b) => ({
      ...b,
      diasSemana: b.diasSemana.includes(d)
        ? b.diasSemana.filter((x) => x !== d)
        : [...b.diasSemana, d],
    }));

  const toggleCategoria = (id: number) =>
    setBody((b) => ({
      ...b,
      categorias: b.categorias.includes(id)
        ? b.categorias.filter((x) => x !== id)
        : [...b.categorias, id],
    }));

  const agregarProducto = (id: number) =>
    setBody((b) => ({
      ...b,
      productos: b.productos.includes(id) ? b.productos : [...b.productos, id],
    }));

  const quitarProducto = (id: number) =>
    setBody((b) => ({ ...b, productos: b.productos.filter((x) => x !== id) }));

  const invalido = body.nombre.trim() === "" || body.diasSemana.length === 0;

  const enviar = (e: { preventDefault: () => void }) => {
    e.preventDefault();
    setIntento(true);
    if (invalido) return;
    enGuardar({
      ...body,
      nombre: body.nombre.trim(),
      descripcion: body.descripcion?.trim() || undefined,
      vigenciaDesde: datetimeLocalAiso(vigenciaDesdeStr) ?? body.vigenciaDesde,
      vigenciaHasta: datetimeLocalAiso(vigenciaHastaStr),
      horaDesde: body.horaDesde || undefined,
      horaHasta: body.horaHasta || undefined,
      soloMayoristas: body.soloMayoristas || undefined,
      estado: body.estado,
    });
  };

  const productosSeleccionados =
    productos.data?.data.filter((p) => body.productos.includes(p.productoId)) ??
    [];

  const esDescuento =
    body.tipo === "DESCUENTO_PRODUCTO" ||
    body.tipo === "DESCUENTO_TOTAL_VENTA" ||
    body.tipo === "POR_CANTIDAD";
  const esNxm = body.tipo === "NXM";
  const esPrecio = body.tipo === "PRECIO_ESPECIAL";

  return (
    <form
      onSubmit={enviar}
      className="grid grid-cols-1 gap-3 sm:grid-cols-2"
      noValidate
    >
      <Input
        label={t("comun.nombre")}
        required
        value={body.nombre}
        onChange={(e) => setField("nombre", e.target.value)}
        error={
          intento && body.nombre.trim() === ""
            ? t("comun.obligatorio")
            : undefined
        }
      />
      <Select
        label={t("catalogo.promociones.filtros.tipo")}
        required
        value={body.tipo}
        onChange={(e) => setField("tipo", e.target.value as TipoPromocion)}
      >
        {TIPOS.map((tt) => (
          <option key={tt.value} value={tt.value}>
            {t(`catalogo.promociones.tipos.${tt.value}` as never)}
          </option>
        ))}
      </Select>
      <Input
        label={t("comun.descripcion")}
        className="sm:col-span-2"
        value={body.descripcion ?? ""}
        onChange={(e) => setField("descripcion", e.target.value)}
      />

      {esDescuento && (
        <>
          <Input
            label={t("catalogo.promociones.campos.valorPct")}
            type="number"
            step="0.01"
            value={body.valorPct ?? ""}
            onChange={(e) =>
              setField(
                "valorPct",
                e.target.value === "" ? undefined : Number(e.target.value),
              )
            }
          />
          <Input
            label={t("catalogo.promociones.campos.valorMonto")}
            type="number"
            step="0.01"
            value={body.valorMonto ?? ""}
            onChange={(e) =>
              setField(
                "valorMonto",
                e.target.value === "" ? undefined : Number(e.target.value),
              )
            }
          />
          <Input
            label={t("catalogo.promociones.campos.compraMinTotal")}
            type="number"
            step="0.01"
            value={body.compraMinTotal ?? ""}
            onChange={(e) =>
              setField(
                "compraMinTotal",
                e.target.value === "" ? undefined : Number(e.target.value),
              )
            }
          />
          <Input
            label={t("catalogo.promociones.campos.compraMinCantidad")}
            type="number"
            step="0.001"
            value={body.compraMinCantidad ?? ""}
            onChange={(e) =>
              setField(
                "compraMinCantidad",
                e.target.value === "" ? undefined : Number(e.target.value),
              )
            }
          />
        </>
      )}

      {esNxm && (
        <>
          <Input
            label={t("catalogo.promociones.campos.lleva")}
            type="number"
            step="0.001"
            required
            value={body.lleva ?? ""}
            onChange={(e) =>
              setField(
                "lleva",
                e.target.value === "" ? undefined : Number(e.target.value),
              )
            }
          />
          <Input
            label={t("catalogo.promociones.campos.paga")}
            type="number"
            step="0.001"
            required
            value={body.paga ?? ""}
            onChange={(e) =>
              setField(
                "paga",
                e.target.value === "" ? undefined : Number(e.target.value),
              )
            }
          />
        </>
      )}

      {esPrecio && (
        <Input
          label={t("catalogo.promociones.campos.precioEspecial")}
          type="number"
          step="0.01"
          required
          value={body.precioEspecial ?? ""}
          onChange={(e) =>
            setField(
              "precioEspecial",
              e.target.value === "" ? undefined : Number(e.target.value),
            )
          }
        />
      )}

      <Input
        label={t("catalogo.promociones.campos.maxUsosTotal")}
        type="number"
        value={body.maxUsosTotal ?? ""}
        onChange={(e) =>
          setField(
            "maxUsosTotal",
            e.target.value === "" ? undefined : Number(e.target.value),
          )
        }
      />
      <Input
        label={t("catalogo.promociones.campos.maxUsosCliente")}
        type="number"
        value={body.maxUsosCliente ?? ""}
        onChange={(e) =>
          setField(
            "maxUsosCliente",
            e.target.value === "" ? undefined : Number(e.target.value),
          )
        }
      />

      <div className="sm:col-span-2 grid grid-cols-2 gap-3">
        <Input
          label={t("catalogo.promociones.campos.vigenciaDesde")}
          type="datetime-local"
          value={vigenciaDesdeStr}
          onChange={(e) => setVigenciaDesdeStr(e.target.value)}
        />
        <Input
          label={t("catalogo.promociones.campos.vigenciaHasta")}
          type="datetime-local"
          value={vigenciaHastaStr}
          onChange={(e) => setVigenciaHastaStr(e.target.value)}
        />
      </div>

      <div className="sm:col-span-2">
        <span className="text-xs font-medium text-muted">
          {t("catalogo.promociones.campos.dias")}
        </span>
        <div className="mt-1 flex flex-wrap gap-2">
          {DIAS.map((d) => (
            <label
              key={d.d}
              className={`flex h-9 w-9 cursor-pointer items-center justify-center rounded-md border text-xs font-semibold ${
                body.diasSemana.includes(d.d)
                  ? "border-primary bg-primary text-white"
                  : "border-line"
              }`}
            >
              <input
                type="checkbox"
                className="sr-only"
                checked={body.diasSemana.includes(d.d)}
                onChange={() => toggleDia(d.d)}
              />
              {t(`catalogo.promociones.campos.diasCorto.${d.label}` as never)}
            </label>
          ))}
          {intento && body.diasSemana.length === 0 && (
            <span className="ml-2 text-xs text-red-600">
              {t("catalogo.promociones.campos.seleccionaUnDia")}
            </span>
          )}
        </div>
      </div>

      <div className="sm:col-span-2 grid grid-cols-2 gap-3">
        <Input
          label={t("catalogo.promociones.campos.horaDesde")}
          type="time"
          value={body.horaDesde ?? ""}
          onChange={(e) => setField("horaDesde", e.target.value || undefined)}
        />
        <Input
          label={t("catalogo.promociones.campos.horaHasta")}
          type="time"
          value={body.horaHasta ?? ""}
          onChange={(e) => setField("horaHasta", e.target.value || undefined)}
        />
      </div>

      <label className="flex items-center gap-2 sm:col-span-2">
        <input
          type="checkbox"
          checked={body.soloMayoristas}
          onChange={(e) => setField("soloMayoristas", e.target.checked)}
          className="accent-primary"
        />
        <span className="text-sm">
          {t("catalogo.promociones.campos.soloMayoristas")}
        </span>
      </label>

      <Select
        label={t("comun.estado")}
        value={body.estado}
        onChange={(e) => setField("estado", e.target.value as EstadoPromocion)}
      >
        {ESTADOS.map((e) => (
          <option key={e} value={e}>
            {e}
          </option>
        ))}
      </Select>
      {esEdicion && (
        <div className="flex items-end text-sm text-muted">
          {t("catalogo.promociones.campos.usosActuales", {
            n: inicial!.usosActual,
          })}
        </div>
      )}

      <div className="sm:col-span-2 mt-2">
        <span className="text-xs font-medium text-muted">
          {t("catalogo.promociones.campos.categoriasAplicables")}
        </span>
        {categorias.isLoading ? (
          <Spinner />
        ) : (
          <div className="mt-1 grid max-h-40 grid-cols-2 gap-1 overflow-y-auto rounded-md border border-line p-2 sm:grid-cols-3">
            {categoriasList.map((c) => (
              <label
                key={c.categoriaId}
                className="flex items-center gap-1.5 text-xs"
              >
                <input
                  type="checkbox"
                  checked={body.categorias.includes(c.categoriaId)}
                  onChange={() => toggleCategoria(c.categoriaId)}
                  className="accent-primary"
                />
                <span style={{ paddingLeft: `${(c.nivel ?? 0) * 8}px` }}>
                  {c.nombre}
                </span>
              </label>
            ))}
          </div>
        )}
      </div>

      <div className="sm:col-span-2 mt-2">
        <span className="text-xs font-medium text-muted">
          {t("catalogo.promociones.campos.productosAplicables")}
        </span>
        <Input
          className="mt-1"
          placeholder={t("catalogo.promociones.campos.buscarProducto")}
          value={busquedaProducto}
          onChange={(e) => setBusquedaProducto(e.target.value)}
        />
        {productos.isLoading ? (
          <Spinner />
        ) : (
          <div className="mt-2 grid grid-cols-1 gap-2 sm:grid-cols-2">
            <div className="max-h-40 overflow-y-auto rounded-md border border-line p-2">
              {productos.data?.data.length === 0 && (
                <span className="text-xs text-muted">
                  {t("catalogo.promociones.campos.sinCoincidencias")}
                </span>
              )}
              {productos.data?.data
                .filter((p) => !body.productos.includes(p.productoId))
                .map((p) => (
                  <button
                    key={p.productoId}
                    type="button"
                    onClick={() => agregarProducto(p.productoId)}
                    className="block w-full rounded px-2 py-1 text-left text-xs hover:bg-primary-50"
                  >
                    <span className="font-mono text-muted">{p.codigo}</span> ·{" "}
                    {p.nombre}
                  </button>
                ))}
            </div>
            <div className="rounded-md border border-line p-2">
              <div className="mb-1 text-xs font-medium">
                {t("catalogo.promociones.campos.seleccionados")}
              </div>
              {productosSeleccionados.length === 0 && (
                <span className="text-xs text-muted">
                  {t("catalogo.promociones.campos.ninguno")}
                </span>
              )}
              {productosSeleccionados.map((p) => (
                <button
                  key={p.productoId}
                  type="button"
                  onClick={() => quitarProducto(p.productoId)}
                  className="block w-full rounded px-2 py-1 text-left text-xs hover:bg-red-50"
                >
                  <span className="font-mono text-muted">{p.codigo}</span> ·{" "}
                  {p.nombre}
                  <span className="ml-2 text-red-600">
                    {t("catalogo.promociones.campos.quitar")}
                  </span>
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      <div className="sm:col-span-2 mt-2 flex justify-end gap-2">
        <Button
          variant="ghost"
          type="button"
          disabled={guardando}
          onClick={cerrar}
        >
          {t("comun.cancelar")}
        </Button>
        <Button type="submit" disabled={guardando}>
          {guardando
            ? t("catalogo.promociones.guardando")
            : esEdicion
              ? t("catalogo.promociones.guardar")
              : t("catalogo.promociones.crear")}
        </Button>
      </div>
    </form>
  );
}

export default function PromocionesPage() {
  const t = useT();
  useDocumentTitle(t("catalogo.promociones.titulo"));
  const { error: mostrarError, success: mostrarExito } = useToast();
  const queryClient = useQueryClient();
  const puedeAdministrar = useTieneRol(["ADMINISTRADOR", "GERENTE"]);

  const [filtros, setFiltros] = useState<PromocionesFiltros>({
    page: 0,
    size: 15,
  });
  const [dialogo, setDialogo] = useState<"nuevo" | number | null>(null);
  const [eliminarPromo, setEliminarPromo] = useState<Promocion | null>(null);

  const lista = useQuery({
    queryKey: ["promociones", filtros],
    queryFn: () => apiPromociones(filtros),
  });

  const esEdicionDialogo = typeof dialogo === "number";
  const detalle = useQuery({
    queryKey: ["promocion", dialogo],
    queryFn: () => apiPromocion(dialogo as number),
    enabled: esEdicionDialogo,
  });

  const crear = useMutation({
    mutationFn: (b: PromocionRequest) => apiCrearPromocion(b),
    onSuccess: () => {
      mostrarExito(t("catalogo.promociones.mensajes.creada"));
      setDialogo(null);
      queryClient.invalidateQueries({ queryKey: ["promociones"] });
    },
    onError: (err) =>
      mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  });

  const actualizar = useMutation({
    mutationFn: ({ id, body }: { id: number; body: PromocionRequest }) =>
      apiActualizarPromocion(id, body),
    onSuccess: () => {
      mostrarExito(t("catalogo.promociones.mensajes.actualizada"));
      setDialogo(null);
      queryClient.invalidateQueries({ queryKey: ["promociones"] });
    },
    onError: (err) =>
      mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  });

  const eliminar = useMutation({
    mutationFn: (id: number) => apiEliminarPromocion(id),
    onSuccess: () => {
      mostrarExito(t("catalogo.promociones.mensajes.eliminada"));
      setEliminarPromo(null);
      queryClient.invalidateQueries({ queryKey: ["promociones"] });
    },
    onError: (err) =>
      mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  });

  const columnas: Columna<Promocion>[] = useMemo(
    () => [
      { key: "nombre", header: t("comun.nombre"), render: (v) => v.nombre },
      {
        key: "tipo",
        header: t("comun.tipo"),
        render: (v) => (
          <Badge tone="default">
            {t(`catalogo.promociones.tipos.${v.tipo}` as never)}
          </Badge>
        ),
      },
      {
        key: "valor",
        header: t("catalogo.promociones.campos.valor"),
        render: (v) => {
          let contenido;
          if (v.tipo === "PRECIO_ESPECIAL")
            contenido = `$${(v.precioEspecial ?? 0).toFixed(2)}`;
          else if (v.tipo === "NXM")
            contenido = t("catalogo.promociones.campos.llevaPagaLabel", {
              lleva: v.lleva ?? 0,
              paga: v.paga ?? 0,
            });
          else if (v.valorPct) contenido = `${v.valorPct}%`;
          else if (v.valorMonto) contenido = `$${v.valorMonto.toFixed(2)}`;
          else contenido = "—";
          return <Badge tone="danger">{contenido}</Badge>;
        },
      },
      {
        key: "vigencia",
        header: t("catalogo.promociones.campos.vigencia"),
        render: (v) =>
          `${formatoFechaHora(v.vigenciaDesde)}${v.vigenciaHasta ? ` → ${formatoFechaHora(v.vigenciaHasta)}` : ""}`,
      },
      {
        key: "estado",
        header: t("comun.estado"),
        render: (v) => <Badge tone={estadoTone(v.estado)}>{v.estado}</Badge>,
      },
      {
        key: "alcance",
        header: t("catalogo.promociones.campos.alcance"),
        render: (v) => (
          <span className="text-xs text-muted">
            {v.productos.length} prod · {v.categorias.length} cat
          </span>
        ),
      },
      {
        key: "usos",
        header: t("catalogo.promociones.campos.usos"),
        render: (v) => (
          <Badge tone="default">
            {v.maxUsosTotal
              ? t("catalogo.promociones.campos.usosDe", {
                  actual: v.usosActual,
                  max: v.maxUsosTotal,
                })
              : `${v.usosActual}`}
          </Badge>
        ),
      },
      {
        key: "acc",
        header: t("comun.acciones"),
        align: "right",
        render: (v) =>
          puedeAdministrar ? (
            <div className="flex justify-end gap-1">
              <button
                type="button"
                aria-label={t("catalogo.promociones.aria.editar", {
                  nombre: v.nombre,
                })}
                className="rounded p-1.5 text-muted hover:bg-primary-50 hover:text-primary"
                onClick={() => setDialogo(v.promocionId)}
              >
                <Edit className="h-4 w-4" />
              </button>
              <button
                type="button"
                aria-label={t("catalogo.promociones.aria.eliminar", {
                  nombre: v.nombre,
                })}
                className="rounded p-1.5 text-muted hover:bg-red-50 hover:text-red-600 disabled:cursor-not-allowed disabled:opacity-40"
                disabled={v.usosActual > 0}
                title={
                  v.usosActual > 0
                    ? t("catalogo.promociones.mensajes.noSePuedeEliminarUsos")
                    : t("catalogo.promociones.aria.eliminarHelp")
                }
                onClick={() => setEliminarPromo(v)}
              >
                <Trash2 className="h-4 w-4" />
              </button>
            </div>
          ) : null,
      },
    ],
    [puedeAdministrar, t],
  );

  const esDialogoEdicion = typeof dialogo === "number";
  const inicialForm: Promocion | undefined = esDialogoEdicion
    ? detalle.data
    : undefined;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-semibold text-ink">
            <Percent className="h-6 w-6 text-primary" />{" "}
            {t("catalogo.promociones.titulo")}
          </h1>
          <p className="text-sm text-muted">
            {t("catalogo.promociones.subtitulo")}
          </p>
        </div>
        {puedeAdministrar && (
          <Button onClick={() => setDialogo("nuevo")}>
            <Tag className="h-4 w-4" /> {t("catalogo.promociones.nueva")}
          </Button>
        )}
      </div>

      <Card>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-4">
          <Input
            label={t("catalogo.promociones.filtros.nombre")}
            placeholder={t("catalogo.promociones.filtros.placeholder")}
            value={filtros.nombre ?? ""}
            onChange={(e) =>
              setFiltros((f) => ({ ...f, nombre: e.target.value, page: 0 }))
            }
          />
          <Select
            label={t("catalogo.promociones.filtros.tipo")}
            value={filtros.tipo ?? ""}
            onChange={(e) =>
              setFiltros((f) => ({
                ...f,
                tipo: e.target.value || undefined,
                page: 0,
              }))
            }
          >
            <option value="">{t("catalogo.promociones.filtros.todos")}</option>
            {TIPOS.map((tt) => (
              <option key={tt.value} value={tt.value}>
                {t(`catalogo.promociones.tipos.${tt.value}` as never)}
              </option>
            ))}
          </Select>
          <Select
            label={t("catalogo.promociones.filtros.estado")}
            value={filtros.estado ?? ""}
            onChange={(e) =>
              setFiltros((f) => ({
                ...f,
                estado: e.target.value || undefined,
                page: 0,
              }))
            }
          >
            <option value="">{t("catalogo.promociones.filtros.todos")}</option>
            {ESTADOS.map((e) => (
              <option key={e} value={e}>
                {e}
              </option>
            ))}
          </Select>
          <div className="flex items-end">
            <Button
              variant="ghost"
              onClick={() => setFiltros({ page: 0, size: filtros.size ?? 15 })}
            >
              {t("catalogo.promociones.filtros.limpiar")}
            </Button>
          </div>
        </div>
      </Card>

      <DataTable
        columnas={columnas}
        items={lista.data?.data}
        loading={lista.isLoading}
        rowKey={(v) => v.promocionId}
        emptyTitle={t("catalogo.promociones.sinResultados")}
        emptyDescripcion={t("catalogo.promociones.sinResultadosDesc")}
      />

      {lista.data && (
        <Pagination
          meta={lista.data.meta}
          onPage={(p) => setFiltros((f) => ({ ...f, page: p }))}
        />
      )}

      <Dialog
        open={dialogo !== null}
        onClose={() => {
          if (!crear.isPending && !actualizar.isPending) setDialogo(null);
        }}
        title={
          esDialogoEdicion
            ? t("catalogo.promociones.editar")
            : t("catalogo.promociones.nueva")
        }
        width="max-w-3xl"
      >
        {esDialogoEdicion && !detalle.data ? (
          <Spinner />
        ) : (
          <PromocionForm
            key={inicialForm?.promocionId ?? "nuevo"}
            guardando={crear.isPending || actualizar.isPending}
            cerrar={() => {
              setDialogo(null);
            }}
            inicial={inicialForm}
            enGuardar={(body) => {
              if (esDialogoEdicion) {
                actualizar.mutate({ id: dialogo as number, body });
              } else {
                crear.mutate(body);
              }
            }}
          />
        )}
      </Dialog>

      <ConfirmDialog
        open={eliminarPromo !== null}
        title={t("catalogo.promociones.dialog.confirmar")}
        confirmLabel={t("comun.si")}
        busy={eliminar.isPending}
        onCancel={() => setEliminarPromo(null)}
        onConfirm={() =>
          eliminarPromo && eliminar.mutate(eliminarPromo.promocionId)
        }
      >
        <p className="text-sm text-ink">
          {t("catalogo.promociones.dialog.pregunta", {
            nombre: eliminarPromo ? `“${eliminarPromo.nombre}”` : "",
          })}
        </p>
      </ConfirmDialog>
    </div>
  );
}
