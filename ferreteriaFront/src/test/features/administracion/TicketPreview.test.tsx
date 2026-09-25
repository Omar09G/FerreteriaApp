import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";

import { TicketPreview } from "@/features/administracion/TicketPreview";
import type { TicketConfig } from "@/lib/api/types";

const CONFIG: TicketConfig = {
	ticketConfigId: 1,
	almacenId: null,
	logotipoUrl: null,
	mostrarLogotipo: false,
	nombreNegocio: "El Tornillo Feliz",
	direccion: "Av. Principal 123",
	cp: "40006",
	rfc: "XAXX010101000",
	telefono: "555000111",
	email: null,
	sitioWeb: null,
	tituloDocumento: "Factura simplificada",
	mostrarDatosCliente: true,
	mostrarNumeroFactura: true,
	mostrarCaja: true,
	mostrarFechaHora: true,
	mostrarVendedor: true,
	mostrarDesgloseIva: true,
	mostrarDescuento: true,
	mostrarCambio: true,
	mensajePie: "Gracias por su compra",
	pieSecundario: null,
	anchoPapelMm: 80,
	fontSizePt: 9,
	actualizadoEn: null,
	actualizadoPor: null,
};

function renderPreview(config: TicketConfig = CONFIG) {
	return render(
		<MemoryRouter>
			<TicketPreview config={config} />
		</MemoryRouter>,
	);
}

describe("TicketPreview (smoke)", () => {
	it("renderiza negocio, artículos mock y totales", () => {
		renderPreview();
		// El nombre del negocio aparece en encabezado y pie del ticket.
		expect(
			screen.getAllByText("El Tornillo Feliz").length,
		).toBeGreaterThanOrEqual(1);
		expect(screen.getByText("Factura simplificada")).toBeInTheDocument();
		expect(screen.getByText("Gucci")).toBeInTheDocument();
		expect(screen.getByText(/Total \(con impuestos\)/)).toBeInTheDocument();
		expect(screen.getByText("Datos Del Cliente")).toBeInTheDocument();
		expect(screen.getByText("Miguel Dominguez")).toBeInTheDocument();
	});

	it("oculta el bloque de cliente cuando mostrarDatosCliente es falso", () => {
		renderPreview({ ...CONFIG, mostrarDatosCliente: false });
		expect(screen.queryByText("Datos Del Cliente")).not.toBeInTheDocument();
		expect(
			screen.getAllByText("El Tornillo Feliz").length,
		).toBeGreaterThanOrEqual(1);
	});

	it("usa ancho 58mm cuando el config lo pide", () => {
		const { container } = renderPreview({ ...CONFIG, anchoPapelMm: 58 });
		const ticket = container.querySelector("#ticket-preview");
		expect(ticket).toBeInTheDocument();
		expect(ticket).toHaveStyle({ width: "208px" });
	});
});

const VENTA_BASE = {
	ventaId: 999,
	folio: "V-TEST-001",
	clienteId: 1,
	clienteNombre: "Miguel Dominguez",
	cliente: {
		clienteId: 1,
		razonSocial: "Miguel Dominguez",
		nombreComercial: null,
		rfc: "16618263Z",
		curp: "CURP123456",
		regimenFiscal: null,
		telefono: "63883532",
		whatsapp: null,
		email: "miguel@example.com",
		calle: "C/Carretera 56A",
		colonia: "Centro",
		cp: "40006",
		ciudadNombre: "Segovia",
	},
	almacenId: 1,
	almacenNombre: "Principal",
	fecha: "2026-09-20T12:00:00",
	fechaLocal: "2026-09-20",
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
		{
			ventaDetalleId: 1,
			productoId: 1,
			productoNombre: "Gucci",
			cantidad: 1,
			precioUnitario: 10.0,
			costoUnitario: 6,
			descuentoLinea: 5,
			totalLinea: 10,
		},
	],
	pagos: [
		{
			pagoClienteId: 1,
			formaPagoId: 1,
			referencia: null,
			monto: 200,
			fecha: "2026-09-20",
		},
	],
};

