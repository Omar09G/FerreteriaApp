import { useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Download, Upload } from "lucide-react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { esApiError } from "@/lib/api/client";
import {
  apiCargaMasivaProductos,
  apiCategoriasArbol,
  apiMarcas,
  apiUnidadesMedida,
} from "@/lib/api/catalogo";
import type {
  CargaMasivaFilaError,
  Categoria,
  ProductoRequest,
  TipoProducto,
} from "@/lib/api/types";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";

const COLUMNAS = [
  "codigo",
  "tipo",
  "nombre",
  "descripcion",
  "categoria",
  "marca",
  "unidadMedida",
  "costoActual",
  "precioMenudeo",
  "precioMayoreo",
  "aplicaIva",
  "codigosBarras",
] as const;

const TIPOS: TipoProducto[] = ["PRODUCTO", "SERVICIO", "HERRAMIENTA_RENTA"];

const EJEMPLO: Record<(typeof COLUMNAS)[number], string | number> = {
  codigo: "TAL-001",
  tipo: "PRODUCTO",
  nombre: "Taladro 1/2 pulg 750W",
  descripcion: "Taladro percutor uso profesional",
  categoria: "Herramientas",
  marca: "Acme",
  unidadMedida: "Pieza",
  costoActual: 100,
  precioMenudeo: 150,
  precioMayoreo: 140,
  aplicaIva: "SI",
  codigosBarras: "7501234567890; 7501234567891",
};

interface FilaVista {
  excel: number;
  cruda: Record<string, unknown>;
  errores: string[];
  lista: boolean;
}

function normaliza(valor: unknown): string {
  return String(valor ?? "")
    .trim()
    .toLowerCase();
}

function aplanarCategorias(nodos: Categoria[]): Categoria[] {
  const planas: Categoria[] = [];
  const visita = (lista: Categoria[]) => {
    for (const c of lista) {
      planas.push(c);
      if (c.hijos) visita(c.hijos);
    }
  };
  visita(nodos);
  return planas;
}

function numero(valor: unknown): number | null {
  if (valor === "" || valor == null) return null;
  const n = Number(valor);
  return Number.isFinite(n) ? n : NaN;
}

/** Códigos separados por ";" (ej. "750100; 750101"). Factor siempre 1. */
function barras(valor: unknown): { lista: string[]; errores: string[] } {
  const texto = String(valor ?? "").trim();
  if (!texto) return { lista: [], errores: [] };
  const lista = texto
    .split(";")
    .map((c) => c.trim())
    .filter((c) => c !== "");
  const errores: string[] = [];
  if (lista.some((c) => c.length > 50))
    errores.push("cada código de barras debe tener máximo 50 caracteres");
  const vistos = new Set<string>();
  if (lista.some((c) => !vistos.add(c.toLowerCase())))
    errores.push("códigos de barras duplicados en la fila");
  return { lista, errores };
}

