import type { ClienteVentaInfo, TicketConfig, Venta } from "@/lib/api/types";
import { formatoFechaHora, formatoMoneda } from "@/lib/format";

interface Props {
  config: TicketConfig;
  venta?: Venta | null;
  vendedorNombre?: string | null;
  cajaNombre?: string | null;
  clienteOverride?: ClienteVentaInfo | null;
  montoEntregado?: number | null;
}

const MOCK_CLIENTE: ClienteVentaInfo = {
  clienteId: 1,
  razonSocial: "Miguel Dominguez",
  nombreComercial: null,
  rfc: "16618263Z",
  curp: null,
  regimenFiscal: null,
  telefono: "63883532",
  whatsapp: null,
  email: "miqueldomingu445@gmail.com",
  calle: "C/Carretera de Villacastin 56A 2ºC",
  colonia: null,
  cp: "40006",
  ciudadNombre: "Segovia",
};

const MOCK_VENTA: Venta = {
  ventaId: 999,
  folio: "V-40006-1015",
  clienteId: 1,
  clienteNombre: "Miguel Dominguez",
  cliente: MOCK_CLIENTE,
  almacenId: 1,
  almacenNombre: "Principal",
  fecha: new Date().toISOString(),
  fechaLocal: new Date().toISOString().slice(0, 10),
  formaPagoId: 1,
  formaPagoNombre: "Efectivo",
  ivaTasa: 16,
  ivaIncluido: true,
  subtotal: 145.45,
  iva: 30.55,
  descuentoTotal: 11.0,
  total: 176.0,
  estado: "COMPLETADA",
  usuarioId: 1,
  turnoCajaId: 1,
  notas: null,
  detalles: [
    { ventaDetalleId: 1, productoId: 1, productoNombre: "Gucci", cantidad: 1, precioUnitario: 10.0, costoUnitario: 6, descuentoLinea: 0, totalLinea: 10 },
    { ventaDetalleId: 2, productoId: 2, productoNombre: "1950s style tea party dress", cantidad: 1, precioUnitario: 58.0, costoUnitario: 30, descuentoLinea: 0, totalLinea: 58 },
    { ventaDetalleId: 3, productoId: 3, productoNombre: "Lady vintage hepburm red rose womens dress", cantidad: 1, precioUnitario: 99.0, costoUnitario: 50, descuentoLinea: 0, totalLinea: 99 },
  ],
  pagos: [{ pagoClienteId: 1, formaPagoId: 1, referencia: null, monto: 176, fecha: new Date().toISOString() }],
};

