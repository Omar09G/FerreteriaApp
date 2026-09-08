import type { ClienteVentaInfo, TicketConfig, Venta } from "@/lib/api/types";

const ESC = 0x1b;
const GS = 0x1d;

function enc(str: string): Uint8Array {
  // Normalizar acentos para impresoras CP437/CP858 básicas
  const norm = str
    .replace(/[áàäâ]/gi, (m) => ({ á: "a", à: "a", ä: "a", â: "a", Á: "A", À: "A", Ä: "A", Â: "A" }[m] ?? m))
    .replace(/[éèëê]/gi, (m) => ({ é: "e", è: "e", ë: "e", ê: "e", É: "E", È: "E", Ë: "E", Ê: "E" }[m] ?? m))
    .replace(/[íìïî]/gi, (m) => ({ í: "i", ì: "i", ï: "i", î: "i", Í: "I", Ì: "I", Ï: "I", Î: "I" }[m] ?? m))
    .replace(/[óòöô]/gi, (m) => ({ ó: "o", ò: "o", ö: "o", ô: "o", Ó: "O", Ò: "O", Ö: "O", Ô: "O" }[m] ?? m))
    .replace(/[úùüû]/gi, (m) => ({ ú: "u", ù: "u", ü: "u", û: "u", Ú: "U", Ù: "U", Ü: "U", Û: "U" }[m] ?? m))
    .replace(/ñ/gi, (m) => (m === "ñ" ? "n" : "N"))
    .replace(/[^ -~\n]/g, "?");
  return new TextEncoder().encode(norm);
}

function concat(...parts: Uint8Array[]): Uint8Array {
  const len = parts.reduce((s, p) => s + p.length, 0);
  const out = new Uint8Array(len);
  let o = 0;
  for (const p of parts) { out.set(p, o); o += p.length; }
  return out;
}

function cmd(...b: number[]): Uint8Array { return new Uint8Array(b); }

function align(n: 0 | 1 | 2): Uint8Array { return cmd(ESC, 0x61, n); }
function bold(on: boolean): Uint8Array { return cmd(ESC, 0x45, on ? 1 : 0); }
function line(text = ""): Uint8Array { return concat(enc(text + "\n")); }
function dashed(chars: number, char = "-"): Uint8Array { return line(char.repeat(chars)); }
function feed(n: number): Uint8Array { return cmd(ESC, 0x64, n); }
function cut(): Uint8Array { return cmd(GS, 0x56, 0x00); }
function init(): Uint8Array { return cmd(ESC, 0x40); }

function money(n: number | string | null | undefined): string {
  if (n == null || n === "") return "—";
  const v = typeof n === "string" ? Number(n) : n;
  if (Number.isNaN(v)) return "—";
  return `$${v.toFixed(2)}`;
}

function fmtFecha(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("es-MX");
}

