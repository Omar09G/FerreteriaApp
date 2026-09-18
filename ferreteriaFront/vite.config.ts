import { fileURLToPath, URL } from "node:url";

import react, { reactCompilerPreset } from "@vitejs/plugin-react";
import babel from "@rolldown/plugin-babel";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig, loadEnv } from "vite";

export default defineConfig(({ mode }) => {
	const env = loadEnv(mode, process.cwd(), "");

	// SOLO en desarrollo: si VITE_DEV_SIN_PROXY=true el front llama directo al
	// backend (client.ts usa baseURL absoluta) y el proxy queda desactivado.
	const sinProxyDev =
		mode === "development" && env.VITE_DEV_SIN_PROXY === "true";

	return {
		plugins: [
			react(),
			// Mantenemos el compilador de React pero con filtros para acelerar un poco el build
			babel({
				presets: [reactCompilerPreset()],
				include: /\.(jsx|tsx)$/, // Evita que Babel procese archivos JS puros, CSS o librerías
			}),
			tailwindcss(),
		],
		resolve: {
			alias: {
				"@": fileURLToPath(new URL("./src", import.meta.url)),
			},
		},
		build: {
			target: "es2022",
			chunkSizeWarningLimit: 600,
			// Aumentamos el límite para que absorba automáticamente assets de menos de 4kB dentro del JS principal
			assetsInlineLimit: 4096,
			rollupOptions: {
				output: {
					manualChunks(id) {
						// 1. Agrupar librerías pesadas (Ya lo tenías)
						if (id.includes("recharts")) return "recharts";
						if (id.includes("@opentelemetry")) return "otel";
						if (id.includes("sweetalert2")) return "swal";

						// 2. SOLUCIÓN A LOS ICONOS SUELTOS: Agrupa lucide y otros iconos en un solo paquete
						if (id.includes("node_modules/lucide-react") || id.includes("node_modules/@lucide")) {
							return "lucide-icons";
						}

						// 3. SOLUCIÓN A LOS HOOKS SUELTOS: Agrupa tus utilidades y custom hooks comunes
						if (id.includes("src/hooks/")) {
							return "custom-hooks";
						}

						return undefined;
					},
				},
			},
		},
		test: {
			environment: "jsdom",
			setupFiles: ["./src/test/setup.ts"],
			globals: true,
		},
		server: {
			proxy: sinProxyDev
				? undefined
				: {
					"/api": {
						target: env.VITE_API_PROXY || "http://localhost:8080",
						changeOrigin: true,
					},
				},
		},
	};
});
