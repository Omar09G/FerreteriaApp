import { withFallback } from "@/router/builder";

const NotificacionesPage = withFallback(
	() => import("@/features/notificaciones/NotificacionesPage"),
);

export const notificacionesRoutes = [
	{
		path: "notificaciones",
		element: <NotificacionesPage />,
	},
];
