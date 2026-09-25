import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";

import ClientesPage from "@/features/catalogo/ClientesPage";
import { ToastProvider } from "@/components/ui/Toast";
import Swal from "sweetalert2";
import { writeFile } from "xlsx";
import {
	apiActualizarCliente,
	apiClientes,
	apiCrearCliente,
	apiEliminarCliente,
} from "@/lib/api/catalogo";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

vi.mock("@/lib/api/catalogo", () => ({
	apiActualizarCliente: vi.fn(),
	apiClientes: vi.fn(),
	apiCrearCliente: vi.fn(),
	apiEliminarCliente: vi.fn(),
}));

vi.mock("xlsx", () => ({
	utils: {
		book_new: vi.fn(() => ({})),
		aoa_to_sheet: vi.fn(() => ({})),
		book_append_sheet: vi.fn(),
	},
	writeFile: vi.fn(),
}));

const CLIENTE = {
	clienteId: 1,
	tipoPersona: "FISICA",
	razonSocial: "Juan Pérez",
	nombreComercial: "Abarrotes Juan",
	rfc: "JUAP800101ABC",
	curp: null,
	regimenFiscal: null,
	telefono: "5551234567",
	whatsapp: null,
	email: "juan@example.com",
	calle: null,
	colonia: null,
	ciudadId: null,
	ciudadNombre: null,
	cp: null,
	limiteCredito: 5000,
	diasCredito: 30,
	esMayorista: true,
	activo: true,
	fotoUrl: null,
};

const MORAL = {
	...CLIENTE,
	clienteId: 2,
	tipoPersona: "MORAL",
	razonSocial: "Ferretería SA de CV",
	nombreComercial: null,
	rfc: null,
	telefono: null,
	email: null,
	limiteCredito: null,
	diasCredito: null,
	esMayorista: false,
};

function pageOf(data: unknown[]) {
	return {
		success: true,
		data,
		meta: { page: 0, size: 20, totalElements: data.length, totalPages: 1 },
	};
}

function renderPage() {
	const qc = new QueryClient({
		defaultOptions: {
			queries: { retry: false },
			mutations: { retry: false },
		},
	});
	const wrapper = ({ children }: { children: ReactNode }) => (
		<MemoryRouter>
			<QueryClientProvider client={qc}>
				<ToastProvider>{children}</ToastProvider>
			</QueryClientProvider>
		</MemoryRouter>
	);
	return render(<ClientesPage />, { wrapper });
}

beforeEach(() => {
	vi.clearAllMocks();
	localStorage.clear();
	vi.mocked(apiClientes).mockResolvedValue(pageOf([CLIENTE, MORAL]) as never);
});

describe("ClientesPage", () => {
	it("renderiza título, badges y fila con crédito", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Clientes" }),
		).toBeInTheDocument();
		expect(await screen.findByText("Juan Pérez")).toBeInTheDocument();
		expect(screen.getByText("Ferretería SA de CV")).toBeInTheDocument();
		expect(screen.getByText("Moral")).toBeInTheDocument();
		expect(screen.getByText("Física")).toBeInTheDocument();
		expect(screen.getByText("JUAP800101ABC")).toBeInTheDocument();
		expect(screen.getByText("Resultados (2)")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /Excel/i })).toBeInTheDocument();
	});

	it("busca y limpia el filtro", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Juan Pérez");
		const buscar = screen.getByLabelText(/Buscar/);
		await user.type(buscar, "juan{enter}");
		expect(apiClientes).toHaveBeenLastCalledWith(
			expect.objectContaining({ q: "juan", page: 0 }),
		);
		expect(
			await screen.findByRole("button", { name: "Limpiar" }),
		).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Limpiar" }));
		expect(apiClientes).toHaveBeenLastCalledWith(
			expect.objectContaining({ q: undefined, page: 0 }),
		);
	});

	it("busca con el botón Buscar", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Juan Pérez");
		await user.type(screen.getByLabelText(/Buscar/), "ferre");
		await user.click(screen.getByRole("button", { name: /Buscar/ }));
		expect(apiClientes).toHaveBeenLastCalledWith(
			expect.objectContaining({ q: "ferre" }),
		);
	});

	it("valida razón social y crea cliente mayorista", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearCliente).mockResolvedValue(CLIENTE as never);
		renderPage();
		await screen.findByText("Juan Pérez");
		await user.click(screen.getByRole("button", { name: /Nuevo cliente/ }));
		const dialogo = await screen.findByRole("dialog");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		expect(
			within(dialogo).getByText("La razón social es obligatoria."),
		).toBeInTheDocument();
		expect(apiCrearCliente).not.toHaveBeenCalled();

		await user.type(
			within(dialogo).getByLabelText(/Razón social/),
			"Nuevo Cliente",
		);
		await user.type(within(dialogo).getByLabelText(/RFC/), "nucv900101xyz");
		await user.type(
			within(dialogo).getByLabelText("Límite de crédito"),
			"1000",
		);
		await user.type(within(dialogo).getByLabelText("Días de crédito"), "15");
		await user.click(within(dialogo).getByText("Cliente mayorista"));
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		expect(apiCrearCliente).toHaveBeenCalledWith(
			expect.objectContaining({
				razonSocial: "Nuevo Cliente",
				rfc: "NUCV900101XYZ",
				limiteCredito: 1000,
				diasCredito: 15,
				esMayorista: true,
			}),
		);
	});

	it("edita cambiando tipo de persona y desactiva con confirmación", async () => {
		const user = userEvent.setup();
		vi.mocked(apiActualizarCliente).mockResolvedValue(CLIENTE as never);
		vi.mocked(apiEliminarCliente).mockResolvedValue(undefined as never);
		renderPage();
		await screen.findByText("Juan Pérez");
		await user.click(
			screen.getByRole("button", { name: "Editar Juan Pérez" }),
		);
		const dialogo = await screen.findByRole("dialog");
		expect(within(dialogo).getByLabelText(/Razón social/)).toHaveValue(
			"Juan Pérez",
		);
		await user.selectOptions(
			within(dialogo).getByLabelText("Tipo de persona"),
			"MORAL",
		);
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		expect(apiActualizarCliente).toHaveBeenCalledWith(
			1,
			expect.objectContaining({ tipoPersona: "MORAL" }),
		);

		await user.click(
			screen.getByRole("button", { name: "Desactivar Juan Pérez" }),
		);
		expect(
			await screen.findByText("Confirmar desactivación"),
		).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Sí, desactivar" }));
		expect(apiEliminarCliente).toHaveBeenCalledWith(1);
	});

	it("muestra error si la carga inicial falla", async () => {
		vi.mocked(apiClientes).mockRejectedValueOnce(new Error("sin red"));
		renderPage();
		await vi.waitFor(() => {
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin red") }),
			);
		});
	});

	it("exporta a Excel lo visible", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Juan Pérez");
		await user.click(screen.getByRole("button", { name: /Excel/i }));
		await waitFor(() =>
			expect(vi.mocked(writeFile)).toHaveBeenCalledTimes(1),
		);
	});
});
