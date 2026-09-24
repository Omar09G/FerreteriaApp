import { describe, expect, it } from "vitest";
import { buildEscPosTicket } from "@/lib/print/escpos";
import type {
	ClienteVentaInfo,
	TicketConfig,
	Venta,
	VentaDetalle,
} from "@/lib/api/types";

function detalleBase(over: Partial<VentaDetalle> = {}): VentaDetalle {
	return {
		ventaDetalleId: 1,
		productoId: 10,
		productoNombre: "Martillo 16oz",
		cantidad: 1,
		precioUnitario: 150,
		costoUnitario: 100,
		descuentoLinea: 0,
		totalLinea: 150,
		...over,
	};
}

function configBase(over: Partial<TicketConfig> = {}): TicketConfig {
	return {
		ticketConfigId: 1,
		almacenId: 1,
		logotipoUrl: null,
		mostrarLogotipo: false,
		nombreNegocio: "Ferreteria El Martillo",
		direccion: "Av Juarez 123",
		cp: "44100",
		rfc: "XAXX010101000",
		telefono: "3331234567",
		email: null,
		sitioWeb: "www.example.com",
		tituloDocumento: "Ticket de venta",
		mostrarDatosCliente: true,
		mostrarNumeroFactura: true,
		mostrarCaja: true,
		mostrarFechaHora: true,
		mostrarVendedor: true,
		mostrarDesgloseIva: true,
		mostrarDescuento: true,
		mostrarCambio: true,
		mensajePie: "Gracias por su compra",
		pieSecundario: "Vuelva pronto",
		anchoPapelMm: 80,
		fontSizePt: 10,
		actualizadoEn: null,
		actualizadoPor: null,
		...over,
	};
}

function ventaBase(over: Partial<Venta> = {}): Venta {
	return {
		ventaId: 7,
		folio: "V-0007",
		clienteId: null,
		clienteNombre: null,
		cliente: null,
		almacenId: 1,
		almacenNombre: "Matriz",
		fecha: "2026-01-15T18:30:00Z",
		fechaLocal: "2026-01-15",
		formaPagoId: 1,
		formaPagoNombre: "Efectivo",
		ivaTasa: 16,
		ivaIncluido: true,
		subtotal: 100,
		iva: 16,
		descuentoTotal: 10,
		total: 106,
		estado: "COMPLETADA",
		usuarioId: 1,
		turnoCajaId: null,
		notas: null,
		detalles: [
			detalleBase(),
			detalleBase({
				ventaDetalleId: 2,
				productoNombre: "Clavos 2in (caja)",
				cantidad: 2,
				precioUnitario: 50,
				descuentoLinea: 5,
				totalLinea: 95,
			}),
		],
		pagos: [
			{
				pagoClienteId: 1,
				formaPagoId: 1,
				referencia: null,
				monto: 200,
				fecha: "2026-01-15T18:30:00Z",
			},
		],
		...over,
	};
}

function clienteBase(over: Partial<ClienteVentaInfo> = {}): ClienteVentaInfo {
	return {
		clienteId: 3,
		razonSocial: "Juan Perez",
		nombreComercial: null,
		rfc: "PEPJ800101AAA",
		curp: "PEPJ800101HJCRRN01",
		regimenFiscal: null,
		telefono: "3330001122",
		whatsapp: null,
		email: "juan@example.com",
		calle: "Calle Falsa 123",
		colonia: "Centro",
		cp: "44100",
		ciudadNombre: "Guadalajara",
		...over,
	};
}

const decode = (b: Uint8Array) => new TextDecoder().decode(b);