describe("TicketPreview (profundización)", () => {
	it("muestra el logotipo cuando la URL es segura", () => {
		render(
			<MemoryRouter>
				<TicketPreview
					config={{
						...CONFIG,
						mostrarLogotipo: true,
						logotipoUrl: "https://tienda.example.com/logo.png",
					}}
				/>
			</MemoryRouter>,
		);
		expect(screen.getByAltText("Logotipo")).toBeInTheDocument();
	});

	it("bloquea el logotipo con esquema malicioso", () => {
		render(
			<MemoryRouter>
				<TicketPreview
					config={{
						...CONFIG,
						mostrarLogotipo: true,
						logotipoUrl: "javascript:alert(1)",
					}}
				/>
			</MemoryRouter>,
		);
		expect(screen.queryByAltText("Logotipo")).not.toBeInTheDocument();
	});

	it("muestra consumidor final cuando la venta no tiene cliente", () => {
		render(
			<MemoryRouter>
				<TicketPreview
					config={CONFIG}
					venta={{ ...VENTA_BASE, clienteId: null, cliente: null, clienteNombre: null } as never}
				/>
			</MemoryRouter>,
		);
		expect(
			screen.getByText("Consumidor final — Público en general"),
		).toBeInTheDocument();
	});

	it("usa el nombre del cliente cuando solo hay clienteId", () => {
		render(
			<MemoryRouter>
				<TicketPreview
					config={CONFIG}
					venta={{ ...VENTA_BASE, cliente: null, clienteNombre: "Público General" } as never}
				/>
			</MemoryRouter>,
		);
		expect(screen.getByText("Público General")).toBeInTheDocument();
	});

	it("muestra el cambio cuando el pago en efectivo supera el total", () => {
		render(
			<MemoryRouter>
				<TicketPreview config={CONFIG} montoEntregado={200} />
			</MemoryRouter>,
		);
		expect(screen.getByText(/Cambio:/)).toBeInTheDocument();
	});

	it("oculta el cambio con guion cuando mostrarCambio es falso", () => {
		render(
			<MemoryRouter>
				<TicketPreview config={{ ...CONFIG, mostrarCambio: false }} />
			</MemoryRouter>,
		);
		expect(screen.getByText("—")).toBeInTheDocument();
		expect(screen.queryByText(/Cambio:/)).not.toBeInTheDocument();
	});

	it("muestra cero devuelto en pago no efectivo", () => {
		render(
			<MemoryRouter>
				<TicketPreview
					config={CONFIG}
					venta={{
						...VENTA_BASE,
						formaPagoId: 2,
						formaPagoNombre: "Tarjeta",
						pagos: [],
					} as never}
				/>
			</MemoryRouter>,
		);
		expect(screen.getByText("tarjeta")).toBeInTheDocument();
	});

	it("oculta número, caja, fecha y vendedor cuando sus flags son falsos", () => {
		render(
			<MemoryRouter>
				<TicketPreview
					config={{
						...CONFIG,
						mostrarNumeroFactura: false,
						mostrarCaja: false,
						mostrarFechaHora: false,
						mostrarVendedor: false,
					}}
				/>
			</MemoryRouter>,
		);
		expect(screen.queryByText(/Número de Factura/)).not.toBeInTheDocument();
		expect(screen.queryByText(/Caja:/)).not.toBeInTheDocument();
		expect(screen.queryByText(/Fecha:/)).not.toBeInTheDocument();
		expect(screen.queryByText(/Le ha atendido/)).not.toBeInTheDocument();
	});

	it("oculta el desglose de IVA cuando el flag es falso", () => {
		render(
			<MemoryRouter>
				<TicketPreview config={{ ...CONFIG, mostrarDesgloseIva: false }} />
			</MemoryRouter>,
		);
		expect(screen.queryByText("Base imp.")).not.toBeInTheDocument();
	});

	it("advierte cuando el IVA no está incluido en precios", () => {
		render(
			<MemoryRouter>
				<TicketPreview
					config={CONFIG}
					venta={{ ...VENTA_BASE, ivaIncluido: false } as never}
				/>
			</MemoryRouter>,
		);
		expect(
			screen.getByText("IVA no incluido en precios"),
		).toBeInTheDocument();
	});

	it("muestra el descuento de línea y el aplicado al total", () => {
		render(
			<MemoryRouter>
				<TicketPreview config={CONFIG} venta={{ ...VENTA_BASE } as never} />
			</MemoryRouter>,
		);
		expect(screen.getByText(/-desc/)).toBeInTheDocument();
		expect(screen.getByText(/Descuento aplicado:/)).toBeInTheDocument();
	});

	it("usa clienteOverride y nombres de caja y vendedor personalizados", () => {
		render(
			<MemoryRouter>
				<TicketPreview
					config={CONFIG}
					vendedorNombre="ana"
					cajaNombre="Caja 9"
					clienteOverride={{
						clienteId: 7,
						razonSocial: "Empresa SA",
						nombreComercial: null,
						rfc: "ESA010101AAA",
						curp: null,
						regimenFiscal: null,
						telefono: null,
						whatsapp: null,
						email: null,
						calle: null,
						colonia: null,
						cp: null,
						ciudadNombre: null,
					}}
				/>
			</MemoryRouter>,
		);
		expect(screen.getByText("Empresa SA")).toBeInTheDocument();
		expect(screen.getByText(/Caja 9/)).toBeInTheDocument();
		expect(screen.getByText(/ana/)).toBeInTheDocument();
	});

	it("oculta el pie cuando no hay mensajes configurados", () => {
		renderPreview({ ...CONFIG, mensajePie: null, pieSecundario: null });
		expect(screen.queryByText("Gracias por su compra")).not.toBeInTheDocument();
	});
});
