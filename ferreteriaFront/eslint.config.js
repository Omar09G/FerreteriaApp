import js from "@eslint/js";
import globals from "globals";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import tseslint from "typescript-eslint";
import jsxA11y from "eslint-plugin-jsx-a11y";
import { defineConfig, globalIgnores } from "eslint/config";

export default defineConfig([
	globalIgnores(["dist"]),
	{
		files: ["**/*.{ts,tsx}"],
		extends: [
			js.configs.recommended,
			tseslint.configs.recommended,
			reactHooks.configs.flat.recommended,
			reactRefresh.configs.vite,
			jsxA11y.flatConfigs.recommended,
		],
		languageOptions: {
			globals: globals.browser,
		},
		rules: {
			// Login y POS usan autoFocus intencional para el primer input;
			// se degrada a warning para no bloquear lint pero mantiene visibilidad.
			"jsx-a11y/no-autofocus": "warn",
			// Dialog backdrop usa onMouseDown para cerrar al click en overlay;
			// no es contenido interactivo nativo.
			"jsx-a11y/no-static-element-interactions": "warn",
		},
	},
]);
