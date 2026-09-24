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
		expect(screen.getByText("El Tornillo Feliz")).toBeInTheDocument();
		expect(screen.getByText("Factura simplificada")).toBeInTheDocument();
		expect(screen.getByText("Gucci")).toBeInTheDocument();
		expect(screen.getByText(/Total \(con impuestos\)/)).toBeInTheDocument();
		expect(screen.getByText("Datos Del Cliente")).toBeInTheDocument();
		expect(screen.getByText("Miguel Dominguez")).toBeInTheDocument();
	});

	it("oculta el bloque de cliente cuando mostrarDatosCliente es falso", () => {
		renderPreview({ ...CONFIG, mostrarDatosCliente: false });
		expect(screen.queryByText("Datos Del Cliente")).not.toBeInTheDocument();
		expect(screen.getByText("El Tornillo Feliz")).toBeInTheDocument();
	});

	it("usa ancho 58mm cuando el config lo pide", () => {
		const { container } = renderPreview({ ...CONFIG, anchoPapelMm: 58 });
		const ticket = container.querySelector("#ticket-preview");
		expect(ticket).toBeInTheDocument();
		expect(ticket).toHaveStyle({ width: "208px" });
	});
});
