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
			babel({ presets: [reactCompilerPreset()] }),
			tailwindcss(),
		],
		resolve: {
			alias: {
				"@": fileURLToPath(new URL("./src", import.meta.url)),
			},
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
