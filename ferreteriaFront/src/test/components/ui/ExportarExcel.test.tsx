import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { ExportarExcel } from "@/components/ui/ExportarExcel";
import type { Columna } from "@/components/ui/DataTable";

const writeFile = vi.fn();
const book_append_sheet = vi.fn();

vi.mock("xlsx", () => ({
	utils: {
		book_new: vi.fn(() => ({})),
		aoa_to_sheet: vi.fn(() => ({})),
		book_append_sheet: (...args: unknown[]) => book_append_sheet(...args),
	},
	writeFile: (...args: unknown[]) => writeFile(...args),
}));

interface Fila {
	nombre: string;
	precio: number;
}

const columnas: Columna<Fila>[] = [
	{ key: "nombre", header: "Nombre", render: (f) => f.nombre, exportar: (f) => f.nombre },
	{ key: "precio", header: "Precio", render: (f) => f.precio, exportar: (f) => f.precio },
	{ key: "acciones", header: "Acciones", render: () => null },
];

const items: Fila[] = [{ nombre: "Martillo", precio: 100 }];

beforeEach(() => {
	writeFile.mockClear();
	book_append_sheet.mockClear();
});

describe("ExportarExcel", () => {
	it("está deshabilitado sin items", () => {
		render(<ExportarExcel columnas={columnas} items={[]} archivo="ventas" />);
		expect(screen.getByRole("button", { name: /Excel/ })).toBeDisabled();
	});

	it("está deshabilitado sin columnas exportables", () => {
		render(
			<ExportarExcel
				columnas={[{ key: "a", header: "A", render: () => null }]}
				items={items}
				archivo="ventas"
			/>,
		);
		expect(screen.getByRole("button", { name: /Excel/ })).toBeDisabled();
	});

	it("exporta y genera el xlsx con el nombre del archivo", async () => {
		const user = userEvent.setup();
		render(<ExportarExcel columnas={columnas} items={items} archivo="ventas" />);
		await user.click(screen.getByRole("button", { name: /Excel/ }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		const nombre = String(writeFile.mock.calls[0][1]);
		expect(nombre).toMatch(/^ventas-\d{4}-\d{2}-\d{2}\.xlsx$/);
		expect(book_append_sheet).toHaveBeenCalledTimes(1);
	});
});
