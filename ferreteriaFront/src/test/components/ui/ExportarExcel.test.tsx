import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { ExportarExcel } from "@/components/ui/ExportarExcel";
import type { Columna } from "@/components/ui/DataTable";

const writeFile = vi.fn((...args: unknown[]) => { void args; });
const book_append_sheet = vi.fn((...args: unknown[]) => { void args; });
const aoa_to_sheet = vi.fn((...args: unknown[]) => { void args; return {}; });

vi.mock("xlsx", () => ({
  utils: {
    book_new: vi.fn(() => ({})),
    aoa_to_sheet: (...args: unknown[]) => aoa_to_sheet(...args),
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
  aoa_to_sheet.mockClear();
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

  it("neutraliza celdas con inicio de fórmula (= + - @)", async () => {
    const user = userEvent.setup();
    const maliciosos: Fila[] = [
      { nombre: "=1+1", precio: 0 },
      { nombre: "@SUM(A1:A2)", precio: 0 },
      { nombre: "-2+3", precio: 0 },
      { nombre: "+cmd", precio: 0 },
      { nombre: "Martillo", precio: 0 },
    ];
    render(<ExportarExcel columnas={columnas} items={maliciosos} archivo="x" />);
    await user.click(screen.getByRole("button", { name: /Excel/ }));
    await waitFor(() => expect(aoa_to_sheet).toHaveBeenCalledTimes(1));
    const filas = aoa_to_sheet.mock.calls[0][0] as string[][];
    const nombres = filas.slice(1).map((f) => f[0]);
    expect(nombres).toEqual(["'=1+1", "'@SUM(A1:A2)", "'-2+3", "'+cmd", "Martillo"]);
  });
});
