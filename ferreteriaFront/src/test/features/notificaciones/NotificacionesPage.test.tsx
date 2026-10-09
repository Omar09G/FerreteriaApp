import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import NotificacionesPage from "@/features/notificaciones/NotificacionesPage";
import * as apiNotif from "@/lib/api/notificaciones";
import type { Notificacion } from "@/lib/api/types";
import { renderConProviders } from "@/test/helpers/renderProveedores";

vi.mock("@/lib/api/notificaciones");
vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));
vi.mock("@/store/notificaciones", () => ({
	marcarTodoLeido: vi.fn().mockResolvedValue(undefined),
	rutaNotificacion: vi.fn().mockReturnValue("/dashboard"),
	useNotificacionesStore: (sel: (s: { marcarLeidaLocal: (id: number) => void }) => unknown) =>
		sel({ marcarLeidaLocal: vi.fn() }),
}));

const LEIDA: Notificacion = {
	bandejaId: 1,
	tipo: "VENTA_TICKET",
	titulo: "Venta V-1",
	detalle: null,
	refTipo: "VENTA",
	refId: 5,
	leidaEn: new Date().toISOString(),
	creadaEn: new Date().toISOString(),
};

const NO_LEIDA: Notificacion = { ...LEIDA, bandejaId: 2, titulo: "Venta V-2", leidaEn: null };

beforeEach(() => {
	vi.mocked(apiNotif.apiNotificaciones).mockResolvedValue({
		success: true,
		data: [LEIDA, NO_LEIDA],
		meta: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
	});
	vi.mocked(apiNotif.apiMarcarLeida).mockResolvedValue(LEIDA);
});

describe("NotificacionesPage", () => {
	it("elimina las leídas tras confirmar", async () => {
		const user = userEvent.setup();
		vi.mocked(apiNotif.apiEliminarLeidas).mockResolvedValue(1);
		renderConProviders(<NotificacionesPage />);
		expect(await screen.findByText("Venta V-1")).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Eliminar leídas" }));
		expect(await screen.findByText("Eliminar avisos leídos")).toBeInTheDocument();
		const confirmar = screen.getAllByRole("button", { name: "Eliminar leídas" });
		await user.click(confirmar[confirmar.length - 1]);
		expect(apiNotif.apiEliminarLeidas).toHaveBeenCalledTimes(1);
	});

	it("deshabilita eliminar cuando no hay leídas", async () => {
		vi.mocked(apiNotif.apiNotificaciones).mockResolvedValue({
			success: true,
			data: [NO_LEIDA],
			meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
		});
		renderConProviders(<NotificacionesPage />);
		expect(await screen.findByText("Venta V-2")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Eliminar leídas" })).toBeDisabled();
	});
});
