import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  AlertTriangle,
  Banknote,
  Boxes,
  ClipboardList,
  ReceiptText,
  Send,
  ShoppingBag,
  Store,
  TrendingUp,
  Undo2,
} from "lucide-react";
import { Link } from "react-router-dom";

import type { ReactNode } from "react";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { rangoFechas, type RangoFechas } from "@/lib/rango";
import { formatoFechaHora, formatoMoneda } from "@/lib/format";
import { apiDashboard, apiEnviarInforme, apiInformeEstado } from "@/lib/api/reportes";
import { esApiError } from "@/lib/api/client";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { Spinner } from "@/components/ui/Spinner";
import { useTieneRol } from "@/store/auth";
import type { InformeEstado } from "@/lib/api/types";

import { useToast } from "@/components/ui/Toast";

function KPI({
  icono,
  label,
  valor,
  alerta,
}: {
  icono: ReactNode;
  label: string;
  valor: string;
  alerta?: "warn" | "danger";
}) {
  return (
    <Card className="p-5">
      <div className="flex items-center gap-3">
        <span
          className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${
            alerta === "danger"
              ? "bg-red-100 text-red-600"
              : alerta === "warn"
                ? "bg-amber-100 text-amber-600"
                : "bg-orange-100 text-primary"
          }`}
          aria-hidden
        >
          {icono}
        </span>
        <div className="min-w-0">
          <p className="text-xs uppercase tracking-wide text-muted">{label}</p>
          <p className="truncate text-lg font-bold tabular-nums text-ink">
            {valor}
          </p>
        </div>
      </div>
    </Card>
  );
}

export default function DashboardPage() {
  useDocumentTitle("Inicio");
  const { success: mostrarExito, error: mostrarError, loading: mostrarCarga } = useToast();
  const [rango] = useState<RangoFechas>(() => rangoFechas());
  const puedeEnviar = useTieneRol(["ADMINISTRADOR", "GERENTE"]);
  const [enviando, setEnviando] = useState(false);
  const [confirmarReenvio, setConfirmarReenvio] = useState<InformeEstado | null>(null);

  const { data, isLoading, error } = useQuery({
    queryKey: ["dashboard", rango.inicio, rango.fin],
    queryFn: () => apiDashboard(rango.inicio, rango.fin),
  });

  useEffect(() => {
    if (error)
      mostrarError(
        esApiError(error) ? error.mensajeParaUsuario() : String(error),
      );
  }, [error, mostrarError]);

  async function enviarInforme() {
    if (enviando) return;
    const cerrarCarga = mostrarCarga("Verificando informe…");
    try {
      const estado = await apiInformeEstado(rango.inicio, rango.fin);
      if (estado.yaEnviado) {
        setConfirmarReenvio(estado);
        return;
      }
      await ejecutarEnvio();
    } catch (e) {
      mostrarError(esApiError(e) ? e.mensajeParaUsuario() : String(e));
    } finally {
      cerrarCarga();
    }
  }

  async function ejecutarEnvio() {
    if (enviando) return;
    setEnviando(true);
    const cerrarCarga = mostrarCarga("Enviando informe…");
    try {
      const r = await apiEnviarInforme(rango.inicio, rango.fin);
      setConfirmarReenvio(null);
      mostrarExito(
        `Informe enviado a ${r.destinatarios} destinatarios (${r.emailsEnviados} correos, ${r.whatsappEnviados} WhatsApp).`,
      );
    } catch (e) {
      mostrarError(esApiError(e) ? e.mensajeParaUsuario() : String(e));
    } finally {
      cerrarCarga();
      setEnviando(false);
    }
  }

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-ink">Panel de control</h1>
          <p className="text-sm text-muted">
            Resumen del periodo seleccionado.
          </p>
        </div>
        {puedeEnviar && (
          <Button
            variant="primary"
            size="sm"
            disabled={enviando}
            onClick={enviarInforme}
          >
            <Send className="h-4 w-4" aria-hidden />
            {enviando ? "Enviando…" : "Enviar informe"}
          </Button>
        )}
      </header>

      {isLoading && <Spinner label="Cargando indicadores…" />}

      {data && (
        <>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <KPI
              icono={<TrendingUp className="h-5 w-5" />}
              label="Ventas en rango"
              valor={formatoMoneda(data.ventasEnRango)}
            />
            <KPI
              icono={<ReceiptText className="h-5 w-5" />}
              label="Tickets"
              valor={String(data.ticketsEnRango)}
            />
            <KPI
              icono={<Undo2 className="h-5 w-5" />}
              label="Devoluciones"
              valor={`${data.devolucionesEnRango} · ${formatoMoneda(data.totalDevueltoEnRango)}`}
              alerta={data.devolucionesEnRango > 0 ? "warn" : undefined}
            />
            <KPI
              icono={<ShoppingBag className="h-5 w-5" />}
              label="Ticket promedio"
              valor={formatoMoneda(data.ticketPromedioEnRango)}
            />
            <KPI
              icono={<Banknote className="h-5 w-5" />}
              label="Saldo por cobrar"
              valor={formatoMoneda(data.saldoPorCobrar)}
              alerta={data.saldoPorCobrar > 0 ? "warn" : undefined}
            />
            <KPI
              icono={<AlertTriangle className="h-5 w-5" />}
              label="Cobranza vencida"
              valor={formatoMoneda(data.cobranzaVencida)}
              alerta={data.cobranzaVencida > 0 ? "danger" : undefined}
            />
            <KPI
              icono={<Boxes className="h-5 w-5" />}
              label="Valor de inventario"
              valor={formatoMoneda(data.valorInventario)}
            />
            {data.productosAgotados > 0 ? (
              <Link to="/inventario/stock?soloBajoStock=1" className="block">
                <KPI
                  icono={<ClipboardList className="h-5 w-5" />}
                  label="Productos agotados"
                  valor={String(data.productosAgotados)}
                  alerta="danger"
                />
              </Link>
            ) : (
              <KPI
                icono={<ClipboardList className="h-5 w-5" />}
                label="Productos agotados"
                valor="0"
              />
            )}
            <KPI
              icono={<TrendingUp className="h-5 w-5" />}
              label="Promociones activas"
              valor={String(data.promocionesActivas)}
            />
            <KPI
              icono={<Store className="h-5 w-5" />}
              label="Cajas abiertas"
              valor={String(data.cajasAbiertas)}
            />
          </div>

          <Card
            titulo={`Periodo seleccionado`}
            actions={
              <Link
                to="/reportes"
                className="text-sm text-primary hover:underline"
              >
                Ver reportes →
              </Link>
            }
          >
            <p className="text-sm text-muted">
              Consulta los reportes detallados (ventas por hora, días de mayor
              venta, productos y clientes) desde la sección Reportes, con el
              mismo rango de fechas.
            </p>
          </Card>
        </>
      )}

      <ConfirmDialog
        open={confirmarReenvio !== null}
        title="Informe ya enviado"
        confirmLabel="Sí, reenviar"
        tone="primary"
        busy={enviando}
        onCancel={() => !enviando && setConfirmarReenvio(null)}
        onConfirm={ejecutarEnvio}
      >
        <p className="text-sm text-ink">
          El informe del periodo seleccionado ya se envió
          {confirmarReenvio?.enviadoEn
            ? ` el ${formatoFechaHora(confirmarReenvio.enviadoEn)}`
            : ""}
          . ¿Desea enviarlo nuevamente por correo y WhatsApp?
        </p>
      </ConfirmDialog>
    </div>
  );
}
