import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import Swal from "sweetalert2";

import { ToastProvider } from "@/components/ui/Toast";
import AuditoriaPage from "@/features/seguridad/AuditoriaPage";
import { apiAuditoria, apiTablasAuditoria } from "@/lib/api/auditoria";
import { useAuthStore } from "@/store/auth";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

const writeFile = vi.fn();
vi.mock("xlsx", () => ({
	utils: {
		book_new: vi.fn(() => ({})),
		aoa_to_sheet: vi.fn(() => ({})),
		book_append_sheet: vi.fn(),
	},
	writeFile: (...args: unknown[]) => writeFile(...args),
}));

vi.mock("@/lib/api/auditoria", () => ({
	apiAuditoria: vi.fn(),
	apiTablasAuditoria: vi.fn(),
}));

const REGISTRO = {
	auditoriaId: 10,
	esquema: "seg",
	tabla: "usuarios",
	registroId: 5,
	accion: "UPDATE",
	datosAnteriores: '{"activo":true}',
	datosNuevos: '{"activo":false}',
	usuarioId: 1,
	usuario: "admin",
	creadoEn: "2026-03-01T12:00:00Z",
};

function renderPage() {
	const client = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	return render(
		<MemoryRouter>
			<QueryClientProvider client={client}>
				<ToastProvider>
					<AuditoriaPage />
				</ToastProvider>
			</QueryClientProvider>
		</MemoryRouter>,
	);
}

beforeEach(() => {
	vi.clearAllMocks();
	localStorage.clear();
	useAuthStore.setState({
		autenticado: true,
		usuario: { usuarioId: 1, username: "admin", roles: ["ADMINISTRADOR"] },
		lastActivityAt: Date.now(),
	});
	localStorage.clear();
	vi.mocked(apiTablasAuditoria).mockResolvedValue([
		{ esquema: "seg", tabla: "usuarios" },
	] as never);
	vi.mocked(apiAuditoria).mockResolvedValue({
		success: true,
		data: [REGISTRO],
		meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
	} as never);
});

describe("AuditoriaPage (smoke)", () => {
	it("renderiza título, filtros y columnas", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Auditoría", level: 1 }),
		).toBeInTheDocument();
		expect(screen.getByLabelText(/Esquema/)).toBeInTheDocument();
		expect(screen.getByLabelText(/Tabla/)).toBeInTheDocument();
		expect(screen.getByLabelText(/Acción/)).toBeInTheDocument();
		expect(
			screen.getByPlaceholderText("Ej. admin"),
		).toBeInTheDocument();
		expect(await screen.findByRole("button", { name: "Ver" })).toBeInTheDocument();
		expect(screen.getAllByText("UPDATE").length).toBeGreaterThanOrEqual(1);
		expect(
			screen.getByRole("columnheader", { name: "Fecha" }),
		).toBeInTheDocument();
		expect(
			screen.getByRole("columnheader", { name: "Esquema/Tabla" }),
		).toBeInTheDocument();
		expect(
			screen.getByRole("columnheader", { name: "Cambios" }),
		).toBeInTheDocument();
	});

	it("expande el detalle de cambios al pulsar Ver", async () => {
		const user = userEvent.setup();
		renderPage();
		const ver = await screen.findByRole("button", { name: "Ver" });
		await user.click(ver);
		expect(screen.getByText("Anterior")).toBeInTheDocument();
		expect(screen.getByText("Nuevo")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: "Ocultar" }),
		).toBeInTheDocument();
	});

	it("filtrar por usuario vuelve a pedir datos con el filtro", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("UPDATE");
		const llamadasAntes = vi.mocked(apiAuditoria).mock.calls.length;
		await user.type(screen.getByPlaceholderText("Ej. admin"), "admin");
		await vi.waitFor(() => {
			const llamadas = vi
				.mocked(apiAuditoria)
				.mock.calls.slice(llamadasAntes)
				.map((c) => c[0]);
			expect(
				llamadas.some(
					(f) => f && (f as { usuario?: string }).usuario === "admin",
				),
			).toBe(true);
		});
	});
});

const REGISTRO_INSERT = {
	auditoriaId: 11,
	esquema: "ven",
	tabla: "ventas",
	registroId: 7,
	accion: "INSERT",
	datosAnteriores: null,
	datosNuevos: '{"total":116}',
	usuarioId: null,
	usuario: null,
	creadoEn: "2026-03-02T12:00:00Z",
};

const REGISTRO_DELETE = {
	auditoriaId: 12,
	esquema: "inv",
	tabla: "productos",
	registroId: 3,
	accion: "DELETE",
	datosAnteriores: '{"nombre":"Martillo"}',
	datosNuevos: null,
	usuarioId: 2,
	usuario: "cajero",
	creadoEn: "2026-03-03T12:00:00Z",
};

