import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { ProductoForm } from "./ProductosPage";
import type { Producto, ProductoRequest } from "@/lib/api/types";

const PRODUCTO: Producto = {
	productoId: 2,
	codigo: "LLA-002",
	tipo: "PRODUCTO",
	nombre: "Juego llaves combinadas 12 pz",
	descripcion: null,
	categoriaId: 5,
	categoriaNombre: "Construcción",
	marcaId: 1,
	marcaNombre: "Truper",
	unidadMedidaId: 1,
	unidadMedidaClave: "PZA",
	costoActual: 380,
	precioMenudeo: 489,
	precioMayoreo: 455,
	aplicaIva: true,
	codigosBarras: ["7501234567001"],
};

const CATS = [{ categoriaId: 5, nombre: "Construcción", categoriaPadreId: null, ruta: "Construcción", nivel: 0 }];
const MARCAS = [{ marcaId: 1, nombre: "Truper" }];
const UMS = [{ unidadId: 1, clave: "PZA", nombre: "Pieza", permiteFraccion: false }];

function renderForm(producto: Producto | null, onGuardar = vi.fn()) {
	return {
		onGuardar,
		...render(
			<ProductoForm
				producto={producto}
				categorias={CATS}
				marcas={MARCAS}
				unidades={UMS}
				guardando={false}
				onGuardar={onGuardar}
				onClose={() => {}}
			/>,
		),
	};
}

describe("ProductoForm", () => {
	it("pinta todos los campos al editar (incluidos selects y barras)", () => {
		renderForm(PRODUCTO);
		expect(screen.getByLabelText(/Nombre/)).toHaveValue(
			"Juego llaves combinadas 12 pz",
		);
		expect(screen.getByPlaceholderText("Ej. TAL-005")).toHaveValue("LLA-002");
		expect(screen.getByLabelText(/Tipo/)).toHaveValue("PRODUCTO");
		expect(screen.getByLabelText(/Categoría/)).toHaveValue("5");
		expect(screen.getByLabelText(/Marca/)).toHaveValue("1");
		expect(screen.getByLabelText(/Unidad de medida/)).toHaveValue("1");
		expect(screen.getByDisplayValue("7501234567001")).toBeInTheDocument();
	});

	it("envía codigosBarras en el payload al guardar", async () => {
		const user = userEvent.setup();
		const onGuardar = vi.fn();
		renderForm(PRODUCTO, onGuardar);
		await user.click(screen.getByRole("button", { name: /Guardar/ }));
		expect(onGuardar).toHaveBeenCalledOnce();
		const payload = onGuardar.mock.calls[0][0] as ProductoRequest;
		expect(payload.codigosBarras).toEqual([
			{ codigo: "7501234567001", factor: 1 },
		]);
	});

	it("bloquea el guardado con barras duplicadas", async () => {
		const user = userEvent.setup();
		const onGuardar = vi.fn();
		renderForm({ ...PRODUCTO, codigosBarras: ["A", "a"] }, onGuardar);
		await user.click(screen.getByRole("button", { name: /Guardar/ }));
		expect(onGuardar).not.toHaveBeenCalled();
		expect(
			screen.getByText("Corrige los códigos de barras antes de guardar."),
		).toBeInTheDocument();
	});
});
