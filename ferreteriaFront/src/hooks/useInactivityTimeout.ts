import { useEffect, useRef } from "react";

import { useAutenticado, useAuthStore } from "@/store/auth";
import { useToast } from "@/components/ui/Toast";
import { useT } from "@/i18n";
import { env } from "@/config/env";

/**
 * Cierra la sesión cuando el usuario pasa `env.sessionTimeoutMs` sin
 * actividad detectable (mouse, teclado, scroll, touch). El refresh queda
 * revocado en backend y la marca de autenticación local se limpia; el
 * access token (cookie HttpOnly) queda muerto en el siguiente request.
 *
 * Se monta una sola vez dentro de la zona autenticada. Si
 * `env.sessionTimeoutMs` es 0, el hook es no-op.
 */
export function useInactivityTimeout() {
	const autenticado = useAutenticado();
	const lastActivityAt = useAuthStore((s) => s.lastActivityAt);
	const pingActivity = useAuthStore((s) => s.pingActivity);
	const clearSession = useAuthStore((s) => s.clearSession);
	const toast = useToast();
	const t = useT();
	const yaAvisadoRef = useRef(false);
	const tickRef = useRef<number | null>(null);

	// Refs para acceder a valores actuales dentro de listeners efímeros.
	const autenticadoRef = useRef(autenticado);
	const lastRef = useRef(lastActivityAt);
	useEffect(() => {
		autenticadoRef.current = autenticado;
	}, [autenticado]);
	useEffect(() => {
		lastRef.current = lastActivityAt;
	}, [lastActivityAt]);

	useEffect(() => {
		const timeout = env.sessionTimeoutMs;
		if (timeout <= 0) return;

		const events: Array<keyof DocumentEventMap> = [
			"mousemove",
			"mousedown",
			"keydown",
			"scroll",
			"touchstart",
			"click",
		];
		const onActivity = () => {
			pingActivity();
			yaAvisadoRef.current = false;
		};
		events.forEach((e) =>
			document.addEventListener(e, onActivity, { passive: true }),
		);

		const check = () => {
			if (!autenticadoRef.current) return;
			const elapsed = Date.now() - lastRef.current;
			if (elapsed >= timeout) {
				clearSession();
				toast.warning(t("auth.sesionExpiradaInactividad"));
				return;
			}
			// Aviso 60s antes del logout si la sesión sigue activa.
			const aviso = timeout - 60_000;
			if (!yaAvisadoRef.current && elapsed >= aviso && aviso > 0) {
				yaAvisadoRef.current = true;
				toast.info(t("auth.sesionPorExpirar"));
			}
		};

		tickRef.current = window.setInterval(check, 5_000);

		return () => {
			events.forEach((e) => document.removeEventListener(e, onActivity));
			if (tickRef.current != null) {
				window.clearInterval(tickRef.current);
				tickRef.current = null;
			}
		};
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [autenticado]);
}
