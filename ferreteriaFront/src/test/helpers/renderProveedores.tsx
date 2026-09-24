import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, type RenderResult } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";

import { ToastProvider } from "@/components/ui/Toast";
import { useAuthStore } from "@/store/auth";
import type { MeResponse } from "@/lib/api/types";

/** Rol por defecto: ADMINISTRADOR (cubre las páginas que exigen rol). */
export function renderConProviders(
	ui: ReactNode,
	opts?: { ruta?: string; roles?: string[] },
): RenderResult {
	const usuario: MeResponse = {
		usuarioId: 1,
		username: "test-admin",
		roles: opts?.roles ?? ["ADMINISTRADOR"],
	};
	useAuthStore.setState({ autenticado: true, usuario, lastActivityAt: Date.now() });
	const qc = new QueryClient({
		defaultOptions: { queries: { retry: false, staleTime: 0 } },
	});
	return render(
		<MemoryRouter initialEntries={[opts?.ruta ?? "/"]}>
			<QueryClientProvider client={qc}>
				<ToastProvider>{ui}</ToastProvider>
			</QueryClientProvider>
		</MemoryRouter>,
	);
}

export function resetAuth() {
	useAuthStore.setState({
		autenticado: false,
		usuario: null,
		lastActivityAt: Date.now(),
	});
}
