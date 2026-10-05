import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { KeyRound, Mail, MessageCircle } from "lucide-react";

import { ensureCsrfCookie, mensajeError } from "@/lib/api/client";
import { apiSolicitarOtp, apiVerificarOtp } from "@/lib/api/endpoints";
import type { OtpCanal } from "@/lib/api/types";
import { useAuthStore } from "@/store/auth";
import { useT } from "@/i18n";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";

const REENVIO_ESPERA = 60;

export default function Otp() {
  const t = useT();
  useDocumentTitle(t("auth.otp.titulo"));
  const navigate = useNavigate();
  const location = useLocation();
  const challenge = useAuthStore((s) => s.challenge);
  const setChallenge = useAuthStore((s) => s.setChallenge);
  const setSession = useAuthStore((s) => s.setSession);

  const registro = location.state as { from?: string } | null;
  const destinoCrudo = registro?.from ?? "/dashboard";
  const destino =
    destinoCrudo.startsWith("/") && !destinoCrudo.startsWith("//")
      ? destinoCrudo
      : "/dashboard";

  const [canalElegido, setCanalElegido] = useState<OtpCanal | null>(null);
  const [codigo, setCodigo] = useState("");
  const [enviado, setEnviado] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [verificando, setVerificando] = useState(false);
  const [espera, setEspera] = useState(0);

  useEffect(() => {
    if (espera <= 0) return;
    const id = window.setTimeout(() => setEspera((s) => s - 1), 1000);
    return () => window.clearTimeout(id);
  }, [espera]);

  if (!challenge) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-linear-to-br from-orange-700 via-primary to-orange-900 p-4">
        <div className="w-full max-w-sm rounded-xl border border-white/20 bg-surface p-6 text-center shadow-2xl">
          <p className="mb-4 text-sm text-muted">{t("auth.otp.sinDesafio")}</p>
          <Link to="/login" className="text-sm font-semibold text-primary hover:underline">
            {t("auth.otp.volverLogin")}
          </Link>
        </div>
      </div>
    );
  }

  // Canal efectivo: elección del usuario o el primero disponible.
  const canal = canalElegido ?? challenge.canales[0] ?? null;
  const destinoEnmascarado =
    canal === "email" ? challenge.emailEnmascarado : challenge.whatsappEnmascarado;

  const enviar = async (e?: React.SubmitEvent<HTMLFormElement>) => {
    e?.preventDefault();
    if (!canal) return;
    setError(null);
    setEnviando(true);
    try {
      await ensureCsrfCookie();
      await apiSolicitarOtp({ challengeId: challenge.challengeId, canal });
      setCanalElegido(canal);
      setEnviado(true);
      setEspera(REENVIO_ESPERA);
    } catch (err) {
      setError(mensajeError(err));
    } finally {
      setEnviando(false);
    }
  };

  const verificar = async (e: React.SubmitEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError(null);
    setVerificando(true);
    try {
      await ensureCsrfCookie();
      const token = await apiVerificarOtp({
        challengeId: challenge.challengeId,
        codigo: codigo.trim(),
      });
      setChallenge(null);
      setSession(token);
      navigate(destino, { replace: true });
    } catch (err) {
      setError(mensajeError(err));
    } finally {
      setVerificando(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-linear-to-br from-orange-700 via-primary to-orange-900 p-4">
      <div className="w-full max-w-sm rounded-xl border border-white/20 bg-surface p-6 shadow-2xl">
        <h1 className="text-center text-lg font-bold text-ink">{t("auth.otp.titulo")}</h1>
        <p className="mb-4 text-center text-sm text-muted">{t("auth.otp.subtitulo")}</p>
        {error && (
          <p className="mb-3 rounded-md bg-red-50 p-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-400">
            {error}
          </p>
        )}
        {!enviado ? (
          <form onSubmit={enviar} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2" role="radiogroup" aria-label={t("auth.otp.titulo")}>
              {challenge.canales.includes("email") && (
                <button
                  type="button"
                  role="radio"
                  aria-checked={canal === "email"}
                  onClick={() => setCanalElegido("email")}
                  className={`flex items-center gap-3 rounded-lg border px-4 py-3 text-left text-sm font-semibold transition ${
                    canal === "email"
                      ? "border-primary bg-orange-50 text-ink dark:bg-orange-950/30"
                      : "border-line text-muted hover:bg-warmbg"
                  }`}
                >
                  <Mail className="h-4 w-4 shrink-0" />
                  <span>
                    {t("auth.otp.canalEmail")}
                    {challenge.emailEnmascarado && (
                      <span className="block text-xs font-normal">{challenge.emailEnmascarado}</span>
                    )}
                  </span>
                </button>
              )}
              {challenge.canales.includes("whatsapp") && (
                <button
                  type="button"
                  role="radio"
                  aria-checked={canal === "whatsapp"}
                  onClick={() => setCanalElegido("whatsapp")}
                  className={`flex items-center gap-3 rounded-lg border px-4 py-3 text-left text-sm font-semibold transition ${
                    canal === "whatsapp"
                      ? "border-primary bg-orange-50 text-ink dark:bg-orange-950/30"
                      : "border-line text-muted hover:bg-warmbg"
                  }`}
                >
                  <MessageCircle className="h-4 w-4 shrink-0" />
                  <span>
                    {t("auth.otp.canalWhatsapp")}
                    {challenge.whatsappEnmascarado && (
                      <span className="block text-xs font-normal">
                        {challenge.whatsappEnmascarado}
                      </span>
                    )}
                  </span>
                </button>
              )}
            </div>
            <Button type="submit" size="lg" className="w-full" disabled={enviando || !canal}>
              {enviando ? t("auth.otp.enviando") : t("auth.otp.enviar")}
            </Button>
          </form>
        ) : (
          <form onSubmit={verificar} className="flex flex-col gap-4">
            {destinoEnmascarado && (
              <p className="text-center text-sm text-muted">
                {t("auth.otp.enviadoA", { destino: destinoEnmascarado })}
              </p>
            )}
            <Input
              label={t("auth.otp.codigo")}
              icono={<KeyRound className="h-4 w-4 text-muted" />}
              value={codigo}
              onChange={(e) => setCodigo(e.target.value.replace(/\D/g, "").slice(0, 6))}
              inputMode="numeric"
              autoComplete="one-time-code"
              required
              minLength={6}
              maxLength={6}
              className="w-full text-center text-xl tracking-[0.5em]"
            />
            <Button
              type="submit"
              size="lg"
              className="w-full"
              disabled={verificando || codigo.length !== 6}
            >
              {verificando ? t("auth.otp.verificando") : t("auth.otp.verificar")}
            </Button>
            <button
              type="button"
              onClick={() => void enviar()}
              disabled={enviando || espera > 0}
              className="text-center text-sm font-semibold text-primary hover:underline disabled:opacity-50"
            >
              {espera > 0 ? `${t("auth.otp.reenviar")} (${espera})` : t("auth.otp.reenviar")}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
