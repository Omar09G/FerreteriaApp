import type { ReactNode } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";

import { useAutenticado, tieneRol, useAuthStore } from "@/store/auth";
import { AccessDenied } from "@/components/errors/PageStates";
import { Spinner } from "@/components/ui/Spinner";

/**
 * Pantalla de espera mientras el bootstrap revalida la sesión (/auth/me).
 * Los guards NUNCA deciden con un flag obsoleto: sin sesionLista no hay
 * ni contenido privado ni redirect (evita el flash de sistema sin OTP).
 */
export function SplashVerificando() {
	return (
		<div className="flex min-h-screen items-center justify-center">
			<Spinner />
		</div>
	);
}

/** Requiere sesión activa; si no, redirige a /login conservando el destino. */
export function RequiereAuth() {
	const autenticado = useAutenticado();
	const sesionLista = useAuthStore((s) => s.sesionLista);
	const location = useLocation();
	if (!sesionLista) {
		return <SplashVerificando />;
	}
	if (!autenticado) {
		return <Navigate to="/login" replace state={{ from: location.pathname }} />;
	}
	return <Outlet />;
}

/** Requiere que el usuario tenga al menos uno de los roles indicados. */
export function RequiereRol({
	roles,
	children,
}: {
	roles: string[];
	children?: ReactNode;
}) {
	const rolesUsuario = useAuthStore((s) => s.usuario?.roles);
	if (!tieneRol(rolesUsuario, roles)) {
		return <AccessDenied />;
	}
	return children ?? <Outlet />;
}

/** /login redirige al dashboard si ya hay sesión. */
export function RedirigirSiAutenticado({ children }: { children: ReactNode }) {
	const autenticado = useAutenticado();
	const sesionLista = useAuthStore((s) => s.sesionLista);
	if (!sesionLista) {
		return <SplashVerificando />;
	}
	if (autenticado) return <Navigate to="/dashboard" replace />;
	return children;
}
