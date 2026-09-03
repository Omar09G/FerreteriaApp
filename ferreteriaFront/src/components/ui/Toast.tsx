/* eslint-disable react-refresh/only-export-components */
import {
	createContext,
	useCallback,
	useContext,
	useMemo,
	type ReactNode,
} from "react";
import Swal, { type SweetAlertIcon } from "sweetalert2";

import { useT } from "@/i18n";

type TipoToast = "success" | "error" | "info" | "warning";

interface ToastContextValue {
	toast: (tipo: TipoToast, mensaje: string) => void;
	success: (mensaje: string) => void;
	error: (mensaje: string) => void;
	info: (mensaje: string) => void;
	warning: (mensaje: string) => void;
	/**
	 * Muestra un overlay de carga (SweetAlert2 con spinner). Devuelve una
	 * función `close()` para que el caller lo cierre cuando termine.
	 */
	loading: (mensaje: string) => () => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

const ICON: Record<TipoToast, SweetAlertIcon> = {
	success: "success",
	error: "error",
	info: "info",
	warning: "warning",
};

const TITULO_KEY: Record<TipoToast, string> = {
	success: "alerta.success",
	error: "alerta.error",
	info: "alerta.info",
	warning: "alerta.warning",
};

/**
 * Provider sin estado propio: delega en SweetAlert2 directamente. Mantiene la
 * misma API `useToast()` que la versión anterior (toast/success/error/info)
 * para no romper a los 17+ consumidores. Las notificaciones se renderizan
 * como toasts en la esquina superior derecha sin bloquear la UI.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
	const t = useT();

	const toast = useCallback(
		(tipo: TipoToast, mensaje: string) => {
			void Swal.fire({
				toast: true,
				position: "top-end",
				icon: ICON[tipo],
				title: t(TITULO_KEY[tipo]),
				text: mensaje,
				showConfirmButton: false,
				timer: tipo === "error" ? 8000 : 5000,
				timerProgressBar: true,
			});
		},
		[t],
	);

	const loading = useCallback((mensaje: string) => {
		Swal.fire({
			title: mensaje,
			allowOutsideClick: false,
			allowEscapeKey: false,
			showConfirmButton: false,
			didOpen: () => {
				Swal.showLoading();
			},
		});
		return () => {
			Swal.close();
		};
	}, []);

	const value = useMemo<ToastContextValue>(
		() => ({
			toast,
			success: (m) => toast("success", m),
			error: (m) => toast("error", m),
			info: (m) => toast("info", m),
			warning: (m) => toast("warning", m),
			loading,
		}),
		[toast, loading],
	);

	return (
		<ToastContext.Provider value={value}>{children}</ToastContext.Provider>
	);
}

export function useToast(): ToastContextValue {
	const ctx = useContext(ToastContext);
	if (!ctx) throw new Error("useToast debe usarse dentro de <ToastProvider>");
	return ctx;
}
