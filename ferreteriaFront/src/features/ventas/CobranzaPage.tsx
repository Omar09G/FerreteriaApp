import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ReceiptText, Wallet } from "lucide-react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { esApiError } from "@/lib/api/client";
import { apiClientes } from "@/lib/api/catalogo";
import { apiCuentasCobrar, apiPagoCliente } from "@/lib/api/venta";
import type { CuentaCobrar, PagoClienteRequest } from "@/lib/api/types";
import { FORMAS_PAGO } from "@/lib/api/types";
import { formatoFecha, formatoMoneda } from "@/lib/format";
import type { RangoFechas } from "@/lib/rango";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { Dialog } from "@/components/ui/Dialog";
import { Input, Select } from "@/components/ui/Input";
import { Pagination } from "@/components/ui/Pagination";
import { RangoFiltro } from "@/components/ui/RangoFiltro";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";

const diasVencido = (vencimiento: string) => {
  const hoy = new Date();
  hoy.setHours(0, 0, 0, 0);
  const vto = new Date(`${vencimiento}T00:00:00`);
  return Math.round((hoy.getTime() - vto.getTime()) / 86_400_000);
};

const validaDeuda = (montoTotal: number, montoPagado: number): boolean => {
  return montoTotal !== montoPagado;
};

function AbonoForm({
  cuenta,
  guardando,
  onGuardar,
  onClose,
}: {
  cuenta: CuentaCobrar;
  guardando: boolean;
  onGuardar: (body: PagoClienteRequest) => void;
  onClose: () => void;
}) {
  const [formaPagoId, setFormaPagoId] = useState(1);
  const [monto, setMonto] = useState(String(cuenta.saldo));
  const [referencia, setReferencia] = useState("");
  const [intento, setIntento] = useState(false);

  const forma = FORMAS_PAGO.find((f) => f.id === formaPagoId);
  const montoNum = Number(monto);
  const invalido =
    montoNum <= 0 ||
    montoNum > cuenta.saldo ||
    (forma?.requiereReferencia && referencia.trim() === "");

  const enviar = (e: { preventDefault: () => void }) => {
    e.preventDefault();
    setIntento(true);
    if (invalido) return;
    onGuardar({
      cuentaCobrarId: cuenta.cuentaCobrarId,
      formaPagoId,
      monto: montoNum,
      referencia: forma?.requiereReferencia ? referencia.trim() : undefined,
    });
  };

  return (
    <form onSubmit={enviar} className="space-y-3" noValidate>
      <div className="rounded-md bg-canvas px-3 py-2 text-sm">
        <p className="font-medium text-ink">
          {cuenta.ventaFolio} · {cuenta.clienteNombre}
        </p>
        <p className="text-muted">
          Saldo pendiente:{" "}
          <span className="font-semibold tabular-nums text-ink">
            {formatoMoneda(cuenta.saldo)}
          </span>{" "}
          · vence {formatoFecha(cuenta.fechaVencimiento)}
        </p>
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Select
          label="Forma de pago"
          required
          value={formaPagoId}
          onChange={(e) => setFormaPagoId(Number(e.target.value))}
        >
          {FORMAS_PAGO.filter((f) => f.id !== 6).map((f) => (
            <option key={f.id} value={f.id}>
              {f.nombre}
            </option>
          ))}
        </Select>
        <Input
          label="Monto del abono"
          type="number"
          inputMode="decimal"
          min="0.01"
          step="0.01"
          max={cuenta.saldo}
          required
          value={monto}
          onChange={(e) => setMonto(e.target.value)}
        />
      </div>
      {forma?.requiereReferencia && (
        <Input
          label="Referencia del pago"
          required
          value={referencia}
          onChange={(e) => setReferencia(e.target.value)}
          placeholder="Folio, autorización…"
        />
      )}
      <div className="flex items-center justify-between">
        <p className="text-xs text-muted">
          El abono se aplica al saldo de la cuenta. Al cubrirse el total queda{" "}
          <Badge tone="success">LIQUIDADA</Badge>.
        </p>
        <div className="flex gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" disabled={guardando}>
            {guardando ? "Registrando…" : "Registrar abono"}
          </Button>
        </div>
      </div>
      {intento && invalido && (
        <p className="text-xs text-red-600">
          El monto debe ser mayor a 0 y no superar el saldo.
        </p>
      )}
    </form>
  );
}

