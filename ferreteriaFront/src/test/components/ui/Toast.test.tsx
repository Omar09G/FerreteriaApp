import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Swal from "sweetalert2";

import { ToastProvider, useToast } from "@/components/ui/Toast";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

const fireMock = vi.mocked(Swal.fire);
const closeMock = vi.mocked(Swal.close);

function Botonera() {
	const { success, error, loading } = useToast();
	return (
		<div>
			<button onClick={() => success("ok")}>Exito</button>
			<button onClick={() => error("fallo")}>Fallo</button>
			<button
				onClick={() => {
					const cerrar = loading("Cargando");
					cerrar();
				}}
			>
				Carga
			</button>
		</div>
	);
}

describe("Toast", () => {
	it("success dispara Swal.fire como toast", async () => {
		const user = userEvent.setup();
		render(
			<ToastProvider>
				<Botonera />
			</ToastProvider>,
		);
		await user.click(screen.getByRole("button", { name: "Exito" }));
		expect(fireMock).toHaveBeenCalledWith(
			expect.objectContaining({ toast: true, icon: "success", text: "ok" }),
		);
	});

	it("error usa timer largo de 8s", async () => {
		const user = userEvent.setup();
		render(
			<ToastProvider>
				<Botonera />
			</ToastProvider>,
		);
		await user.click(screen.getByRole("button", { name: "Fallo" }));
		expect(fireMock).toHaveBeenCalledWith(
			expect.objectContaining({ icon: "error", timer: 8000 }),
		);
	});

	it("loading abre y cerrar cierra el overlay", async () => {
		const user = userEvent.setup();
		render(
			<ToastProvider>
				<Botonera />
			</ToastProvider>,
		);
		await user.click(screen.getByRole("button", { name: "Carga" }));
		expect(fireMock).toHaveBeenCalledWith(
			expect.objectContaining({ title: "Cargando" }),
		);
		expect(closeMock).toHaveBeenCalled();
	});

	it("useToast fuera del provider lanza error", () => {
		function SinProvider() {
			useToast();
			return null;
		}
		expect(() => render(<SinProvider />)).toThrow(
			"useToast debe usarse dentro de <ToastProvider>",
		);
	});
});
