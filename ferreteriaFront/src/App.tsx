import { useEffect } from "react";
import { RouterProvider } from "react-router-dom";

import { useUiStore } from "@/store/ui";
import { ensureCsrfCookie } from "@/lib/api/client";
import { apiMe } from "@/lib/api/endpoints";
import { useAuthStore } from "@/store/auth";
import { router } from "./router/router";

/** Aplica tema (claro/oscuro/sistema) e idioma al root del documento. */
function SincronizarUI() {
	const tema = useUiStore((s) => s.tema);
	const idioma = useUiStore((s) => s.idioma);

	useEffect(() => {
		const media = window.matchMedia("(prefers-color-scheme: dark)");
		const aplicar = () => {
			const oscuro = tema === "dark" || (tema === "system" && media.matches);
			document.documentElement.classList.toggle("dark", oscuro);
		};
		aplicar();
		if (tema === "system") {
			media.addEventListener("change", aplicar);
			return () => media.removeEventListener("change", aplicar);
		}
	}, [tema]);

	useEffect(() => {
		document.documentElement.lang = idioma === "en" ? "en" : "es-MX";
	}, [idioma]);

	return null;
}

/**
 * Garantiza que la cookie XSRF-TOKEN exista antes de cualquier mutating
 * request. Se llama una vez al montar la app; idempotente.
 */
function BootstrapCsrf() {
	useEffect(() => {
		void ensureCsrfCookie();
	}, []);
	return null;
}

/**
 * FRONT-SEC-002: al montar la app revalidamos la sesion contra el backend
 * con /auth/me (la cookie HttpOnly `at` es la fuente de verdad, no
 * localStorage). Si responde 200 -> setMe; si 401 -> clearSession y el guard
 * redirige a /login. Una sola llamada por mount; errores silenciosos.
 */
function BootstrapSesion() {
	const setMe = useAuthStore((s) => s.setMe);
	const clearSession = useAuthStore((s) => s.clearSession);
	useEffect(() => {
		apiMe()
			.then((me) => setMe(me))
			.catch(() => clearSession());
	}, [setMe, clearSession]);
	return null;
}

export default function App() {
	return (
		<>
			<SincronizarUI />
			<BootstrapCsrf />
			<BootstrapSesion />
			<RouterProvider router={router} />
		</>
	);
}
