import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";

import ProveedoresPage from "@/features/catalogo/ProveedoresPage";
import { ToastProvider } from "@/components/ui/Toast";
import Swal from "sweetalert2";
import { writeFile } from "xlsx";
import {
	apiActualizarProveedor,
	apiCrearProveedor,
	apiEliminarProveedor,
	apiProveedoresPaginado,
} from "@/lib/api/catalogo";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

vi.mock("@/lib/api/catalogo", () => ({
	apiActualizarProveedor: vi.fn(),
	apiCrearProveedor: vi.fn(),
	apiEliminarProveedor: vi.fn(),
	apiProveedoresPaginado: vi.fn(),
}));

vi.mock("xlsx", () => ({
	utils: {
		book_new: vi.fn(() => ({})),
		aoa_to_sheet: vi.fn(() => ({})),
		book_append_sheet: vi.fn(),
	},
	writeFile: vi.fn(),
}));

const PROVEEDOR = {
	proveedorId: 1,
	razonSocial: "Distribuidora Acme",
	rfc: "DAC900101XXX",
	regimenFiscal: "Persona moral",
	email: "ventas@acme.mx",
	telefono: "5559876543",
	diasCredito: 30,
	limiteCredito: 20000,
	fotoUrl: null,
};

const SIN_CREDITO = {
	...PROVEEDOR,
	proveedorId: 2,
	razonSocial: "Tlapalería El Roble",
	rfc: null,
	regimenFiscal: null,
	email: null,
	telefono: null,
	diasCredito: null,
	limiteCredito: null,
};

function pageOf(data: unknown[]) {
	return {
		success: true,
		data,
		meta: { page: 0, size: 15, totalElements: data.length, totalPages: 1 },
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
	return render(<ProveedoresPage />, { wrapper });
}

beforeEach(() => {
	vi.clearAllMocks();
	localStorage.clear();
	vi.mocked(apiProveedoresPaginado).mockResolvedValue(
		pageOf([PROVEEDOR, SIN_CREDITO]) as never,
	);
});

describe("ProveedoresPage", () => {
	it("renderiza título, tabla y em-dash en nulos", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Proveedores" }),
		).toBeInTheDocument();
		expect(await screen.findByText("Distribuidora Acme")).toBeInTheDocument();
		expect(screen.getByText("Tlapalería El Roble")).toBeInTheDocument();
		expect(screen.getByText("DAC900101XXX")).toBeInTheDocument();
		expect(screen.getByText("Resultados (2)")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /Excel/i })).toBeInTheDocument();
		expect(screen.getAllByText("—").length).toBeGreaterThan(0);
	});

	it("busca con Enter y limpia el filtro", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Distribuidora Acme");
		await user.type(screen.getByLabelText(/Buscar/), "acme{enter}");
		expect(apiProveedoresPaginado).toHaveBeenLastCalledWith(
			expect.objectContaining({ q: "acme", page: 0 }),
		);
		await user.click(
			await screen.findByRole("button", { name: "Limpiar" }),
		);
		expect(apiProveedoresPaginado).toHaveBeenLastCalledWith(
			expect.objectContaining({ q: undefined, page: 0 }),
		);
	});

	it("valida razón social y crea proveedor con crédito", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearProveedor).mockResolvedValue(PROVEEDOR as never);
		renderPage();
		await screen.findByText("Distribuidora Acme");
		await user.click(screen.getByRole("button", { name: /Nuevo proveedor/ }));
		const dialogo = await screen.findByRole("dialog");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		expect(
			within(dialogo).getByText("La razón social es obligatoria."),
		).toBeInTheDocument();
		expect(apiCrearProveedor).not.toHaveBeenCalled();

		await user.type(
			within(dialogo).getByLabelText(/Razón social/),
			"Nuevo Proveedor",
		);
		await user.type(within(dialogo).getByLabelText(/RFC/), "npr900101aaa");
		await user.type(
			within(dialogo).getByLabelText("Días de crédito"),
			"10",
		);
		await user.type(
			within(dialogo).getByLabelText("Límite de crédito"),
			"7500",
		);
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		expect(apiCrearProveedor).toHaveBeenCalledWith(
			expect.objectContaining({
				razonSocial: "Nuevo Proveedor",
				rfc: "NPR900101AAA",
				diasCredito: 10,
				limiteCredito: 7500,
			}),
		);
	});

	it("edita y elimina con confirmación", async () => {
		const user = userEvent.setup();
		vi.mocked(apiActualizarProveedor).mockResolvedValue(PROVEEDOR as never);
		vi.mocked(apiEliminarProveedor).mockResolvedValue(undefined as never);
		renderPage();
		await screen.findByText("Distribuidora Acme");
		await user.click(
			screen.getByRole("button", { name: "Editar Distribuidora Acme" }),
		);
		const dialogo = await screen.findByRole("dialog");
		expect(within(dialogo).getByLabelText(/Razón social/)).toHaveValue(
			"Distribuidora Acme",
		);
		await user.clear(within(dialogo).getByLabelText("Teléfono"));
		await user.type(within(dialogo).getByLabelText("Teléfono"), "5550000000");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		expect(apiActualizarProveedor).toHaveBeenCalledWith(
			1,
			expect.objectContaining({ telefono: "5550000000" }),
		);

		await user.click(
			screen.getByRole("button", { name: "Eliminar Distribuidora Acme" }),
		);
		expect(
			await screen.findByText("Confirmar eliminación"),
		).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Sí, eliminar" }));
		expect(apiEliminarProveedor).toHaveBeenCalledWith(1);
	});

	it("muestra error si eliminar falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiEliminarProveedor).mockRejectedValueOnce(
			new Error("no se puede eliminar"),
		);
		renderPage();
		await screen.findByText("Distribuidora Acme");
		await user.click(
			screen.getByRole("button", { name: "Eliminar Tlapalería El Roble" }),
		);
		await user.click(screen.getByRole("button", { name: "Sí, eliminar" }));
		await vi.waitFor(() => {
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("no se puede eliminar"),
				}),
			);
		});
	});

	it("exporta a Excel lo visible", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Distribuidora Acme");
		await user.click(screen.getByRole("button", { name: /Excel/i }));
		await waitFor(() =>
			expect(vi.mocked(writeFile)).toHaveBeenCalledTimes(1),
		);
	});
});
