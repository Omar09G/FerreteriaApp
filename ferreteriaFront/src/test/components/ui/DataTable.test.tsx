import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";

import { DataTable, type Columna } from "@/components/ui/DataTable";

interface Fila {
	id: number;
	nombre: string;
}

const columnas: Columna<Fila>[] = [
	{ key: "id", header: "ID", render: (f) => f.id },
	{ key: "nombre", header: "Nombre", render: (f) => f.nombre },
];

describe("DataTable", () => {
	it("muestra spinner cuando loading", () => {
		render(
			<DataTable columnas={columnas} items={undefined} loading rowKey={(f) => f.id} />,
		);
		expect(screen.getByRole("status")).toBeInTheDocument();
	});

	it("muestra estado vacío sin items", () => {
		render(
			<DataTable
				columnas={columnas}
				items={[]}
				rowKey={(f) => f.id}
				emptyTitle="Sin filas"
			/>,
		);
		expect(screen.getByText("Sin filas")).toBeInTheDocument();
	});

	it("renderiza encabezados y celdas", () => {
		render(
			<DataTable
				columnas={columnas}
				items={[
					{ id: 1, nombre: "Martillo" },
					{ id: 2, nombre: "Clavo" },
				]}
				rowKey={(f) => f.id}
			/>,
		);
		expect(screen.getByText("ID")).toBeInTheDocument();
		expect(screen.getByText("Martillo")).toBeInTheDocument();
		expect(screen.getByText("Clavo")).toBeInTheDocument();
		expect(screen.getAllByRole("row")).toHaveLength(3); // header + 2 filas
	});

	it("renderiza el caption accesible", () => {
		render(
			<DataTable
				columnas={columnas}
				items={[{ id: 1, nombre: "X" }]}
				rowKey={(f) => f.id}
				caption="Tabla de prueba"
			/>,
		);
		expect(screen.getByText("Tabla de prueba")).toBeInTheDocument();
	});
});