function EstadoCuenta({ cuenta }: { cuenta: CuentaCobrar }) {
  return (
    <div className="space-y-1.5">
      {cuenta.pagos.length === 0 ? (
        <p className="text-sm text-muted">Sin abonos registrados.</p>
      ) : (
        cuenta.pagos.map((p) => (
          <div
            key={p.pagoClienteId}
            className="flex items-center justify-between rounded-md border border-line px-3 py-1.5 text-sm"
          >
            <span>
              <span className="font-medium text-ink">
                {formatoMoneda(p.monto)}
              </span>
              <span className="ml-2 text-xs text-muted">
                {FORMAS_PAGO.find((f) => f.id === p.formaPagoId)?.nombre ??
                  `Forma ${p.formaPagoId}`}
              </span>
            </span>
            <span className="text-xs tabular-nums text-muted">
              {formatoFecha(p.fecha)}
            </span>
          </div>
        ))
      )}
    </div>
  );
}

export default function CobranzaPage() {
  useDocumentTitle("Cobranza");
  const { error: mostrarError, success: mostrarExito } = useToast();
  const queryClient = useQueryClient();

  const [estado, setEstado] = useState<string>("");
  const [clienteId, setClienteId] = useState<number | "">("");
  const [rango, setRango] = useState<RangoFechas | null>(null);
  const [page, setPage] = useState(0);
  const [abonando, setAbonando] = useState<CuentaCobrar | null>(null);
  const [historial, setHistorial] = useState<CuentaCobrar | null>(null);

  const clientes = useQuery({
    queryKey: ["clientes-cobranza"],
    queryFn: () => apiClientes({ page: 0, size: 50 }),
  });

  const { data, isLoading, error, isFetching } = useQuery({
    queryKey: [
      "cuentas-cobrar",
      estado,
      clienteId,
      rango?.inicio,
      rango?.fin,
      page,
    ],
    queryFn: () =>
      apiCuentasCobrar({
        estado: estado || undefined,
        clienteId: clienteId || undefined,
        desde: rango?.inicio,
        hasta: rango?.fin,
        page,
        size: 15,
      }),
  });

  useEffect(() => {
    if (error)
      mostrarError(
        esApiError(error) ? error.mensajeParaUsuario() : String(error),
      );
  }, [error, mostrarError]);

  const abono = useMutation({
    mutationFn: (body: PagoClienteRequest) => apiPagoCliente(body),
    onSuccess: () => {
      mostrarExito("Abono aplicado a la cuenta.");
      setAbonando(null);
      queryClient.invalidateQueries({ queryKey: ["cuentas-cobrar"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    },
    onError: (err) =>
      mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  });

  const columnas: Columna<CuentaCobrar>[] = [
    {
      key: "f",
      header: "Venta",
      render: (v) => (
        <span className="font-medium text-ink">{v.ventaFolio}</span>
      ),
    },
    { key: "c", header: "Cliente", render: (v) => v.clienteNombre },
    {
      key: "tot",
      header: "Total",
      align: "right",
      render: (v) => (
        <Badge
          tone={validaDeuda(v.montoTotal, v.montoPagado) ? "info" : "default"}
        >
          <span className="tabular-nums">{formatoMoneda(v.montoTotal)}</span>
        </Badge>
      ),
    },
    {
      key: "pag",
      header: "Pagado",
      align: "right",
      render: (v) => (
        <Badge
          tone={
            validaDeuda(v.montoTotal, v.montoPagado) ? "warning" : "default"
          }
        >
          <span className="tabular-nums text-muted">
            {formatoMoneda(v.montoPagado)}
          </span>
        </Badge>
      ),
    },
    {
      key: "sal",
      header: "Saldo",
      align: "right",
      render: (v) => {
        return v.saldo > 0 ? (
          <Badge tone="danger">
            <span className="font-medium tabular-nums">
              {formatoMoneda(v.saldo)}
            </span>
          </Badge>
        ) : (
          <span className="font-medium tabular-nums">
            {formatoMoneda(v.saldo)}
          </span>
        );
      },
    },
    {
      key: "vto",
      header: "Vencimiento",
      render: (v) => {
        const dias = diasVencido(v.fechaVencimiento);
        const isDeuda = validaDeuda(v.montoTotal, v.montoPagado);

        return dias > 0 && isDeuda ? (
          <span className="flex items-center gap-1.5 whitespace-nowrap">
            <Badge tone="danger">Vencida {dias}d</Badge>
            <span className="text-xs tabular-nums text-muted">
              {formatoFecha(v.fechaVencimiento)}
            </span>
          </span>
        ) : (
          <span className="tabular-nums">
            {formatoFecha(v.fechaVencimiento)}
          </span>
        );
      },
    },
    {
      key: "est",
      header: "Estado",
      render: (v) => {
        const d = diasVencido(v.fechaVencimiento);
        const isDeuda = validaDeuda(v.montoTotal, v.montoPagado);
        return v.estado === "PARCIAL" ? (
          <Badge tone="warning">Parcial</Badge>
        ) : d > 0 && isDeuda ? (
          <Badge tone="danger">Vigente vencida</Badge>
        ) : (
          <Badge tone="info">{v.estado}</Badge>
        );
      },
    },
    {
      key: "acc",
      header: "Acciones",
      align: "right",
      render: (v) => (
        <div className="flex justify-end gap-1">
          <button
            type="button"
            aria-label="Estado de cuenta"
            title="Estado de cuenta"
            className="rounded p-1.5 text-muted hover:bg-primary-50 hover:text-primary"
            onClick={() => setHistorial(v)}
          >
            <ReceiptText className="h-4 w-4" />
          </button>
          <button
            type="button"
            disabled={v.saldo <= 0}
            aria-label="Registrar abono"
            title="Registrar abono"
            className="rounded p-1.5 text-primary hover:bg-primary-50 disabled:text-muted"
            onClick={() => setAbonando(v)}
          >
            <Wallet className="h-4 w-4" />
          </button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-ink">Cobranza</h1>
          <p className="text-sm text-muted">
            Cuentas por cobrar de ventas a crédito interna. Registra abonos y
            vigila vencimientos.
          </p>
        </div>
        <RangoFiltro
          valor={rango}
          onChange={(siguiente) => {
            setRango(siguiente);
            setPage(0);
          }}
        />
      </header>

      <Card>
        <div className="flex flex-wrap items-end gap-2">
          <Select
            label="Estado"
            value={estado}
            onChange={(e) => {
              setEstado(e.target.value);
              setPage(0);
            }}
            className="w-48"
          >
            <option value="">Todos</option>
            <option value="VIGENTE">Vigentes</option>
            <option value="PARCIAL">Parciales</option>
          </Select>
          <Select
            label="Cliente"
            value={clienteId}
            onChange={(e) => {
              setClienteId(e.target.value ? Number(e.target.value) : "");
              setPage(0);
            }}
            className="w-72"
          >
            <option value="">Todos</option>
            {clientes.data?.data.map((c) => (
              <option key={c.clienteId} value={c.clienteId}>
                {c.razonSocial}
              </option>
            ))}
          </Select>
          {(estado !== "" || clienteId !== "") && (
            <Button
              variant="ghost"
              onClick={() => {
                setEstado("");
                setClienteId("");
                setPage(0);
              }}
            >
              Limpiar
            </Button>
          )}
        </div>
      </Card>

      {(isLoading || (isFetching && !data)) && <Spinner />}
      {data && (
        <Card titulo={`Cuentas por cobrar (${data.meta.totalElements})`}>
          <DataTable
            columnas={columnas}
            items={data.data}
            rowKey={(v) => v.cuentaCobrarId}
            loading={isFetching}
          />
          <Pagination meta={data.meta} onPage={setPage} />
        </Card>
      )}

      <Dialog
        open={abonando !== null}
        onClose={() => !abono.isPending && setAbonando(null)}
        title="Registrar abono"
        width="max-w-lg"
      >
        {abonando && (
          <AbonoForm
            cuenta={abonando}
            guardando={abono.isPending}
            onGuardar={(body) => abono.mutate(body)}
            onClose={() => setAbonando(null)}
          />
        )}
      </Dialog>

      <Dialog
        open={historial !== null}
        onClose={() => setHistorial(null)}
        title={historial ? `Estado de cuenta · ${historial.clienteNombre}` : ""}
        width="max-w-lg"
      >
        {historial && (
          <div className="space-y-3">
            <div className="grid grid-cols-3 gap-2 text-center">
              <div className="rounded-md bg-canvas py-2">
                <p className="text-xs text-muted">Total</p>
                <p className="font-semibold tabular-nums">
                  {formatoMoneda(historial.montoTotal)}
                </p>
              </div>
              <div className="rounded-md bg-canvas py-2">
                <p className="text-xs text-muted">Pagado</p>
                <p className="font-semibold tabular-nums text-green-700">
                  {formatoMoneda(historial.montoPagado)}
                </p>
              </div>
              <div className="rounded-md bg-canvas py-2">
                <p className="text-xs text-muted">Saldo</p>
                <p className="font-semibold tabular-nums text-red-700">
                  {formatoMoneda(historial.saldo)}
                </p>
              </div>
            </div>
            <EstadoCuenta cuenta={historial} />
            {historial.saldo > 0 && (
              <div className="flex justify-end">
                <Button
                  onClick={() => {
                    setAbonando(historial);
                    setHistorial(null);
                  }}
                >
                  <Wallet className="h-4 w-4" /> Registrar abono
                </Button>
              </div>
            )}
          </div>
        )}
      </Dialog>
    </div>
  );
}
