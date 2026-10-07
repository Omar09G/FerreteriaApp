import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import ChatPage from "@/features/chat/ChatPage";
import * as apiChat from "@/lib/api/chat";
import type { ChatConversacion, ChatMensaje } from "@/lib/api/types";
import { renderConProviders } from "@/test/helpers/renderProveedores";

vi.mock("@/lib/api/chat");
vi.mock("@/lib/api/admin", () => ({
	apiUsuarios: vi.fn().mockResolvedValue({ data: [], meta: { page: 0, size: 50, totalElements: 0, totalPages: 0 } }),
}));

const CONV: ChatConversacion = {
	conversacionId: 1,
	tipo: "DIRECTA",
	titulo: "cajero",
	participantes: [
		{ usuarioId: 1, username: "gerente" },
		{ usuarioId: 2, username: "cajero" },
	],
	ultimoMensaje: { cuerpo: "Hola", autorNombre: "cajero", creadaEn: new Date().toISOString() },
	noLeidos: 1,
};

beforeEach(() => {
	vi.mocked(apiChat.apiConversaciones).mockResolvedValue([CONV]);
	vi.mocked(apiChat.apiHistorial).mockResolvedValue({
		success: true,
		data: [] as ChatMensaje[],
		meta: { page: 0, size: 30, totalElements: 0, totalPages: 0 },
	});
	vi.mocked(apiChat.apiMarcarChatLeida).mockResolvedValue(undefined);
});

describe("ChatPage", () => {
	it("lista conversaciones y abre el hilo al hacer clic", async () => {
		const user = userEvent.setup();
		renderConProviders(<ChatPage />);
		expect(await screen.findByText("cajero")).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: /cajero/ }));
		expect(apiChat.apiHistorial).toHaveBeenCalledWith(1, { page: 0, size: 30 });
	});

	it("envía con Enter y limpia el campo", async () => {
		const user = userEvent.setup();
		vi.mocked(apiChat.apiEnviarMensaje).mockResolvedValue({
			mensajeId: 9,
			autorId: 1,
			autorNombre: "gerente",
			cuerpo: "Hola",
			creadaEn: new Date().toISOString(),
		});
		renderConProviders(<ChatPage />);
		await user.click(await screen.findByRole("button", { name: /cajero/ }));
		const campo = await screen.findByPlaceholderText("Escribe un mensaje…");
		await user.type(campo, "Hola{enter}");
		expect(apiChat.apiEnviarMensaje).toHaveBeenCalledWith(1, { cuerpo: "Hola" });
	});
});
