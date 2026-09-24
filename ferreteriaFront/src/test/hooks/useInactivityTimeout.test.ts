import { renderHook, act } from "@testing-library/react";
import {
	afterEach,
	beforeEach,
	describe,
	expect,
	it,
	vi,
	type Mock,
} from "vitest";

import { useInactivityTimeout } from "@/hooks/useInactivityTimeout";
import { useAuthStore } from "@/store/auth";
import { env } from "@/config/env";
import { apiLogout } from "@/lib/api/endpoints";
import { useToast } from "@/components/ui/Toast";

vi.mock("@/lib/api/endpoints", () => ({ apiLogout: vi.fn() }));
vi.mock("@/components/ui/Toast", () => ({ useToast: vi.fn() }));

const apiLogoutMock = apiLogout as unknown as Mock;
const useToastMock = useToast as unknown as Mock;

const AHORA = new Date("2026-05-01T12:00:00").getTime();
const TIMEOUT = 120_000; // 2 min → aviso a los 60s (timeout - 60s)

let warning: Mock;
let info: Mock;
let originalTimeout: number;

function montarAutenticado(ultimaActividad: number) {
	useAuthStore.setState({
		autenticado: true,
		usuario: null,
		lastActivityAt: ultimaActividad,
	});
	return renderHook(() => useInactivityTimeout());
}

beforeEach(() => {
	originalTimeout = env.sessionTimeoutMs;
	env.sessionTimeoutMs = TIMEOUT;
	vi.useFakeTimers();
	vi.setSystemTime(AHORA);
	warning = vi.fn();
	info = vi.fn();
	useToastMock.mockReturnValue({
		toast: vi.fn(),
		success: vi.fn(),
		error: vi.fn(),
		info,
		warning,
		loading: vi.fn(),
	});
	apiLogoutMock.mockResolvedValue({ ok: true });
	localStorage.clear();
	useAuthStore.setState({
		autenticado: false,
		usuario: null,
		lastActivityAt: AHORA,
	});
});

afterEach(() => {
	env.sessionTimeoutMs = originalTimeout;
	vi.useRealTimers();
	vi.clearAllMocks();
});

describe("useInactivityTimeout", () => {
	it("es no-op si sessionTimeoutMs es 0", async () => {
		env.sessionTimeoutMs = 0;
		montarAutenticado(AHORA - TIMEOUT - 1);
		await act(async () => {
			await vi.advanceTimersByTimeAsync(60_000);
		});
		expect(apiLogoutMock).not.toHaveBeenCalled();
		expect(useAuthStore.getState().autenticado).toBe(true);
	});

	it("sin autenticación no hace logout aunque pase el timeout", async () => {
		useAuthStore.setState({ autenticado: false, lastActivityAt: AHORA - 10 * TIMEOUT });
		renderHook(() => useInactivityTimeout());
		await act(async () => {
			await vi.advanceTimersByTimeAsync(30_000);
		});
		expect(apiLogoutMock).not.toHaveBeenCalled();
		expect(warning).not.toHaveBeenCalled();
	});

	it("avisa 60s antes (una sola vez) y cierra sesión al cumplir el timeout", async () => {
		montarAutenticado(AHORA - 61_000); // ya superó el aviso (120s - 60s)
		await act(async () => {
			await vi.advanceTimersByTimeAsync(5_000);
		});
		expect(info).toHaveBeenCalledTimes(1);
		expect(info).toHaveBeenCalledWith(expect.stringContaining("1 minuto"));
		expect(apiLogoutMock).not.toHaveBeenCalled();

		// Más ticks: el aviso no se repite.
		await act(async () => {
			await vi.advanceTimersByTimeAsync(10_000);
		});
		expect(info).toHaveBeenCalledTimes(1);

		// Al superar 120s de inactividad: logout + clearSession + aviso final.
		await act(async () => {
			await vi.advanceTimersByTimeAsync(60_000);
		});
		expect(apiLogoutMock).toHaveBeenCalledTimes(1);
		expect(useAuthStore.getState().autenticado).toBe(false);
		expect(warning).toHaveBeenCalledTimes(1);
		expect(warning).toHaveBeenCalledWith(expect.stringContaining("inactividad"));

		// Tras cerrar, no reintenta el logout en ticks posteriores.
		await act(async () => {
			await vi.advanceTimersByTimeAsync(30_000);
		});
		expect(apiLogoutMock).toHaveBeenCalledTimes(1);
	});

	it("aunque apiLogout falle, limpia la sesión local (finally)", async () => {
		apiLogoutMock.mockRejectedValueOnce(new Error("red caída"));
		montarAutenticado(AHORA - TIMEOUT - 1);
		await act(async () => {
			await vi.advanceTimersByTimeAsync(5_000);
		});
		expect(apiLogoutMock).toHaveBeenCalledTimes(1);
		expect(useAuthStore.getState().autenticado).toBe(false);
		expect(warning).toHaveBeenCalledTimes(1);
	});

	it("la actividad (throttle 1s en mousemove) pospone el cierre", async () => {
		montarAutenticado(AHORA - 110_000); // a 10s del logout
		const antes = useAuthStore.getState().lastActivityAt;
		act(() => {
			document.dispatchEvent(new Event("mousemove"));
		});
		expect(useAuthStore.getState().lastActivityAt).toBe(AHORA);
		expect(AHORA).toBeGreaterThan(antes);

		// Dentro del mismo segundo el segundo evento se throttlea.
		vi.setSystemTime(AHORA + 500);
		act(() => {
			document.dispatchEvent(new Event("mousemove"));
		});
		expect(useAuthStore.getState().lastActivityAt).toBe(AHORA);

		// Pasado 1s el ping vuelve a registrar actividad.
		vi.setSystemTime(AHORA + 1_100);
		act(() => {
			document.dispatchEvent(new Event("mousemove"));
		});
		expect(useAuthStore.getState().lastActivityAt).toBe(AHORA + 1_100);

		// Sin más actividad el temporizador ya no alcanza el timeout.
		await act(async () => {
			await vi.advanceTimersByTimeAsync(60_000);
		});
		expect(apiLogoutMock).not.toHaveBeenCalled();
		expect(useAuthStore.getState().autenticado).toBe(true);
	});

	it("al desmontar limpia listeners e interval", async () => {
		const { unmount } = montarAutenticado(AHORA - TIMEOUT - 1);
		unmount();
		await act(async () => {
			await vi.advanceTimersByTimeAsync(30_000);
		});
		act(() => {
			document.dispatchEvent(new Event("keydown"));
		});
		expect(apiLogoutMock).not.toHaveBeenCalled();
	});
});
