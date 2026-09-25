import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import Swal from "sweetalert2";

import { ToastProvider } from "@/components/ui/Toast";
import NominaPage from "@/features/rrhh/NominaPage";
import {
	apiCancelarNomina,
	apiCrearNomina,
	apiEmpleados,
	apiGenerarQuincena,
	apiNomina,
	apiPagarNomina,
	apiPagarNominaLote,
} from "@/lib/api/admin";
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

vi.mock("@/lib/api/admin", () => ({
	apiCancelarNomina: vi.fn(),
	apiCrearNomina: vi.fn(),
	apiEmpleados: vi.fn(),
	apiGenerarQuincena: vi.fn(),
	apiNomina: vi.fn(),
	apiPagarNomina: vi.fn(),
	apiPagarNominaLote: vi.fn(),
}));

const NOMINA = {
	nominaId: 7,
	empleadoId: 3,
	empleado: "Pérez López Juan",
	periodoIni: "2026-09-01",
	periodoFin: "2026-09-15",
	diasPagados: 15,
	percepciones: 5250,
	deducciones: 250,
	netoPagar: 5000,
	estado: "PENDIENTE",
	fechaPago: null,
	usuarioRegistraId: 1,
	notas: null,
};

const EMPLEADO = {
	empleadoId: 3,
	puestoId: 1,
	puestoNombre: "Vendedor",
	nombre: "Juan",
	apellidoPaterno: "Pérez",
	apellidoMaterno: "López",
	curp: null,
	nss: null,
	telefono: null,
	email: null,
	calle: null,
	colonia: null,
	ciudadId: null,
	cp: null,
	fechaIngreso: "2025-01-15",
	fechaBaja: null,
	sueldoDiario: 350,
	activo: true,
	fotoUrl: null,
};

function renderPage() {
	const client = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	return render(
		<MemoryRouter>
			<QueryClientProvider client={client}>
				<ToastProvider>
					<NominaPage />
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
	vi.mocked(apiEmpleados).mockResolvedValue({
		success: true,
		data: [EMPLEADO],
		meta: { page: 0, size: 100, totalElements: 1, totalPages: 1 },
	} as never);
	vi.mocked(apiNomina).mockResolvedValue({
		success: true,
		data: [NOMINA],
		meta: { page: 0, size: 15, totalElements: 1, totalPages: 1 },
	} as never);
});

describe("NominaPage (smoke)", () => {
	it("renderiza título, tabla y acciones principales", async () => {
		renderPage();
		expect(screen.getByRole("heading", { name: "Nómina" })).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Nueva nómina/ }),
		).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Generar nómina/ }),
		).toBeInTheDocument();
		expect(await screen.findByText("Pérez López Juan")).toBeInTheDocument();
		expect(screen.getByText("PENDIENTE")).toBeInTheDocument();
		expect(screen.getByLabelText("Filtrar por estado")).toBeInTheDocument();
	});

	it("abre el diálogo de nueva nómina", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Pérez López Juan");
		await user.click(screen.getByRole("button", { name: /Nueva nómina/ }));
		expect(await screen.findByRole("heading", { name: "Nueva nómina" })).toBeInTheDocument();
		expect(screen.getByLabelText(/Empleado/)).toBeInTheDocument();
		expect(screen.getByLabelText(/Días pagados/)).toBeInTheDocument();
	});

	it("abre el diálogo de generar nómina por quincena", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Pérez López Juan");
		await user.click(screen.getByRole("button", { name: /Generar nómina/ }));
		expect(
			await screen.findByText("Generar nómina por quincena"),
		).toBeInTheDocument();
		expect(screen.getByLabelText(/Quincena/)).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Generar" })).toBeInTheDocument();
	});
});

const NOMINA_PAGADA = {
	nominaId: 8,
	empleadoId: 3,
	empleado: "Pérez López Juan",
	periodoIni: "2026-08-16",
	periodoFin: "2026-08-31",
	diasPagados: 15,
	percepciones: 5250,
	deducciones: 250,
	netoPagar: 5000,
	estado: "PAGADA",
	fechaPago: "2026-09-01",
	usuarioRegistraId: 1,
	notas: null,
};

