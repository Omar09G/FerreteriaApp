import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";

import Login from "@/features/auth/Login";
import { apiGoogleInit, apiLogin } from "@/lib/api/endpoints";
import { ensureCsrfCookie, mensajeError } from "@/lib/api/client";
import { useAuthStore } from "@/store/auth";

vi.mock("@/lib/api/endpoints", () => ({
  apiLogin: vi.fn(),
  apiGoogleInit: vi.fn(),
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
    <MemoryRouter initialEntries={["/login"]}>
      <Login />
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
  vi.mocked(apiLogin).mockResolvedValue({ ...CHALLENGE } as never);
});

describe("Login (smoke)", () => {
  it("renderiza marca, campos, botón de ingreso y botón de Google", () => {
    renderPage();
    expect(screen.getByText("El Tornillo Feliz")).toBeInTheDocument();
    expect(screen.getByText("Sistema de punto de venta")).toBeInTheDocument();
    expect(screen.getByLabelText(/Usuario/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Contraseña/)).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Ingresar" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Google/ }),
    ).toBeInTheDocument();
  });

  it("envía credenciales y guarda el desafío OTP al ingresar", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.type(screen.getByLabelText(/Usuario/), "admin");
    await user.type(screen.getByLabelText(/Contraseña/), "secreto123");
    await user.click(screen.getByRole("button", { name: "Ingresar" }));
    await vi.waitFor(() => {
      expect(ensureCsrfCookie).toHaveBeenCalledOnce();
      expect(apiLogin).toHaveBeenCalledWith({
        username: "admin",
        password: "secreto123",
      });
    });
    // Primera fase: guarda el desafío, NO autentica todavía.
    expect(useAuthStore.getState().challenge?.challengeId).toBe("ch-1");
    expect(useAuthStore.getState().autenticado).toBe(false);
  });

  it("redirige al OTP tras login con destino interno válido", async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={[{ pathname: "/login", state: { from: "/caja" } }]}>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/auth/otp" element={<div>Pantalla OTP</div>} />
        </Routes>
      </MemoryRouter>,
    );
    await user.type(screen.getByLabelText(/Usuario/), "admin");
    await user.type(screen.getByLabelText(/Contraseña/), "secreto123");
    await user.click(screen.getByRole("button", { name: "Ingresar" }));
    expect(await screen.findByText("Pantalla OTP")).toBeInTheDocument();
  });

  it("muestra el error cuando el login falla", async () => {
    const user = userEvent.setup();
    vi.mocked(apiLogin).mockRejectedValueOnce(new Error("Credenciales inválidas"));
    renderPage();
    await user.type(screen.getByLabelText(/Usuario/), "admin");
    await user.type(screen.getByLabelText(/Contraseña/), "mal");
    await user.click(screen.getByRole("button", { name: "Ingresar" }));
    expect(await screen.findByText("Credenciales inválidas")).toBeInTheDocument();
    expect(mensajeError).toHaveBeenCalled();
    expect(useAuthStore.getState().autenticado).toBe(false);
    expect(useAuthStore.getState().challenge).toBeNull();
  });

  it("el botón Google redirige a la URL del backend", async () => {
    const user = userEvent.setup();
    vi.mocked(apiGoogleInit).mockResolvedValue({
      url: "https://accounts.google.com/o/oauth2/v2/auth?x=1",
    });
    // jsdom no navega de verdad: sustituimos location por un objeto
    // observable y verificamos la asignación del redirect.
    Object.defineProperty(window, "location", {
      value: { href: "" },
      writable: true,
      configurable: true,
    });
    renderPage();
    await user.click(screen.getByRole("button", { name: /Google/ }));
    await vi.waitFor(() => {
      expect(apiGoogleInit).toHaveBeenCalledOnce();
    });
    expect(window.location.href).toBe(
      "https://accounts.google.com/o/oauth2/v2/auth?x=1",
    );
  });

  it.each(["https://evil.test/x", "//evil.test/x", "javascript:alert(1)"])(
    "ignora destino forjado %s y cae a /auth/otp",
    async (from) => {
      const user = userEvent.setup();
      render(
        <MemoryRouter initialEntries={[{ pathname: "/login", state: { from } }]}>
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/auth/otp" element={<div>Pantalla OTP</div>} />
            <Route path="/dashboard" element={<div>Panel</div>} />
          </Routes>
        </MemoryRouter>,
      );
      await user.type(screen.getByLabelText(/Usuario/), "admin");
      await user.type(screen.getByLabelText(/Contraseña/), "secreto123");
      await user.click(screen.getByRole("button", { name: "Ingresar" }));
      expect(await screen.findByText("Pantalla OTP")).toBeInTheDocument();
    },
  );
});
