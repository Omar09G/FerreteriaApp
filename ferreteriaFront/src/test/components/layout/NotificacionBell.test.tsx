import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";

import { NotificacionBell } from "@/components/layout/NotificacionBell";
import { useNotificacionesStore } from "@/store/notificaciones";
import { renderConProviders } from "@/test/helpers/renderProveedores";

beforeEach(() => {
	useNotificacionesStore.getState().reset();
});

describe("NotificacionBell", () => {
	it("muestra el contador de no leídas", () => {
		useNotificacionesStore.getState().hidratar(
			[
				{
					bandejaId: 1,
					tipo: "VENTA_TICKET",
					titulo: "Venta V-1 registrada",
					detalle: "Total $100.00",
					refTipo: "VENTA",
					refId: 1,
					leidaEn: null,
					creadaEn: new Date().toISOString(),
				},
			],
			3,
		);
		renderConProviders(<NotificacionBell />);
		expect(screen.getByText("3")).toBeInTheDocument();
	});

	it("sin avisos no muestra contador y abre el desplegable vacío", async () => {
		const user = userEvent.setup();
		renderConProviders(<NotificacionBell />);
		expect(screen.queryByText("0")).not.toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Notificaciones" }));
		expect(screen.getByText("Sin avisos por ahora.")).toBeInTheDocument();
	});
});