describe("buildEscPosTicket", () => {
	it("inicia con INIT + codepage y termina con feed + corte", () => {
		const out = buildEscPosTicket({
			config: configBase(),
			venta: ventaBase(),
		});
		// INIT: ESC @
		expect(out[0]).toBe(0x1b);
		expect(out[1]).toBe(0x40);
		// Codepage: ESC t 19
		expect(out[2]).toBe(0x1b);
		expect(out[3]).toBe(0x74);
		expect(out[4]).toBe(19);
		// CUT: GS V 0x00 al final
		expect(Array.from(out.slice(-3))).toEqual([0x1d, 0x56, 0x00]);
		// FEED antes del corte: ESC d 3
		expect(Array.from(out.slice(-6, -3))).toEqual([0x1b, 0x64, 3]);
	});

	it("ancho 58 usa 32 columnas y 80 usa 42", () => {
		const t58 = decode(
			buildEscPosTicket({
				config: configBase({ anchoPapelMm: 58 }),
				venta: ventaBase({ detalles: [] }),
			}),
		);
		const t80 = decode(
			buildEscPosTicket({
				config: configBase({ anchoPapelMm: 80 }),
				venta: ventaBase({ detalles: [] }),
			}),
		);
		expect(t58).toContain("-".repeat(32));
		expect(t58).not.toContain("-".repeat(33));
		expect(t80).toContain("-".repeat(42));
	});

	it("encabezado con negocio en mayúsculas y datos opcionales", () => {
		const txt = decode(
			buildEscPosTicket({ config: configBase(), venta: ventaBase() }),
		);
		expect(txt).toContain("FERRETERIA EL MARTILLO");
		expect(txt).toContain("Av Juarez 123 44100");
		expect(txt).toContain("RFC: XAXX010101000");
		expect(txt).toContain("Tel: 3331234567");
		expect(txt).toContain("www.example.com");
	});

	it("omite dirección/rfc/tel/sitio cuando son null", () => {
		const txt = decode(
			buildEscPosTicket({
				config: configBase({
					direccion: null,
					cp: null,
					rfc: null,
					telefono: null,
					sitioWeb: null,
				}),
				venta: ventaBase(),
			}),
		);
		expect(txt).not.toContain("RFC:");
		expect(txt).not.toContain("Tel:");
		expect(txt).not.toContain("www.example.com");
	});

	it("normaliza acentos y eñes para impresoras básicas", () => {
		const txt = decode(
			buildEscPosTicket({
				config: configBase({ nombreNegocio: "Ferretería Ñoño" }),
				venta: ventaBase({ detalles: [] }),
			}),
		);
		expect(txt).toContain("FERRETERIA NONO");
		expect(txt).not.toContain("?");
	});

	it("muestra bloque de cliente completo", () => {
		const txt = decode(
			buildEscPosTicket({
				config: configBase(),
				venta: ventaBase(),
				cliente: clienteBase(),
			}),
		);
		expect(txt).toContain("Datos Del Cliente");
		expect(txt).toContain("Juan Perez");
		expect(txt).toContain("Id fiscal PEPJ800101AAA");
		expect(txt).toContain("CURP PEPJ800101HJCRRN01");
		expect(txt).toContain("Calle Falsa 123, Centro");
		expect(txt).toContain("Guadalajara 44100");
		expect(txt).toContain("T: 3330001122");
		expect(txt).toContain("Email: juan@example.com");
	});

	it("muestra consumidor final sin cliente y omite bloque si el flag es false", () => {
		const sinCliente = decode(
			buildEscPosTicket({ config: configBase(), venta: ventaBase() }),
		);
		expect(sinCliente).toContain("Consumidor final - Publico en general");

		const oculto = decode(
			buildEscPosTicket({
				config: configBase({ mostrarDatosCliente: false }),
				venta: ventaBase(),
				cliente: clienteBase(),
			}),
		);
		expect(oculto).not.toContain("Datos Del Cliente");
		expect(oculto).not.toContain("Juan Perez");
	});

	it("documento respeta flags y usa cajaNombre o almacén", () => {
		const txt = decode(
			buildEscPosTicket({
				config: configBase(),
				venta: ventaBase(),
				vendedorNombre: "Ana",
				cajaNombre: "Caja 1",
			}),
		);
		expect(txt).toContain("Ticket de venta");
		expect(txt).toContain("Numero de Factura: V-0007");
		expect(txt).toContain("Caja: Caja 1");
		expect(txt).toContain("Fecha:");
		expect(txt).toContain("Le ha atendido a usted: Ana");

		const min = decode(
			buildEscPosTicket({
				config: configBase({
					mostrarNumeroFactura: false,
					mostrarCaja: false,
					mostrarFechaHora: false,
					mostrarVendedor: false,
				}),
				venta: ventaBase(),
			}),
		);
		expect(min).not.toContain("Numero de Factura");
		expect(min).not.toContain("Caja:");
		expect(min).not.toContain("Fecha:");
		expect(min).not.toContain("Le ha atendido");
	});

	it("caja cae a almacenNombre y vendedor a user por defecto", () => {
		const txt = decode(
			buildEscPosTicket({ config: configBase(), venta: ventaBase() }),
		);
		expect(txt).toContain("Caja: Matriz");
		expect(txt).toContain("Le ha atendido a usted: user");
	});

	it("detalles: encabezado, líneas y segunda línea de cantidad/descuento", () => {
		const txt = decode(
			buildEscPosTicket({ config: configBase(), venta: ventaBase() }),
		);
		expect(txt).toContain("Articulo");
		expect(txt).toContain("Martillo 16oz");
		expect(txt).toContain("Clavos 2in (caja)");
		// qty 1 sin descuento: sin segunda línea
		expect(txt).not.toContain("x1");
		// qty 2 con descuento: segunda línea con cantidad y descuento
		expect(txt).toContain("x2 -desc $5.00");
		expect(txt).toContain("$150.00");
	});

	it("trunca nombres de producto al ancho de columna", () => {
		const txt = decode(
			buildEscPosTicket({
				config: configBase({ anchoPapelMm: 58 }),
				venta: ventaBase({
					detalles: [detalleBase({ productoNombre: "A".repeat(100) })],
				}),
			}),
		);
		// 58mm: colArt = 32-3-9-9 = 11
		expect(txt).toContain("A".repeat(11));
		expect(txt).not.toContain("A".repeat(12));
	});

	it("descuento solo cuando el flag está activo y el total > 0", () => {
		const con = decode(
			buildEscPosTicket({ config: configBase(), venta: ventaBase() }),
		);
		expect(con).toContain("Descuento: -$10.00");
		expect(con).toContain("Total (con impuestos): $106.00");

		const sinMonto = decode(
			buildEscPosTicket({
				config: configBase(),
				venta: ventaBase({ descuentoTotal: 0 }),
			}),
		);
		expect(sinMonto).not.toContain("Descuento:");

		const sinFlag = decode(
			buildEscPosTicket({
				config: configBase({ mostrarDescuento: false }),
				venta: ventaBase(),
			}),
		);
		expect(sinFlag).not.toContain("Descuento:");
	});

	it("desglose de IVA con tasa, base y cuota", () => {
		const txt = decode(
			buildEscPosTicket({ config: configBase(), venta: ventaBase() }),
		);
		expect(txt).toContain("16.00%");
		expect(txt).toContain("$100.00");
		expect(txt).toContain("$16.00");
		expect(txt).not.toContain("IVA no incluido en precios");

		const sinIncluir = decode(
			buildEscPosTicket({
				config: configBase(),
				venta: ventaBase({ ivaIncluido: false }),
			}),
		);
		expect(sinIncluir).toContain("IVA no incluido en precios");

		const oculto = decode(
			buildEscPosTicket({
				config: configBase({ mostrarDesgloseIva: false }),
				venta: ventaBase(),
			}),
		);
		expect(oculto).not.toContain("Base imp.");
	});

	it("pago en efectivo calcula cambio desde montoEntregado", () => {
		const txt = decode(
			buildEscPosTicket({
				config: configBase(),
				venta: ventaBase(),
				montoEntregado: 200,
			}),
		);
		// cambio = 200 - 106 = 94
		expect(txt).toContain("$94.00");
	});

	it("pago no efectivo muestra devuelto en cero", () => {
		const txt = decode(
			buildEscPosTicket({
				config: configBase(),
				venta: ventaBase({
					formaPagoId: 2,
					formaPagoNombre: "Tarjeta",
					pagos: [],
				}),
			}),
		);
		expect(txt).toContain("Tarjeta");
		expect(txt).toContain("$0.00");
	});

	it("sin mostrarCambio usa marcador en devuelto", () => {
		const txt = decode(
			buildEscPosTicket({
				config: configBase({ mostrarCambio: false }),
				venta: ventaBase(),
			}),
		);
		// "—" se normaliza a "?" en el encoding de la impresora
		expect(txt).toContain("?");
		expect(txt).not.toContain("$94.00");
	});

	it("pie con mensajes y datos del negocio", () => {
		const txt = decode(
			buildEscPosTicket({ config: configBase(), venta: ventaBase() }),
		);
		expect(txt).toContain("GRACIAS POR SU COMPRA");
		expect(txt).toContain("Vuelva pronto");
		expect(txt).toContain("Ferreteria El Martillo");

		const sinPie = decode(
			buildEscPosTicket({
				config: configBase({ mensajePie: null, pieSecundario: null }),
				venta: ventaBase(),
			}),
		);
		expect(sinPie).not.toContain("GRACIAS POR SU COMPRA");
		expect(sinPie).not.toContain("Vuelva pronto");
	});

	it("dinero con formato $0.00 y fecha inválida se pasa tal cual", () => {
		const txt = decode(
			buildEscPosTicket({
				config: configBase(),
				venta: ventaBase({ fecha: "fecha-rara" }),
			}),
		);
		expect(txt).toContain("Fecha: fecha-rara");
	});

	it("tolera producto sin nombre, ivaTasa y formaPago nulos", () => {		const txt = decode(
			buildEscPosTicket({
				config: configBase(),
				venta: ventaBase({
					detalles: [
						detalleBase({ productoNombre: null as unknown as string }),
					],
					ivaTasa: null as unknown as number,
					formaPagoId: 1,
					formaPagoNombre: null as unknown as string,
					pagos: [],
				}),
				montoEntregado: 150,
			}),
		);
		// Etiqueta genérica de impuesto y nombre vacío
		expect(txt).toContain("IVA");
		expect(txt).not.toContain("16.00%");
		// formaPagoId 1 => efectivo aunque el nombre sea null
		expect(txt).toContain("$44.00"); // 150 - 106
	});

	it("cubre ramas de opcionales: cp ausente, cliente parcial, caja y fecha nulas", () => {
		const sinCp = decode(
			buildEscPosTicket({
				config: configBase({ cp: null }),
				venta: ventaBase({ detalles: [] }),
			}),
		);
		expect(sinCp).toContain("Av Juarez 123\n");

		const clienteParcial = decode(
			buildEscPosTicket({
				config: configBase(),
				venta: ventaBase({ detalles: [] }),
				cliente: clienteBase({
					rfc: null,
					curp: null,
					calle: null,
					colonia: null,
					ciudadNombre: null,
					cp: "44100",
					telefono: null,
					email: null,
				}),
			}),
		);
		expect(clienteParcial).toContain("Juan Perez");
		expect(clienteParcial).toContain("44100");
		expect(clienteParcial).not.toContain("Id fiscal");
		expect(clienteParcial).not.toContain("CURP");
		expect(clienteParcial).not.toContain("Calle Falsa");

		const cajaFechaNulas = decode(
			buildEscPosTicket({
				config: configBase(),
				venta: ventaBase({
					detalles: [],
					almacenNombre: null as unknown as string,
					fecha: null as unknown as string,
				}),
			}),
		);
		// "—" se normaliza a "?" en el encoding de la impresora
		expect(cajaFechaNulas).toContain("Caja: ?");
		expect(cajaFechaNulas).toContain("Fecha: ?");
	});

	it("money nulo/NaN imprime ? y calle sin colonia no agrega coma", () => {
		const txt = decode(
			buildEscPosTicket({
				config: configBase({ mostrarDesgloseIva: false }),
				venta: ventaBase({
					detalles: [
						detalleBase({
							precioUnitario: null as unknown as number,
							totalLinea: "xyz" as unknown as number,
							descuentoLinea: "" as unknown as number,
						}),
					],
					descuentoTotal: 0,
					subtotal: 0,
					iva: 0,
					total: 0,
				}),
				cliente: clienteBase({ calle: "Solo calle", colonia: null }),
			}),
		);
		expect(txt).toContain("Solo calle\n");
		expect(txt).not.toContain("Solo calle,");
		expect(txt).toContain("?");
	});
});
