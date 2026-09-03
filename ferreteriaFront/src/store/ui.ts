import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";

export type Tema = "light" | "dark" | "system";
export type Idioma = "es" | "en";

interface UiState {
	tema: Tema;
	idioma: Idioma;
	setTema: (tema: Tema) => void;
	setIdioma: (idioma: Idioma) => void;
}

/** Preferencias de UI (tema e idioma) persistidas en localStorage. */
export const useUiStore = create<UiState>()(
	persist(
		(set) => ({
			tema: "system",
			idioma: "es",
			setTema: (tema) => set({ tema }),
			setIdioma: (idioma) => set({ idioma }),
		}),
		{
			name: "ferreteria-ui",
			storage: createJSONStorage(() => localStorage),
		},
	),
);