describe("AuditoriaPage (profundización)", () => {
	it("muestra badges de INSERT y DELETE y guion sin usuario", async () => {
		vi.mocked(apiAuditoria).mockResolvedValueOnce({
			success: true,
			data: [REGISTRO, REGISTRO_INSERT, REGISTRO_DELETE],
			meta: { page: 0, size: 20, totalElements: 3, totalPages: 1 },
		} as never);
		renderPage();
		const tabla = await screen.findByRole("table");
		expect(await within(tabla).findByText("cajero")).toBeInTheDocument();
		expect(within(tabla).getByText("INSERT")).toBeInTheDocument();
		expect(within(tabla).getByText("DELETE")).toBeInTheDocument();
		expect(within(tabla).getByText("admin")).toBeInTheDocument();
	});

	it("expande el detalle con guiones cuando no hay datos", async () => {
		const user = userEvent.setup();
		vi.mocked(apiAuditoria).mockResolvedValueOnce({
			success: true,
			data: [REGISTRO_INSERT],
			meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
		} as never);
		renderPage();
		await user.click(await screen.findByRole("button", { name: "Ver" }));
		expect(
			await screen.findByRole("button", { name: "Ocultar" }),
		).toBeInTheDocument();
	});

	it("filtra por esquema, tabla y acción combinados", async () => {
		const user = userEvent.setup();
		vi.mocked(apiTablasAuditoria).mockResolvedValue([
			{ esquema: "seg", tabla: "usuarios" },
			{ esquema: "ven", tabla: "ventas" },
		] as never);
		renderPage();
		await within(await screen.findByRole("table")).findByText("admin");
		await screen.findByRole("option", { name: "ven" });
		await user.selectOptions(screen.getByLabelText(/Esquema/), "ven");
		await waitFor(() =>
			expect(vi.mocked(apiAuditoria)).toHaveBeenLastCalledWith(
				expect.objectContaining({ esquema: "ven", page: 0 }),
			),
		);
		await user.selectOptions(screen.getByLabelText(/Tabla/), "ventas");
		await waitFor(() =>
			expect(vi.mocked(apiAuditoria)).toHaveBeenLastCalledWith(
				expect.objectContaining({ tabla: "ventas", page: 0 }),
			),
		);
		await user.selectOptions(screen.getByLabelText(/Acción/), "DELETE");
		await waitFor(() =>
			expect(vi.mocked(apiAuditoria)).toHaveBeenLastCalledWith(
				expect.objectContaining({ accion: "DELETE", page: 0 }),
			),
		);
	});

	it("filtra por registro y texto libre", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("UPDATE");
		const llamadasAntes = vi.mocked(apiAuditoria).mock.calls.length;
		await user.type(screen.getByLabelText(/Registro ID/), "5");
		await user.type(
			screen.getByPlaceholderText('Ej. "estado":'),
			"Martillo",
		);
		await waitFor(() => {
			const llamadas = vi
				.mocked(apiAuditoria)
				.mock.calls.slice(llamadasAntes)
				.map((c) => c[0]) as Array<{ registroId?: number; texto?: string }>;
			expect(
				llamadas.some((f) => f && f.registroId === 5),
			).toBe(true);
			expect(
				llamadas.some((f) => f && f.texto === "Martillo"),
			).toBe(true);
		});
	});

	it("limpia todos los filtros con el botón Limpiar", async () => {
		const user = userEvent.setup();
		renderPage();
		await within(await screen.findByRole("table")).findByText("admin");
		await user.selectOptions(screen.getByLabelText(/Acción/), "DELETE");
		await waitFor(() =>
			expect(vi.mocked(apiAuditoria)).toHaveBeenLastCalledWith(
				expect.objectContaining({ accion: "DELETE" }),
			),
		);
		await user.click(screen.getByRole("button", { name: "Limpiar" }));
		await waitFor(() => {
			const ultima = vi.mocked(apiAuditoria).mock.calls.at(-1)?.[0] as unknown as Record<
				string,
				unknown
			>;
			expect(ultima).toMatchObject({ page: 0, size: 20 });
			expect(ultima).not.toHaveProperty("accion");
			expect(ultima).not.toHaveProperty("esquema");
			expect(ultima).not.toHaveProperty("tabla");
		});
	});

	it("cambiar el rango de fechas recarga la lista", async () => {
		renderPage();
		await screen.findByText("UPDATE");
		const api = vi.mocked(apiAuditoria);
		const llamadasAntes = api.mock.calls.length;
		const rango = screen.getByTestId("rango-fechas");
		const [del] = within(rango).getAllByDisplayValue(/\d{4}-\d{2}-\d{2}/);
		const actual = (del as HTMLInputElement).value;
		const d = new Date(`${actual}T12:00:00`);
		d.setDate(d.getDate() - 2);
		const nuevo = d.toISOString().slice(0, 10);
		fireEvent.change(del, { target: { value: nuevo } });
		await waitFor(() =>
			expect(api.mock.calls.length).toBeGreaterThan(llamadasAntes),
		);
		const ultima = api.mock.calls[api.mock.calls.length - 1][0] as {
			fechaInicio: string;
		};
		expect(ultima.fechaInicio).toBe(nuevo);
	});

	it("pagina la lista cuando hay más de una página", async () => {
		const user = userEvent.setup();
		vi.mocked(apiAuditoria).mockResolvedValue({
			success: true,
			data: [REGISTRO],
			meta: { page: 0, size: 20, totalElements: 40, totalPages: 2 },
		} as never);
		renderPage();
		await within(await screen.findByRole("table")).findByText("admin");
		await user.click(screen.getByRole("button", { name: /Página siguiente/i }));
		await waitFor(() =>
			expect(vi.mocked(apiAuditoria)).toHaveBeenCalledWith(
				expect.objectContaining({ page: 1 }),
			),
		);
	});

	it("exporta los registros visibles a Excel", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("UPDATE");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^auditoria-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});

	it("muestra estado vacío cuando no hay registros", async () => {
		vi.mocked(apiAuditoria).mockResolvedValueOnce({
			success: true,
			data: [],
			meta: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
		} as never);
		renderPage();
		expect(await screen.findByText("Sin movimientos")).toBeInTheDocument();
		expect(
			screen.getByText("No hay registros que coincidan con los filtros aplicados."),
		).toBeInTheDocument();
	});

	it("muestra toast si la lista falla al cargar", async () => {
		vi.mocked(apiAuditoria).mockRejectedValueOnce(new Error("sin conexión"));
		renderPage();
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin conexión") }),
			),
		);
	});
});
