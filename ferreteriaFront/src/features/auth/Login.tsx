import { useState } from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { Languages, Lock, Monitor, Moon, Sun, User } from "lucide-react";

import { ensureCsrfCookie, mensajeError } from "@/lib/api/client";
import { apiGoogleInit, apiLogin } from "@/lib/api/endpoints";
import { useAuthStore } from "@/store/auth";
import { useUiStore, type Tema } from "@/store/ui";
import { useT } from "@/i18n";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";

const TEMA_SIGUIENTE: Record<Tema, Tema> = {
  light: "dark",
  dark: "system",
  system: "light",
};
const ICONO_TEMA = { light: Sun, dark: Moon, system: Monitor };

export default function Login() {
  const t = useT();
  useDocumentTitle(t("auth.titulo"));
  const navigate = useNavigate();
  const location = useLocation();
  const setChallenge = useAuthStore((state) => state.setChallenge);
  const tema = useUiStore((s) => s.tema);
  const idioma = useUiStore((s) => s.idioma);
  const setTema = useUiStore((s) => s.setTema);
  const setIdioma = useUiStore((s) => s.setIdioma);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(false);
  const [googleCargando, setGoogleCargando] = useState(false);

  const registro = location.state as { from?: string } | null;
  const [params] = useSearchParams();
  // Aviso de sesión expirada: el interceptor redirige aquí con ?expired=1
  // cuando el refresh falla (contrato client-base → /login?expired=1).
  const avisoExpirada = params.get("expired") === "1" ? t("errores.sesionExpirada") : null;
  const destinoCrudo = registro?.from ?? "/dashboard";
  // Allowlist de redirect interno: solo rutas absolutas del SPA (bloquea
  // //evil, https: y javascript: forjados en location.state).
  const destino =
    destinoCrudo.startsWith("/") && !destinoCrudo.startsWith("//")
      ? destinoCrudo
      : "/dashboard";

  const IconoTema = ICONO_TEMA[tema];

  const enviar = async (e: React.SubmitEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError(null);
    setCargando(true);
    try {
      // Antes del primer mutating request: garantizar que XSRF-TOKEN esté
      // en la cookie para que el interceptor lo copie al header.
      await ensureCsrfCookie();
      // Primera fase: password válida → desafío OTP (los tokens llegan tras
      // verificar el código en /auth/otp).
      const challenge = await apiLogin({ username, password });
      setChallenge(challenge);
      navigate("/auth/otp", { replace: true, state: { from: destino } });
    } catch (err) {
      setError(mensajeError(err));
    } finally {
      setCargando(false);
    }
  };

  const entrarConGoogle = async () => {
    setError(null);
    setGoogleCargando(true);
    try {
      await ensureCsrfCookie();
      const init = await apiGoogleInit();
      // Redirect de navegador (no fetch): Google muestra su pantalla y
      // devuelve al callback del backend, que redirige a /auth/callback.
      window.location.href = init.url;
    } catch (err) {
      setError(mensajeError(err));
      setGoogleCargando(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-linear-to-br from-orange-700 via-primary to-orange-900 p-4">
      <form
        onSubmit={enviar}
        className="w-full max-w-sm rounded-xl border border-white/20 bg-surface p-6 shadow-2xl"
        aria-label={t("auth.ariaForm")}
      >
        <div className="mb-6 flex items-start justify-between">
          <div className="flex-1 text-center">
            <span
              className="mb-2 inline-flex h-12 w-12 items-center justify-center rounded-lg bg-primary text-2xl font-black text-white"
              aria-hidden
            >
              T
            </span>
            <h1 className="text-lg font-bold text-ink">{t("auth.marca")}</h1>
            <p className="text-sm text-muted">{t("auth.sistema")}</p>
          </div>
          <div className="flex flex-col items-end gap-1.5">
            <button
              type="button"
              onClick={() => setTema(TEMA_SIGUIENTE[tema])}
              className="rounded-md border border-line bg-surface p-1.5 text-muted hover:bg-warmbg"
              aria-label={t("auth.tema.cambiar")}
              title={t(`auth.tema.${tema}`)}
            >
              <IconoTema className="h-4 w-4" />
            </button>
            <button
              type="button"
              onClick={() => setIdioma(idioma === "es" ? "en" : "es")}
              className="rounded-md border border-line bg-surface px-2 py-1 text-xs font-semibold text-muted hover:bg-warmbg"
              aria-label={t("auth.idioma.cambiar")}
              title={t("auth.idioma.cambiar")}
            >
              <span className="inline-flex items-center gap-1">
                <Languages className="h-3.5 w-3.5" />
                {idioma === "es" ? "EN" : "ES"}
              </span>
            </button>
          </div>
        </div>
        {error && (
          <p className="mb-3 rounded-md bg-red-50 p-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-400">
            {error}
          </p>
        )}
        {!error && avisoExpirada && (
          <p
            role="status"
            className="mb-3 rounded-md bg-amber-50 p-2 text-sm text-amber-800 dark:bg-amber-950/40 dark:text-amber-300"
          >
            {avisoExpirada}
          </p>
        )}
        <div className="flex w-full flex-col gap-5">
          <Input
            label={t("auth.usuario")}
            icono={<User className="h-4 w-4 text-muted" />}
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            required
            className="w-full"
          />
          <Input
            label={t("auth.contrasena")}
            type="password"
            icono={<Lock className="h-4 w-4 text-muted" />}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
            className="w-full"
          />
          <Button
            type="submit"
            size="lg"
            className="w-full"
            disabled={cargando}
          >
            {cargando ? t("auth.ingresando") : t("auth.ingresar")}
          </Button>
          <div className="flex items-center gap-3 text-xs text-muted">
            <span className="h-px flex-1 bg-line" aria-hidden />
            {t("auth.oSegunda")}
            <span className="h-px flex-1 bg-line" aria-hidden />
          </div>
          <button
            type="button"
            onClick={entrarConGoogle}
            disabled={googleCargando || cargando}
            className="flex w-full items-center justify-center gap-2 rounded-lg border border-line bg-surface px-4 py-2.5 text-sm font-semibold text-ink shadow-sm transition hover:bg-warmbg disabled:opacity-60"
          >
            <svg className="h-4 w-4" viewBox="0 0 24 24" aria-hidden>
              <path
                fill="#4285F4"
                d="M23.5 12.3c0-.9-.1-1.5-.3-2.3H12v4.3h6.5c-.1 1.1-.8 2.7-2.4 3.8l-.1.1 3.5 2.7.2.1c2.2-2 3.8-5.1 3.8-8.7z"
              />
              <path
                fill="#34A853"
                d="M12 24c3.2 0 5.9-1.1 7.9-2.9l-3.8-2.9c-1 .7-2.4 1.2-4.1 1.2-3.1 0-5.8-2.1-6.8-5l-.1.1-3.6 2.8-.1.1C3.5 21.3 7.5 24 12 24z"
              />
              <path
                fill="#FBBC05"
                d="M5.2 14.4c-.2-.7-.4-1.5-.4-2.4s.1-1.7.4-2.4l-.1-.1-3.6-2.8v.1C.5 8.9 0 10.4 0 12s.5 3.1 1.5 4.5l3.7-2.1z"
              />
              <path
                fill="#EA4335"
                d="M12 4.6c1.8 0 3 .8 3.7 1.4l3.3-3.2C17.9 1.1 15.2 0 12 0 7.5 0 3.5 2.7 1.5 6.8l3.7 2.8c1-2.9 3.7-5 6.8-5z"
              />
            </svg>
            {googleCargando ? t("auth.googleIniciando") : t("auth.google")}
          </button>
        </div>
      </form>
    </div>
  );
}
