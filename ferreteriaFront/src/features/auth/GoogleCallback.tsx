import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";

import http, { mensajeError } from "@/lib/api/client";
import type { Envelope, OtpChallenge } from "@/lib/api/types";
import { useAuthStore } from "@/store/auth";
import { useT } from "@/i18n";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";

/**
 * Retorno del login con Google: el backend redirige aquí con
 * ?challengeId= (o ?error=). Recuperamos el desafío y seguimos al OTP.
 */
export default function GoogleCallback() {
  const t = useT();
  useDocumentTitle(t("auth.otp.titulo"));
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const setChallenge = useAuthStore((s) => s.setChallenge);
  const [error, setError] = useState<string | null>(() => {
    const codigo = params.get("error");
    return codigo ? t("auth.otp.errorGoogle") : null;
  });

  useEffect(() => {
    const challengeId = params.get("challengeId");
    if (!challengeId) return;
    let vivo = true;
    http
      .get<Envelope<OtpChallenge>>("/auth/otp/desafio", { params: { challengeId } })
      .then(({ data }) => {
        if (!vivo) return;
        setChallenge(data.data);
        navigate("/auth/otp", { replace: true });
      })
      .catch((err: unknown) => {
        if (!vivo) return;
        setError(mensajeError(err));
      });
    return () => {
      vivo = false;
    };
  }, [params, setChallenge, navigate]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-linear-to-br from-orange-700 via-primary to-orange-900 p-4 dark:from-stone-950 dark:via-sidebar dark:to-stone-950">
      <div className="w-full max-w-sm rounded-xl border border-white/20 bg-surface p-6 text-center shadow-2xl">
        {error ? (
          <>
            <p className="mb-4 rounded-md bg-red-50 p-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-400">
              {error}
            </p>
            <Link to="/login" className="text-sm font-semibold text-primary hover:underline">
              {t("auth.otp.volverLogin")}
            </Link>
          </>
        ) : (
          <p className="text-sm text-muted">{t("auth.googleIniciando")}</p>
        )}
      </div>
    </div>
  );
}