export default function CargaMasivaDialog({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  useDocumentTitle("Carga masiva de productos");
  const { success: mostrarExito, error: mostrarError } = useToast();
  const queryClient = useQueryClient();
  const inputRef = useRef<HTMLInputElement>(null);
  const [filas, setFilas] = useState<FilaVista[]>([]);
  const [leyendo, setLeyendo] = useState(false);
  const [reporte, setReporte] = useState<{
    creados: number;
    errores: CargaMasivaFilaError[];
  } | null>(null);

  // Al cerrar, limpiar filas, reporte y file input: la próxima apertura
  // siempre parte limpia, sin errores u observaciones previas.
  const cerrar = () => {
    setFilas([]);
    setReporte(null);
    setLeyendo(false);
    if (inputRef.current) inputRef.current.value = "";
    onClose();
  };

  const { data: categorias } = useQuery({
    queryKey: ["catalogo", "categorias-arbol"],
    queryFn: apiCategoriasArbol,
    enabled: open,
  });
  const { data: marcas } = useQuery({
    queryKey: ["catalogo", "marcas"],
    queryFn: apiMarcas,
    enabled: open,
  });
  const { data: unidades } = useQuery({
    queryKey: ["catalogo", "unidades"],
    queryFn: apiUnidadesMedida,
    enabled: open,
  });

  const porNombre = useMemo(() => {
    const mapa = new Map<string, number>();
    const clave = new Map<string, number>();
    for (const c of aplanarCategorias(categorias ?? []))
      mapa.set(`cat:${normaliza(c.nombre)}`, c.categoriaId);
    for (const m of marcas ?? []) mapa.set(`mar:${normaliza(m.nombre)}`, m.marcaId);
    for (const u of unidades ?? []) {
      mapa.set(`uni:${normaliza(u.nombre)}`, u.unidadId);
      clave.set(`uni:${normaliza(u.clave)}`, u.unidadId);
    }
    return { mapa, clave };
  }, [categorias, marcas, unidades]);

  const descargarPlantilla = async () => {
    const XLSX = await import("xlsx");
    const libro = XLSX.utils.book_new();
    const hoja = XLSX.utils.json_to_sheet([EJEMPLO], { header: [...COLUMNAS] });
    XLSX.utils.book_append_sheet(libro, hoja, "productos");
    XLSX.writeFile(libro, "plantilla-productos.xlsx");
  };

  const leerArchivo = async (archivo: File) => {
    setLeyendo(true);
    setReporte(null);
    try {
      const XLSX = await import("xlsx");
      const buf = await archivo.arrayBuffer();
      const libro = XLSX.read(buf, { type: "array" });
      const nombreHoja = libro.SheetNames[0];
      if (!nombreHoja) throw new Error("libro sin hojas");
      const crudas = XLSX.utils.sheet_to_json<Record<string, unknown>>(
        libro.Sheets[nombreHoja],
        { defval: "" },
      );
      if (crudas.length === 0)
        throw new Error("la hoja no contiene filas de datos");
      const encabezados = Object.keys(crudas[0]);
      if (!COLUMNAS.some((c) => encabezados.includes(c)))
        throw new Error(
          `encabezados no reconocidos (${encabezados.join(", ")}). Descarga la plantilla.`,
        );
      const vistas: FilaVista[] = crudas.map((cruda, i) => {
        const errores: string[] = [];
        const tipo = String(cruda.tipo ?? "").trim().toUpperCase();
        if (!TIPOS.includes(tipo as TipoProducto))
          errores.push(`tipo debe ser ${TIPOS.join("/")}`);
        if (!String(cruda.nombre ?? "").trim()) errores.push("nombre requerido");
        const categoriaId = porNombre.mapa.get(`cat:${normaliza(cruda.categoria)}`);
        if (cruda.categoria && categoriaId == null)
          errores.push(`categoría desconocida: ${cruda.categoria}`);
        if (!cruda.categoria) errores.push("categoría requerida");
        const marcaId = cruda.marca
          ? porNombre.mapa.get(`mar:${normaliza(cruda.marca)}`)
          : undefined;
        if (cruda.marca && marcaId == null)
          errores.push(`marca desconocida: ${cruda.marca}`);
        const unidadId =
          porNombre.mapa.get(`uni:${normaliza(cruda.unidadMedida)}`) ??
          porNombre.clave.get(`uni:${normaliza(cruda.unidadMedida)}`);
        if (!cruda.unidadMedida) errores.push("unidad requerida");
        else if (unidadId == null)
          errores.push(`unidad desconocida: ${cruda.unidadMedida}`);
        for (const campo of [
          "costoActual",
          "precioMenudeo",
          "precioMayoreo",
        ] as const) {
          const n = numero(cruda[campo]);
          if (n !== null && (Number.isNaN(n) || n < 0))
            errores.push(`${campo} debe ser número ≥ 0`);
        }
        errores.push(...barras(cruda.codigosBarras).errores);
        return {
          excel: i + 2,
          cruda,
          errores,
          lista: errores.length === 0,
        };
      });
      setFilas(vistas);
    } catch (e) {
      setFilas([]);
      mostrarError(
        e instanceof Error
          ? `No se pudo leer el archivo: ${e.message}`
          : "No se pudo leer el archivo .xlsx",
      );
    } finally {
      setLeyendo(false);
    }
  };

  const validas = filas.filter((f) => f.lista);
  const mutation = useMutation({
    mutationFn: async () => {
      const items: ProductoRequest[] = validas.map((f) => {
        const categoriaId = porNombre.mapa.get(`cat:${normaliza(f.cruda.categoria)}`)!;
        const marcaId = f.cruda.marca
          ? (porNombre.mapa.get(`mar:${normaliza(f.cruda.marca)}`) ?? null)
          : null;
        const unidadMedidaId = (porNombre.mapa.get(
          `uni:${normaliza(f.cruda.unidadMedida)}`,
        ) ??
          porNombre.clave.get(`uni:${normaliza(f.cruda.unidadMedida)}`))!;
        const num = (campo: string) => {
          const n = numero(f.cruda[campo]);
          return n == null || Number.isNaN(n) ? undefined : n;
        };
        const iva = String(f.cruda.aplicaIva ?? "").trim().toUpperCase();
        return {
          codigo: String(f.cruda.codigo ?? "").trim() || undefined,
          tipo: String(f.cruda.tipo).trim().toUpperCase() as TipoProducto,
          nombre: String(f.cruda.nombre).trim(),
          descripcion: String(f.cruda.descripcion ?? "").trim() || undefined,
          categoriaId,
          marcaId,
          unidadMedidaId,
          costoActual: num("costoActual") ?? undefined,
          precioMenudeo: num("precioMenudeo") ?? undefined,
          precioMayoreo: num("precioMayoreo") ?? undefined,
          aplicaIva: iva === "" ? undefined : !["NO", "0", "FALSE"].includes(iva),
          codigosBarras: barras(f.cruda.codigosBarras).lista.map((codigo) => ({
            codigo,
          })),
        };
      });
      // fila del backend = posición en el envío; se mapea a fila Excel.
      const enviadas = validas.map((f) => f.excel);
      const resp = await apiCargaMasivaProductos(items);
      return {
        creados: resp.creados.length,
        errores: [
          ...filas
            .filter((f) => !f.lista)
            .map((f) => ({
              fila: f.excel,
              codigo: "VALIDACION_CLIENTE",
              mensaje: f.errores.join("; "),
            })),
          ...resp.errores.map((e) => ({
            fila: enviadas[e.fila - 1] ?? e.fila,
            codigo: e.codigo,
            mensaje: e.mensaje,
          })),
        ],
      };
    },
    onSuccess: (r) => {
      queryClient.invalidateQueries({ queryKey: ["productos"] });
      if (r.errores.length === 0) {
        // Carga limpia: cerrar y avisar; cerrar() ya limpia el estado.
        mostrarExito(`${r.creados} productos creados.`);
        cerrar();
      } else {
        // Con errores por fila se queda abierto para ver el reporte.
        setReporte(r);
        mostrarExito(`${r.creados} creados, ${r.errores.length} con error.`);
      }
    },
    onError: (err) =>
      mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  });

  return (
    <Dialog open={open} onClose={cerrar} title="Carga masiva de productos" width="max-w-3xl">
      <div className="space-y-4">
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="ghost" onClick={descargarPlantilla}>
            <Download className="h-4 w-4" /> Descargar plantilla
          </Button>
          <Button type="button" onClick={() => inputRef.current?.click()}>
            <Upload className="h-4 w-4" /> Elegir archivo .xlsx
          </Button>
          <input
            ref={inputRef}
            type="file"
            accept=".xlsx,.xls"
            className="hidden"
            onChange={(e) => {
              const archivo = e.target.files?.[0];
              if (archivo) void leerArchivo(archivo);
              e.target.value = "";
            }}
          />
        </div>

        {leyendo && <Spinner label="Leyendo archivo…" />}

        {filas.length > 0 && (
          <p className="text-sm text-muted">
            {validas.length} de {filas.length} filas listas para enviar.
          </p>
        )}

        {filas.length > 0 && (
          <ul className="max-h-56 space-y-1 overflow-auto rounded-md border border-line p-2">
            {filas.map((f) => (
              <li
                key={f.excel}
                className={`text-xs ${f.lista ? "text-ink" : "text-red-600"}`}
              >
                Fila {f.excel}: {String(f.cruda.nombre || "(sin nombre)")}
                {f.errores.length > 0 && ` — ${f.errores.join("; ")}`}
              </li>
            ))}
          </ul>
        )}

        {mutation.isPending && <Spinner label="Creando productos…" />}

        {reporte && (
          <div className="rounded-md border border-line p-3 text-sm">
            <p className="font-semibold text-ink">
              Resultado: {reporte.creados} creados
              {reporte.errores.length > 0 && `, ${reporte.errores.length} con error`}
            </p>
            {reporte.errores.length > 0 && (
              <ul className="mt-2 max-h-48 space-y-1 overflow-auto">
                {reporte.errores.map((e, i) => (
                  <li key={i} className="text-xs text-muted">
                    Fila {e.fila}: [{e.codigo}] {e.mensaje}
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        <div className="flex justify-end gap-2">
          <Button
            type="button"
            disabled={validas.length === 0 || mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            Crear {validas.length} productos
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