export function buildEscPosTicket(opts: {
  config: TicketConfig;
  venta: Venta;
  vendedorNombre?: string | null;
  cajaNombre?: string | null;
  cliente?: ClienteVentaInfo | null;
  montoEntregado?: number | null;
}): Uint8Array {
  const { config, venta, vendedorNombre, cajaNombre, cliente, montoEntregado } = opts;
  const chars = config.anchoPapelMm === 58 ? 32 : 42;
  const parts: Uint8Array[] = [];

  parts.push(init());
  // Codepage PC858 (Western) si la impresora lo soporta
  parts.push(cmd(ESC, 0x74, 19));

  // Encabezado centrado
  parts.push(align(1), bold(true), line(config.nombreNegocio.toUpperCase()), bold(false));
  if (config.direccion) parts.push(line(`${config.direccion}${config.cp ? " " + config.cp : ""}`));
  if (config.rfc) parts.push(line(`RFC: ${config.rfc}`));
  if (config.telefono) parts.push(line(`Tel: ${config.telefono}`));
  if (config.sitioWeb) parts.push(line(config.sitioWeb));
  parts.push(dashed(chars));

  // Cliente
  if (config.mostrarDatosCliente) {
    parts.push(align(1), bold(true), line("Datos Del Cliente"), bold(false), align(0));
    if (cliente) {
      parts.push(line(cliente.razonSocial));
      if (cliente.rfc) parts.push(line(`Id fiscal ${cliente.rfc}`));
      if (cliente.curp) parts.push(line(`CURP ${cliente.curp}`));
      if (cliente.calle) parts.push(line(`${cliente.calle}${cliente.colonia ? ", " + cliente.colonia : ""}`));
      if (cliente.ciudadNombre || cliente.cp) parts.push(line([cliente.ciudadNombre, cliente.cp].filter(Boolean).join(" ")));
      if (cliente.telefono) parts.push(line(`T: ${cliente.telefono}`));
      if (cliente.email) parts.push(line(`Email: ${cliente.email}`));
    } else {
      parts.push(line("Consumidor final - Publico en general"));
    }
    parts.push(dashed(chars));
  }

  // Documento
  parts.push(align(1), bold(true), line(config.tituloDocumento), bold(false), align(0));
  if (config.mostrarNumeroFactura) parts.push(line(`Numero de Factura: ${venta.folio}`));
  if (config.mostrarCaja) parts.push(line(`Caja: ${cajaNombre ?? venta.almacenNombre ?? "—"}`));
  if (config.mostrarFechaHora) parts.push(line(`Fecha: ${fmtFecha(venta.fecha)}`));
  if (config.mostrarVendedor) parts.push(line(`Le ha atendido a usted: ${vendedorNombre ?? "user"}`));
  parts.push(cmd(ESC, 0x2d, 1), dashed(chars), cmd(ESC, 0x2d, 0)); // underline

  // Detalles
  // Header
  const colN = 3, colArt = chars - 3 - 9 - 9, colPrec = 9, colTot = 9;
  const header = `${"N.".padEnd(colN)}${"Articulo".padEnd(colArt)}${"Prec.".padStart(colPrec)}${"Total".padStart(colTot)}`;
  parts.push(line(header));
  parts.push(dashed(chars, "-"));
  venta.detalles.forEach((d, i) => {
    const n = String(i + 1).padEnd(colN);
    const nombre = (d.productoNombre ?? "").slice(0, colArt).padEnd(colArt);
    const prec = money(d.precioUnitario).padStart(colPrec);
    const tot = money(d.totalLinea).padStart(colTot);
    parts.push(line(`${n}${nombre}${prec}${tot}`));
    const qty = `  x${d.cantidad}`;
    const desc = Number(d.descuentoLinea) > 0 ? ` -desc ${money(d.descuentoLinea)}` : "";
    if (qty.trim() !== "x1" || desc) parts.push(line(qty + desc));
  });
  parts.push(dashed(chars, "-"));

  // Totales
  if (config.mostrarDescuento && Number(venta.descuentoTotal) > 0) {
    parts.push(align(2), line(`Descuento: -${money(venta.descuentoTotal)}`), align(0));
  }
  parts.push(align(2), bold(true), line(`Total (con impuestos): ${money(venta.total)}`), bold(false), align(0));

  // Impuestos
  if (config.mostrarDesgloseIva) {
    parts.push(dashed(chars));
    parts.push(line(`Impuesto  Base imp.      cuota`));
    const tasa = venta.ivaTasa != null ? `${Number(venta.ivaTasa).toFixed(2)}%` : "IVA";
    const base = money(venta.subtotal).padStart(12);
    const cuota = money(venta.iva).padStart(10);
    parts.push(line(`${tasa.padEnd(8)}${base}${cuota}`));
    if (!venta.ivaIncluido) parts.push(line("IVA no incluido en precios"));
  }

  // Pago / cambio
  parts.push(dashed(chars));
  const esEfectivo = venta.formaPagoNombre?.toLowerCase().includes("efectivo") ?? venta.formaPagoId === 1;
  const entregado = montoEntregado != null ? montoEntregado : (venta.pagos?.[0]?.monto ?? venta.total);
  const cambio = esEfectivo ? Math.max(0, entregado - venta.total) : 0;
  // Encabezado pago
  parts.push(line(`Pago      Total   Entregado  Devuelto`));
  const pagoTxt = (venta.formaPagoNombre ?? "—").slice(0, 8).padEnd(8);
  const totTxt = money(venta.total).padStart(8);
  const entTxt = money(entregado).padStart(10);
  const devTxt = config.mostrarCambio && esEfectivo ? money(cambio).padStart(9) : (config.mostrarCambio ? money(0).padStart(9) : "   —    ".padStart(9));
  parts.push(line(`${pagoTxt}${totTxt}${entTxt}${devTxt}`));

  // Pie
  if (config.mensajePie || config.pieSecundario) {
    parts.push(dashed(chars));
    parts.push(align(1));
    if (config.mensajePie) parts.push(bold(true), line(config.mensajePie.toUpperCase()), bold(false));
    if (config.pieSecundario) parts.push(line(config.pieSecundario));
    parts.push(bold(true), line(config.nombreNegocio), bold(false));
    if (config.sitioWeb) parts.push(line(config.sitioWeb));
    if (config.telefono) parts.push(line(`T: ${config.telefono}`));
    parts.push(align(0));
  }

  parts.push(feed(3), cut());
  return concat(...parts);
}