describe("NominaPage (profundización)", () => {
	it("filtra por estado y recarga la lista", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Pérez López Juan");
		await user.selectOptions(
			screen.getByLabelText("Filtrar por estado"),
			"PAGADA",
		);
		await waitFor(() =>
			expect(vi.mocked(apiNomina)).toHaveBeenLastCalledWith(
				expect.objectContaining({ estado: "PAGADA", page: 0 }),
			),
		);
	});

	it("filtra por rango de fechas y lo quita con Todos", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Pérez López Juan");
		await user.click(
			screen.getByRole("button", { name: /Filtrar por rango/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiNomina)).toHaveBeenLastCalledWith(
				expect.objectContaining({ desde: expect.any(String) }),
			),
		);
		await user.click(screen.getByRole("button", { name: "Todos" }));
		await waitFor(() =>
			expect(vi.mocked(apiNomina)).toHaveBeenLastCalledWith(
				expect.objectContaining({ page: 0 }),
			),
		);
		const ultima = vi.mocked(apiNomina).mock.calls.at(-1)?.[0] as Record<
			string,
			unknown
		>;
		expect(ultima.desde).toBeUndefined();
		expect(ultima.hasta).toBeUndefined();
	});

	it("muestra la nómina pagada con fecha y sin acciones", async () => {
		vi.mocked(apiNomina).mockResolvedValueOnce({
			success: true,
			data: [NOMINA_PAGADA],
			meta: { page: 0, size: 15, totalElements: 1, totalPages: 1 },
		} as never);
		renderPage();
		expect(await screen.findByText("PAGADA")).toBeInTheDocument();
		expect(
			screen.queryByRole("button", { name: /Pagar nómina de/ }),
		).not.toBeInTheDocument();
		expect(
			screen.queryByRole("button", { name: /Cancelar nómina de/ }),
		).not.toBeInTheDocument();
	});

	it("crea una nómina válida y muestra éxito", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearNomina).mockResolvedValueOnce({ ...NOMINA, nominaId: 9 } as never);
		renderPage();
		await screen.findByText("Pérez López Juan");
		await user.click(screen.getByRole("button", { name: /Nueva nómina/ }));
		const dialogo = await screen.findByRole("dialog", { name: "Nueva nómina" });
		await user.selectOptions(within(dialogo).getByLabelText(/Empleado/), "3");
		await user.type(within(dialogo).getByLabelText(/Percepciones/), "5250");
		await user.type(within(dialogo).getByLabelText(/Deducciones/), "250");
		await user.type(within(dialogo).getByLabelText(/Notas/), "Quincena 1");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		await waitFor(() =>
			expect(vi.mocked(apiCrearNomina)).toHaveBeenCalledWith(
				expect.objectContaining({
					empleadoId: 3,
					diasPagados: 14,
					percepciones: 5250,
					deducciones: 250,
					notas: "Quincena 1",
				}),
			),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({ text: expect.stringContaining("Nómina creada") }),
		);
	});

	it("valida periodo invertido y campos vacíos", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Pérez López Juan");
		await user.click(screen.getByRole("button", { name: /Nueva nómina/ }));
		const dialogo = await screen.findByRole("dialog", { name: "Nueva nómina" });
		const ini = within(dialogo).getByLabelText(/Periodo inicial/);
		fireEvent.change(ini, { target: { value: "2030-01-10" } });
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		expect(
			await within(dialogo).findByText(
				"El periodo final debe ser igual o posterior al inicial.",
			),
		).toBeInTheDocument();
		expect(apiCrearNomina).not.toHaveBeenCalled();
	});

	it("muestra toast si crear la nómina falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearNomina).mockRejectedValueOnce(new Error("traslape"));
		renderPage();
		await screen.findByText("Pérez López Juan");
		await user.click(screen.getByRole("button", { name: /Nueva nómina/ }));
		const dialogo = await screen.findByRole("dialog", { name: "Nueva nómina" });
		await user.selectOptions(within(dialogo).getByLabelText(/Empleado/), "3");
		await user.type(within(dialogo).getByLabelText(/Percepciones/), "5250");
		await user.type(within(dialogo).getByLabelText(/Deducciones/), "250");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("traslape") }),
			),
		);
	});

	it("paga la nómina pendiente con confirmación", async () => {
		const user = userEvent.setup();
		vi.mocked(apiPagarNomina).mockResolvedValueOnce({ ...NOMINA, estado: "PAGADA" } as never);
		renderPage();
		await screen.findByText("Pérez López Juan");
		await user.click(
			screen.getByRole("button", { name: "Pagar nómina de Pérez López Juan" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Confirmar pago de nómina",
		});
		await user.click(within(dialogo).getByRole("button", { name: /Sí, pagar/ }));
		await waitFor(() =>
			expect(vi.mocked(apiPagarNomina)).toHaveBeenCalledWith(7),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({ text: expect.stringContaining("Nómina pagada.") }),
		);
	});

	it("cancela el pago sin llamar al backend", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Pérez López Juan");
		await user.click(
			screen.getByRole("button", { name: "Pagar nómina de Pérez López Juan" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Confirmar pago de nómina",
		});
		await user.click(within(dialogo).getByRole("button", { name: /Cancelar/ }));
		await waitFor(() =>
			expect(
				screen.queryByRole("dialog", { name: "Confirmar pago de nómina" }),
			).not.toBeInTheDocument(),
		);
		expect(apiPagarNomina).not.toHaveBeenCalled();
	});

	it("muestra toast si pagar falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiPagarNomina).mockRejectedValueOnce(new Error("sin fondos"));
		renderPage();
		await screen.findByText("Pérez López Juan");
		await user.click(
			screen.getByRole("button", { name: "Pagar nómina de Pérez López Juan" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Confirmar pago de nómina",
		});
		await user.click(within(dialogo).getByRole("button", { name: /Sí, pagar/ }));
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin fondos") }),
			),
		);
	});

	it("cancela la nómina pendiente con confirmación", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCancelarNomina).mockResolvedValueOnce({ ...NOMINA, estado: "CANCELADA" } as never);
		renderPage();
		await screen.findByText("Pérez López Juan");
		await user.click(
			screen.getByRole("button", {
				name: "Cancelar nómina de Pérez López Juan",
			}),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Confirmar cancelación",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: "Sí, cancelar" }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiCancelarNomina)).toHaveBeenCalledWith(7),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({ text: expect.stringContaining("Nómina cancelada") }),
		);
	});

	it("muestra toast si cancelar la nómina falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCancelarNomina).mockRejectedValueOnce(new Error("ya pagada"));
		renderPage();
		await screen.findByText("Pérez López Juan");
		await user.click(
			screen.getByRole("button", {
				name: "Cancelar nómina de Pérez López Juan",
			}),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Confirmar cancelación",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: "Sí, cancelar" }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("ya pagada") }),
			),
		);
	});

	it("genera la quincena con la segunda quincena y muestra éxito", async () => {
		const user = userEvent.setup();
		vi.mocked(apiGenerarQuincena).mockResolvedValueOnce({
			creadas: 2,
			omitidas: 1,
			periodoIni: "2026-09-16",
			periodoFin: "2026-09-30",
			nominas: [],
		} as never);
		renderPage();
		await screen.findByText("Pérez López Juan");
		await user.click(screen.getByRole("button", { name: /Generar nómina/ }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Generar nómina por quincena",
		});
		await user.selectOptions(within(dialogo).getByLabelText(/Quincena/), "SEGUNDA");
		expect(screen.getByText(/Periodo:/)).toBeInTheDocument();
		await user.click(within(dialogo).getByRole("button", { name: "Generar" }));
		await waitFor(() =>
			expect(vi.mocked(apiGenerarQuincena)).toHaveBeenCalledWith({
				quincena: "SEGUNDA",
			}),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({ text: expect.stringContaining("Nómina generada: 2 creadas") }),
		);
	});

	it("avisa cuando la quincena ya existe sin crear nada", async () => {
		const user = userEvent.setup();
		vi.mocked(apiGenerarQuincena).mockResolvedValueOnce({
			creadas: 0,
			omitidas: 3,
			periodoIni: "2026-09-01",
			periodoFin: "2026-09-15",
			nominas: [],
		} as never);
		renderPage();
		await screen.findByText("Pérez López Juan");
		await user.click(screen.getByRole("button", { name: /Generar nómina/ }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Generar nómina por quincena",
		});
		await user.click(within(dialogo).getByRole("button", { name: "Generar" }));
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("Nómina ya existente") }),
			),
		);
	});

	it("muestra toast si generar la quincena falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiGenerarQuincena).mockRejectedValueOnce(new Error("fuera de mes"));
		renderPage();
		await screen.findByText("Pérez López Juan");
		await user.click(screen.getByRole("button", { name: /Generar nómina/ }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Generar nómina por quincena",
		});
		await user.click(within(dialogo).getByRole("button", { name: "Generar" }));
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("fuera de mes") }),
			),
		);
	});

	it("paga el lote de pendientes y muestra el resumen", async () => {
		const user = userEvent.setup();
		vi.mocked(apiPagarNominaLote).mockResolvedValueOnce({
			pagadas: 1,
			omitidas: 0,
			nominas: [],
		} as never);
		renderPage();
		await screen.findByText("Pérez López Juan");
		await user.click(screen.getByRole("button", { name: /Pagar nómina \(1\)/ }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Pagar nómina (lote)",
		});
		expect(within(dialogo).getByText("Pérez López Juan")).toBeInTheDocument();
		await user.click(
			within(dialogo).getByRole("button", { name: "Sí, pagar 1" }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiPagarNominaLote)).toHaveBeenCalledWith([7]),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({ text: expect.stringContaining("1 pagadas") }),
		);
	});

	it("muestra toast si pagar el lote falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiPagarNominaLote).mockRejectedValueOnce(new Error("lote fallido"));
		renderPage();
		await screen.findByText("Pérez López Juan");
		await user.click(screen.getByRole("button", { name: /Pagar nómina \(1\)/ }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Pagar nómina (lote)",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: "Sí, pagar 1" }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("lote fallido") }),
			),
		);
	});

	it("pagina la lista cuando hay más de una página", async () => {
		const user = userEvent.setup();
		vi.mocked(apiNomina).mockResolvedValue({
			success: true,
			data: [NOMINA],
			meta: { page: 0, size: 15, totalElements: 30, totalPages: 2 },
		} as never);
		renderPage();
		await screen.findByText("Pérez López Juan");
		await user.click(screen.getByRole("button", { name: /Página siguiente/i }));
		await waitFor(() =>
			expect(vi.mocked(apiNomina)).toHaveBeenCalledWith(
				expect.objectContaining({ page: 1 }),
			),
		);
	});

	it("exporta la nómina visible a Excel", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Pérez López Juan");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^nomina-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});

	it("muestra estado vacío cuando no hay nóminas", async () => {
		vi.mocked(apiNomina).mockResolvedValueOnce({
			success: true,
			data: [],
			meta: { page: 0, size: 15, totalElements: 0, totalPages: 0 },
		} as never);
		renderPage();
		expect(await screen.findByText("Sin nóminas")).toBeInTheDocument();
	});

	it("muestra toast si la lista falla al cargar", async () => {
		vi.mocked(apiNomina).mockRejectedValueOnce(new Error("sin conexión"));
		renderPage();
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin conexión") }),
			),
		);
	});
});
