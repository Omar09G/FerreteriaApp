import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";

import Otp from "@/features/auth/Otp";
import { apiSolicitarOtp, apiVerificarOtp } from "@/lib/api/endpoints";
import { ensureCsrfCookie } from "@/lib/api/client";
import { useAuthStore } from "@/store/auth";

vi.mock("@/lib/api/endpoints", () => ({
  apiSolicitarOtp: vi.fn(),
  apiVerificarOtp: vi.fn(),
}));

vi.mock("@/lib/api/client", () => ({
  ensureCsrfCookie: vi.fn(),
  mensajeError: vi.fn((e: unknown) =>
    e instanceof Error ? e.message : String(e),
  ),
}));

const CHALLENGE = {
  challengeId: "ch-1",
  canales: ["email", "whatsapp"],
  emailEnmascarado: "ad***@x",
  whatsappEnmascarado: "***567",
  expiraEnSegundos: 300,
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/auth/otp"]}>
      <Routes>
        <Route path="/auth/otp" element={<Otp />} />
        <Route path="/dashboard" element={<div>Panel</div>} />
        <Route path="/login" element={<div>Ingreso</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  sessionStorage.clear();
  useAuthStore.setState({
    autenticado: false,
    usuario: null,
    challenge: null,
    lastActivityAt: 0,
  });
  sessionStorage.clear();
  vi.mocked(ensureCsrfCookie).mockResolvedValue(undefined as never);
  vi.mocked(apiSolicitarOtp).mockResolvedValue(undefined as never);
});

describe("Otp", () => {
  it("sin desafío muestra aviso y enlace al login", () => {
    renderPage();
    expect(screen.getByText(/No hay una verificación pendiente/)).toBeInTheDocument();
    expect(screen.getByText("Volver al inicio de sesión")).toBeInTheDocument();
  });

  it("muestra los canales con destinos enmascarados", () => {
    useAuthStore.getState().setChallenge({ ...CHALLENGE } as never);
    renderPage();
    expect(screen.getByText("Verificación en dos pasos")).toBeInTheDocument();
    expect(screen.getByText("ad***@x")).toBeInTheDocument();
    expect(screen.getByText("***567")).toBeInTheDocument();
  });

  it("envía el código por el canal elegido y pide los 6 dígitos", async () => {
    const user = userEvent.setup();
    useAuthStore.getState().setChallenge({ ...CHALLENGE } as never);
    renderPage();
    await user.click(screen.getByRole("button", { name: "Enviar código" }));
    await vi.waitFor(() => {
      expect(apiSolicitarOtp).toHaveBeenCalledWith({
        challengeId: "ch-1",
        canal: "email",
      });
    });
    expect(await screen.findByLabelText(/Código de 6 dígitos/)).toBeInTheDocument();
  });

  it("verifica el código, abre sesión y entra al dashboard", async () => {
    const user = userEvent.setup();
    useAuthStore.getState().setChallenge({ ...CHALLENGE } as never);
    vi.mocked(apiVerificarOtp).mockResolvedValue({
      accessToken: "tok-abc",
      expiresInSeconds: 900,
      usuario: { usuarioId: 1, username: "admin", roles: ["ADMINISTRADOR"] },
    } as never);
    renderPage();
    await user.click(screen.getByRole("button", { name: "Enviar código" }));
    await user.type(await screen.findByLabelText(/Código de 6 dígitos/), "482913");
    await user.click(screen.getByRole("button", { name: /Verificar/ }));
    await vi.waitFor(() => {
      expect(apiVerificarOtp).toHaveBeenCalledWith({
        challengeId: "ch-1",
        codigo: "482913",
      });
    });
    expect(await screen.findByText("Panel")).toBeInTheDocument();
    expect(useAuthStore.getState().autenticado).toBe(true);
  });

  it("muestra el error cuando el envío falla", async () => {
    const user = userEvent.setup();
    useAuthStore.getState().setChallenge({ ...CHALLENGE } as never);
    vi.mocked(apiSolicitarOtp).mockRejectedValueOnce(new Error("Sin destino"));
    renderPage();
    await user.click(screen.getByRole("button", { name: "Enviar código" }));
    expect(await screen.findByText("Sin destino")).toBeInTheDocument();
  });
});