export function TicketPreview({ config, venta, vendedorNombre, cajaNombre, clienteOverride, montoEntregado }: Props) {
  const v = venta ?? MOCK_VENTA;
  const width = config.anchoPapelMm === 58 ? "208px" : "302px";
  const fontSize = `${config.fontSizePt}pt`;

  const cliente = clienteOverride ?? v.cliente ?? (v.clienteId ? { razonSocial: v.clienteNombre ?? `Cliente #${v.clienteId}`, rfc: null, calle: null, colonia: null, cp: null, ciudadNombre: null, telefono: null, email: null } as unknown as ClienteVentaInfo : null);

  const esEfectivo = v.formaPagoNombre?.toLowerCase().includes("efectivo") ?? v.formaPagoId === 1;
  const entregado = montoEntregado != null ? montoEntregado : (v.pagos?.[0]?.monto ?? v.total);
  const cambio = esEfectivo ? Math.max(0, entregado - v.total) : 0;

  return (
    <div
      id="ticket-preview"
      style={{ width, fontSize, lineHeight: 1.35 }}
      className="mx-auto bg-white font-mono text-[11px] leading-tight text-black shadow-md print:shadow-none"
    >
      <div className="p-3">
        {config.mostrarLogotipo && config.logotipoUrl && (
          <div className="mb-2 flex justify-center">
            <img src={config.logotipoUrl} alt="Logotipo" className="max-h-16 max-w-[120px] object-contain" />
          </div>
        )}
        <div className="text-center">
          <p className="text-[11px] font-bold uppercase tracking-wide">{config.nombreNegocio}</p>
          {config.direccion && <p className="text-[10px]">{config.direccion}{config.cp ? ` · ${config.cp}` : ""}</p>}
          {config.rfc && <p className="text-[10px]">RFC: {config.rfc}</p>}
          {config.telefono && <p className="text-[10px]">Tel: {config.telefono}</p>}
          {config.sitioWeb && <p className="text-[10px]">{config.sitioWeb}</p>}
        </div>

        <div className="my-2 border-t border-dashed border-black" />

        {config.mostrarDatosCliente && (
          <div className="text-[10px]">
            <p className="text-center font-bold">Datos Del Cliente</p>
            {cliente ? (
              <>
                <p className="font-semibold">{cliente.razonSocial}</p>
                {cliente.rfc && <p>Id fiscal {cliente.rfc}</p>}
                {cliente.curp && <p>CURP {cliente.curp}</p>}
                {cliente.calle && <p>{cliente.calle}{cliente.colonia ? `, ${cliente.colonia}` : ""}</p>}
                {(cliente.ciudadNombre || cliente.cp) && (
                  <p>{[cliente.ciudadNombre, cliente.cp].filter(Boolean).join(" ")}</p>
                )}
                {cliente.telefono && <p>T: {cliente.telefono}</p>}
                {cliente.email && <p className="truncate">Email: {cliente.email}</p>}
              </>
            ) : (
              <p>Consumidor final — Público en general</p>
            )}
          </div>
        )}

        {config.mostrarDatosCliente && <div className="my-2 border-t border-dashed border-black" />}

        <div className="text-center text-[11px] font-bold">{config.tituloDocumento}</div>

        <div className="mt-1 space-y-0.5 text-[10px]">
          {config.mostrarNumeroFactura && <p>Número de Factura: {v.folio}</p>}
          {config.mostrarCaja && <p>Caja: {cajaNombre ?? `Caja 2`}</p>}
          {config.mostrarFechaHora && <p>Fecha: {formatoFechaHora(v.fecha)}</p>}
          {config.mostrarVendedor && <p>Le ha atendido a usted: {vendedorNombre ?? "user"}</p>}
        </div>

        <div className="my-2 border-t border-black" />

        <table className="w-full text-[10px]">
          <thead>
            <tr className="border-b border-black">
              <th className="py-1 text-left">N.</th>
              <th className="py-1 text-left">Artículos</th>
              <th className="py-1 text-right">Prec.</th>
              <th className="py-1 text-right">Total</th>
            </tr>
          </thead>
          <tbody>
            {v.detalles.map((d, i) => (
              <tr key={d.ventaDetalleId} className="border-b border-dotted border-neutral-300">
                <td className="py-0.5 align-top">{i + 1}</td>
                <td className="py-0.5">
                  <span className="line-clamp-2">{d.productoNombre}</span>
                  <div className="flex gap-1 text-[9px] text-neutral-600">
                    <span>×{d.cantidad}</span>
                    {Number(d.descuentoLinea) > 0 && (
                      <span className="text-red-600">-desc {formatoMoneda(d.descuentoLinea)}</span>
                    )}
                  </div>
                </td>
                <td className="py-0.5 text-right tabular-nums align-top">{formatoMoneda(d.precioUnitario)}</td>
                <td className="py-0.5 text-right tabular-nums align-top">{formatoMoneda(d.totalLinea)}</td>
              </tr>
            ))}
          </tbody>
        </table>

        <div className="mt-2 space-y-0.5 text-right text-[10px]">
          {config.mostrarDescuento && Number(v.descuentoTotal) > 0 && (
            <p>Descuento aplicado: -{formatoMoneda(v.descuentoTotal)}</p>
          )}
          <p className="font-bold">Total (con impuestos): {formatoMoneda(v.total)}</p>
        </div>

        {config.mostrarDesgloseIva && (
          <div className="mt-2 border-t border-black pt-1 text-[10px]">
            <div className="flex justify-between">
              <span>Impuesto</span>
              <span>Base imp.</span>
              <span>cuota</span>
            </div>
            <div className="flex justify-between tabular-nums">
              <span>{v.ivaTasa != null ? `${Number(v.ivaTasa).toFixed(2)}%` : "IVA"}</span>
              <span>{formatoMoneda(v.subtotal)}</span>
              <span>{formatoMoneda(v.iva)}</span>
            </div>
            {!v.ivaIncluido && <p className="text-[8px] text-neutral-500">IVA no incluido en precios</p>}
          </div>
        )}

        <div className="mt-2 border-t border-black pt-1 text-[10px]">
          <div className="flex justify-between font-bold">
            <span>Pago</span><span>Total</span><span>Entregado</span><span>Devuelto</span>
          </div>
          <div className="flex justify-between tabular-nums">
            <span>{v.formaPagoNombre.toLowerCase()}</span>
            <span>{formatoMoneda(v.total)}</span>
            <span>{formatoMoneda(entregado)}</span>
            <span>{config.mostrarCambio && esEfectivo ? formatoMoneda(cambio) : config.mostrarCambio ? formatoMoneda(0) : "—"}</span>
          </div>
          {esEfectivo && config.mostrarCambio && montoEntregado != null && entregado > v.total && (
            <p className="mt-0.5 text-right text-[9px] text-neutral-500">Cambio: {formatoMoneda(cambio)}</p>
          )}
        </div>

        {(config.mensajePie || config.pieSecundario) && (
          <>
            <div className="my-2 border-t border-dashed border-black" />
            <div className="text-center text-[10px]">
              {config.mensajePie && <p className="font-bold uppercase">{config.mensajePie}</p>}
              {config.pieSecundario && <p>{config.pieSecundario}</p>}
              <p className="mt-1 font-bold">{config.nombreNegocio}</p>
              {config.sitioWeb && <p>{config.sitioWeb}</p>}
              {config.telefono && <p>T: {config.telefono}</p>}
            </div>
          </>
        )}
      </div>
    </div>
  );
}

