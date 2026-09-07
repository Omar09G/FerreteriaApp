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
	const toastRef = useRef(toast);
	const tRef = useRef(t);
	useEffect(() => {
		autenticadoRef.current = autenticado;
	}, [autenticado]);
	useEffect(() => {
		lastRef.current = lastActivityAt;
	}, [lastActivityAt]);
	useEffect(() => {
		toastRef.current = toast;
	}, [toast]);
	useEffect(() => {
		tRef.current = t;
	}, [t]);

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
		// Throttle mousemove a 1s para no saturar Zustand con 100+ updates/seg
		let lastPing = 0;
		const onActivity = () => {
			const now = Date.now();
			if (now - lastPing < 1000) return;
			lastPing = now;
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
				toastRef.current.warning(tRef.current("auth.sesionExpiradaInactividad"));
				return;
			}
			// Aviso 60s antes del logout si la sesión sigue activa.
			const aviso = timeout - 60_000;
			if (!yaAvisadoRef.current && elapsed >= aviso && aviso > 0) {
				yaAvisadoRef.current = true;
				toastRef.current.info(tRef.current("auth.sesionPorExpirar"));
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
		// Intencional: el efecto re-monta listeners + interval cuando cambia el
		// estado de autenticación (login/logout) y se desarma en el cleanup.
		// El resto de las dependencias (pingActivity, clearSession, toast, t) se
		// accede vía refs (líneas 28-43) para mantener identidad estable de los
		// listeners entre re-attaches y no reiniciar el interval por re-render.
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [autenticado]);
}
