import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plug, Printer, Save, Settings, Store } from "lucide-react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { esApiError } from "@/lib/api/client";
import { apiGetTicketConfig, apiPutTicketConfig } from "@/lib/api/ticketConfig";
import type { TicketConfig } from "@/lib/api/types";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Input, Select } from "@/components/ui/Input";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { useAuthStore } from "@/store/auth";
import { TicketPreview, printTicketById } from "./TicketPreview";
import { buildEscPosTicket } from "@/lib/print/escpos";
import {
  disconnect,
  ensureConnected,
  getSilentEnabled,
  isSerialSupported,
  printViaSerial,
  requestSerialPort,
  setSilentEnabled,
} from "@/lib/print/serial";

function Toggle({ label, value, onChange }: { label: string; value: boolean; onChange: (v: boolean) => void }) {
  return (
    <label className="flex items-center justify-between gap-2 rounded-md border border-line px-3 py-2 text-sm">
      <span className="font-medium text-ink">{label}</span>
      <input type="checkbox" checked={value} onChange={(e) => onChange(e.target.checked)} className="h-4 w-4 accent-primary" />
    </label>
  );
}

export default function ConfiguracionPage() {
  useDocumentTitle("Configuración");
  const { error: mostrarError, success: mostrarExito } = useToast();
  const queryClient = useQueryClient();
  const usuario = useAuthStore((s) => s.usuario);

  const { data, isLoading, error } = useQuery({
    queryKey: ["ticket-config"],
    queryFn: () => apiGetTicketConfig(),
  });

  const [form, setForm] = useState<TicketConfig | null>(null);
  const [silentEnabled, setSilentLocal] = useState(() => getSilentEnabled());
  const [serialConnected, setSerialConnected] = useState(false);
  const [serialBusy, setSerialBusy] = useState(false);

  useEffect(() => {
    if (data) setForm(data);
  }, [data]);

  useEffect(() => {
    // Restaurar estado de conexión silenciosa al cargar (si ya había permiso)
    if (!isSerialSupported()) return;
    ensureConnected()
      .then((p) => setSerialConnected(!!p))
      .catch(() => setSerialConnected(false));
  }, []);

  const mutate = useMutation({
    mutationFn: () =>
      apiPutTicketConfig({
        logotipoUrl: form?.logotipoUrl,
        mostrarLogotipo: form?.mostrarLogotipo,
        nombreNegocio: form?.nombreNegocio,
        direccion: form?.direccion,
        cp: form?.cp,
        rfc: form?.rfc,
        telefono: form?.telefono,
        email: form?.email,
        sitioWeb: form?.sitioWeb,
        tituloDocumento: form?.tituloDocumento,
        mostrarDatosCliente: form?.mostrarDatosCliente,
        mostrarNumeroFactura: form?.mostrarNumeroFactura,
        mostrarCaja: form?.mostrarCaja,
        mostrarFechaHora: form?.mostrarFechaHora,
        mostrarVendedor: form?.mostrarVendedor,
        mostrarDesgloseIva: form?.mostrarDesgloseIva,
        mostrarDescuento: form?.mostrarDescuento,
        mostrarCambio: form?.mostrarCambio,
        mensajePie: form?.mensajePie,
        pieSecundario: form?.pieSecundario,
        anchoPapelMm: form?.anchoPapelMm,
        fontSizePt: form?.fontSizePt,
        almacenId: null,
      }),
    onSuccess: (saved) => {
      queryClient.setQueryData(["ticket-config"], saved);
      setForm(saved);
      mostrarExito("Configuración guardada.");
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  });

  if (isLoading) return <Spinner label="Cargando configuración…" />;
  if (error) return <p className="text-sm text-red-600">{esApiError(error) ? error.mensajeParaUsuario() : String(error)}</p>;
  if (!form) return null;

  const set = <K extends keyof TicketConfig>(k: K, v: TicketConfig[K]) => setForm((prev) => (prev ? { ...prev, [k]: v } : prev));

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="flex items-center gap-2 text-xl font-bold text-ink">
            <Settings className="h-5 w-5 text-primary" /> Configuración
          </h1>
          <p className="text-sm text-muted">Administra los datos que aparecen en el ticket. Vista previa en vivo a la derecha.</p>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => printTicketById()}>Probar impresión</Button>
          <Button hotkey="Ctrl+Enter" onClick={() => mutate.mutate()} disabled={mutate.isPending}>
            <Save className="h-4 w-4" /> {mutate.isPending ? "Guardando…" : "Guardar"}
          </Button>
        </div>
      </header>

      <div className="grid gap-4 lg:grid-cols-5">
        <div className="space-y-4 lg:col-span-3">
          <Card titulo="Encabezado - Logotipo / Empresa">
            <div className="grid gap-3">
              <Input label="Nombre o razón social" required value={form.nombreNegocio} onChange={(e) => set("nombreNegocio", e.target.value)} />
              <div className="grid gap-3 sm:grid-cols-2">
                <Input label="Dirección" value={form.direccion ?? ""} onChange={(e) => set("direccion", e.target.value || null)} placeholder="C/Jesús Untubre, 2 40006 Segovia" />
                <Input label="CP" value={form.cp ?? ""} onChange={(e) => set("cp", e.target.value || null)} />
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <Input label="CIF / RFC empresa" value={form.rfc ?? ""} onChange={(e) => set("rfc", e.target.value.toUpperCase() || null)} placeholder="A12345678" />
                <Input label="Teléfono" value={form.telefono ?? ""} onChange={(e) => set("telefono", e.target.value || null)} />
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <Input label="Email" value={form.email ?? ""} onChange={(e) => set("email", e.target.value || null)} />
                <Input label="Sitio web" value={form.sitioWeb ?? ""} onChange={(e) => set("sitioWeb", e.target.value || null)} placeholder="tiendadelaesquina.pro" />
              </div>
              <Input label="URL logotipo" value={form.logotipoUrl ?? ""} onChange={(e) => set("logotipoUrl", e.target.value || null)} placeholder="https://..." hint="PNG/JPG 200x80 recomendado" />
              <Toggle label="Mostrar logotipo" value={!!form.mostrarLogotipo} onChange={(v) => set("mostrarLogotipo", v)} />
            </div>
          </Card>

          <Card titulo="Cuerpo del ticket">
            <div className="grid gap-3">
              <Input label="Título del documento" value={form.tituloDocumento} onChange={(e) => set("tituloDocumento", e.target.value)} placeholder="Factura simplificada" />
              <div className="grid gap-2 sm:grid-cols-2">
                <Toggle label="Datos del cliente" value={!!form.mostrarDatosCliente} onChange={(v) => set("mostrarDatosCliente", v)} />
                <Toggle label="Número de factura correlativo" value={!!form.mostrarNumeroFactura} onChange={(v) => set("mostrarNumeroFactura", v)} />
                <Toggle label="Caja en la que se hace la compra" value={!!form.mostrarCaja} onChange={(v) => set("mostrarCaja", v)} />
                <Toggle label="Fecha y hora" value={!!form.mostrarFechaHora} onChange={(v) => set("mostrarFechaHora", v)} />
                <Toggle label="Nombre del vendedor" value={!!form.mostrarVendedor} onChange={(v) => set("mostrarVendedor", v)} />
                <Toggle label="Desglose de impuestos" value={!!form.mostrarDesgloseIva} onChange={(v) => set("mostrarDesgloseIva", v)} />
                <Toggle label="Total de descuentos aplicados" value={!!form.mostrarDescuento} onChange={(v) => set("mostrarDescuento", v)} />
                <Toggle label="Cambio (si pago en efectivo)" value={!!form.mostrarCambio} onChange={(v) => set("mostrarCambio", v)} />
              </div>
              <p className="text-xs text-muted">Relación de artículos, unidades y precio / Total de la compra siempre visibles.</p>
            </div>
          </Card>

          <Card titulo="Pie del ticket y formato">
            <div className="grid gap-3">
              <Input label="Pie de ticket / mensaje principal" value={form.mensajePie ?? ""} onChange={(e) => set("mensajePie", e.target.value || null)} placeholder="30 DÍAS PARA DEVOLUCIONES..." />
              <Input label="Pie secundario" value={form.pieSecundario ?? ""} onChange={(e) => set("pieSecundario", e.target.value || null)} placeholder="T: 921047112" />
              <div className="grid gap-3 sm:grid-cols-2">
                <Select label="Ancho papel" value={form.anchoPapelMm} onChange={(e) => set("anchoPapelMm", Number(e.target.value) as 58 | 80)}>
                  <option value={80}>80mm (recomendado)</option>
                  <option value={58}>58mm</option>
                </Select>
                <Select label="Tamaño fuente" value={form.fontSizePt} onChange={(e) => set("fontSizePt", Number(e.target.value))}>
                  <option value={7}>7pt</option>
                  <option value={8}>8pt</option>
                  <option value={9}>9pt</option>
                  <option value={10}>10pt</option>
                  <option value={11}>11pt</option>
                  <option value={12}>12pt</option>
                </Select>
              </div>
            </div>
          </Card>

          <Card titulo="Impresora POS USB (background)">
            <div className="space-y-3">
              {!isSerialSupported() ? (
                <p className="rounded-md border border-amber-300 bg-amber-50 p-2 text-xs text-amber-900">
                  Web Serial no soportado. Usa Chrome/Edge desktop en HTTPS o localhost para impresión silenciosa USB.
                </p>
              ) : (
                <>
                  <div className="flex flex-wrap items-center gap-2">
                    <Button
                      variant={serialConnected ? "success" : "secondary"}
                      disabled={serialBusy}
                      onClick={async () => {
                        setSerialBusy(true);
                        try {
                          if (serialConnected) {
                            await disconnect();
                            setSerialConnected(false);
                            mostrarExito("Impresora desconectada.");
                          } else {
                            const port = await requestSerialPort();
                            if (port) {
                              // Abrir con 9600 baudios
                              const { openPort } = await import("@/lib/print/serial");
                              await openPort(port);
                              setSerialConnected(true);
                              mostrarExito("Impresora USB conectada.");
                            }
                          }
                        } catch (e) {
                          mostrarError(e instanceof Error ? e.message : String(e));
                        } finally {
                          setSerialBusy(false);
                        }
                      }}
                    >
                      <Plug className="h-4 w-4" /> {serialConnected ? "Desconectar" : "Conectar impresora USB"}
                    </Button>
                    <span className={`text-xs ${serialConnected ? "text-green-700" : "text-muted"}`}>
                      {serialConnected ? "● Conectada (background listo)" : "○ Desconectada — se usará diálogo del navegador"}
                    </span>
                  </div>
                  <label className="flex items-center justify-between gap-2 rounded-md border border-line px-3 py-2 text-sm">
                    <span className="font-medium text-ink">Impresión silenciosa automática tras venta</span>
                    <input
                      type="checkbox"
                      checked={silentEnabled}
                      onChange={(e) => {
                        const v = e.target.checked;
                        setSilentLocal(v);
                        setSilentEnabled(v);
                      }}
                      className="h-4 w-4 accent-primary"
                    />
                  </label>
                  <div className="flex flex-wrap gap-2">
                    <Button
                      variant="secondary"
                      disabled={!serialConnected || serialBusy}
                      onClick={async () => {
                        if (!form) return;
                        setSerialBusy(true);
                        try {
                          const bytes = buildEscPosTicket({
                            config: form,
                            venta: {
                              ventaId: 999,
                              folio: "PRUEBA-001",
                              clienteId: null,
                              clienteNombre: null,
                              cliente: null,
                              almacenId: 1,
                              almacenNombre: "Principal",
                              fecha: new Date().toISOString(),
                              fechaLocal: new Date().toISOString().slice(0, 10),
                              formaPagoId: 1,
                              formaPagoNombre: "Efectivo",
                              ivaTasa: 16,
                              ivaIncluido: true,
                              subtotal: 86.21,
                              iva: 13.79,
                              descuentoTotal: 0,
                              total: 100,
                              estado: "COMPLETADA",
                              usuarioId: 1,
                              turnoCajaId: 1,
                              notas: null,
                              detalles: [
                                { ventaDetalleId: 1, productoId: 1, productoNombre: "Prueba 80mm ticket", cantidad: 1, precioUnitario: 100, costoUnitario: 0, descuentoLinea: 0, totalLinea: 100 },
                              ],
                              pagos: [{ pagoClienteId: 1, formaPagoId: 1, referencia: "CONTADO", monto: 100, fecha: new Date().toISOString() }],
                            },
                            vendedorNombre: usuario?.username ?? "user",
                            cajaNombre: "Caja 2",
                            cliente: null,
                            montoEntregado: 100,
                          });
                          await printViaSerial(bytes);
                          mostrarExito("Ticket de prueba enviado por USB (background).");
                        } catch (e) {
                          mostrarError(e instanceof Error ? e.message : String(e));
                        } finally {
                          setSerialBusy(false);
                        }
                      }}
                    >
                      <Printer className="h-4 w-4" /> Probar impresión background
                    </Button>
                    <Button variant="ghost" onClick={() => printTicketById()}>
                      Probar con diálogo
                    </Button>
                  </div>
                  <p className="text-xs text-muted">Conectada una vez, queda autorizada por navegador. Al vender en POS, si silenciosa está activa, el ticket se imprime en background sin mostrar la ventana del navegador.</p>
                </>
              )}
            </div>
          </Card>

          <Card titulo="Información">
            <p className="flex items-center gap-2 text-sm text-muted">
              <Store className="h-4 w-4" /> Configuración global. Más adelante podrá personalizarse por almacén (almacenId).
            </p>
            {form.actualizadoEn && <p className="text-xs text-muted">Actualizado: {new Date(form.actualizadoEn).toLocaleString("es-MX")} {usuario?.username ? `por ${usuario.username}` : ""}</p>}
          </Card>
        </div>

        <div className="lg:col-span-2">
          <Card titulo="Vista previa 80mm" actions={<Button variant="ghost" size="sm" onClick={() => printTicketById()}>Imprimir prueba</Button>}>
            <div className="rounded-md bg-neutral-100 p-4">
              <TicketPreview config={form} vendedorNombre={usuario?.username ?? "user"} cajaNombre="Caja 2" />
            </div>
            <p className="mt-2 text-xs text-muted">Preview igual al ESC/POS que se enviará por USB.</p>
          </Card>
        </div>
      </div>
    </div>
  );
}