export function printTicketById(id: string = "ticket-preview") {
  const el = document.getElementById(id);
  if (!el) return;

  // Intento iframe oculto: no bloquea la ventana principal y no requiere popup.
  // Si el navegador bloquea iframe print, fallback a window.open con cierre seguro.
  const html = `<!doctype html><html><head><meta charset="utf-8"><title>Ticket</title><style>
    @page { size: 80mm auto; margin: 2mm; }
    * { box-sizing: border-box; }
    body { margin: 0; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 10px; color: #000; background:#fff; }
    table { width: 100%; border-collapse: collapse; }
    @media print { body { -webkit-print-color-adjust: exact; print-color-adjust: exact; } }
  </style></head><body>${el.outerHTML}</body></html>`;

  try {
    const iframe = document.createElement("iframe");
    iframe.style.position = "fixed";
    iframe.style.right = "0";
    iframe.style.bottom = "0";
    iframe.style.width = "0";
    iframe.style.height = "0";
    iframe.style.border = "0";
    iframe.setAttribute("aria-hidden", "true");
    document.body.appendChild(iframe);

    const doc = iframe.contentWindow?.document;
    if (!doc) throw new Error("no-iframe-doc");
    doc.open();
    doc.write(html);
    doc.close();

    const cleanup = () => {
      window.setTimeout(() => {
        if (iframe.parentNode) iframe.parentNode.removeChild(iframe);
      }, 500);
    };

    const doPrint = () => {
      try {
        iframe.contentWindow?.focus();
        iframe.contentWindow?.print();
      } catch {
        // fallback silencioso
      }
      // onafterprint no es fiable si el usuario cancela; limpiamos en ambos casos
      cleanup();
      // Doble seguridad: si el navegador no dispara afterprint, limpiar tras 2s
      window.setTimeout(cleanup, 2000);
    };

    // Esperar a que imágenes/fuentes carguen
    const img = doc.images;
    if (img.length === 0) {
      window.setTimeout(doPrint, 250);
    } else {
      let loaded = 0;
      const check = () => { loaded++; if (loaded >= img.length) window.setTimeout(doPrint, 100); };
      Array.from(img).forEach((i) => {
        if ((i as HTMLImageElement).complete) check();
        else { i.addEventListener("load", check); i.addEventListener("error", check); }
      });
      window.setTimeout(doPrint, 1500);
    }

    // Seguridad: si el usuario cancela, el iframe se limpia igual y la pantalla NO queda pasmada
    iframe.contentWindow?.addEventListener("afterprint", cleanup);
    return;
  } catch {
    // Fallback ventana nueva con cierre garantizado aunque se cancele
  }

  const w = window.open("", "_blank", "width=400,height=700");
  if (!w) return;
  w.document.write(`<html><head><meta charset="utf-8"><title>Ticket</title><style>
    @page { size: 80mm auto; margin: 2mm; }
    body { margin: 0; font-family: ui-monospace, monospace; }
    table { width: 100%; border-collapse: collapse; }
  </style></head><body>${el.outerHTML}<script>
    function done(){ try{window.close();}catch(e){} }
    window.onafterprint = done;
    window.onload = function(){ window.print(); setTimeout(done, 1000); };
    setTimeout(done, 3000);
  <\/script></body></html>`);
  w.document.close();
}
