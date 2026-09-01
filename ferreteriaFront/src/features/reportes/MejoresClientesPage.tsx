import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { rangoFechas, type RangoFechas } from "@/lib/rango";
import { formatoFecha, formatoMoneda, formatoNumero } from "@/lib/format";
import { apiMejoresClientes } from "@/lib/api/reportes";
import { esApiError } from "@/lib/api/client";
import { Card } from "@/components/ui/Card";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { ReporteHeader } from "./ReporteHeader";
import type { MejorCliente } from "@/lib/api/types";
import CardListReportes from "./CardListReportes";

export default function MejoresClientesPage() {
  useDocumentTitle("Mejores clientes");
  const { error: mostrarError } = useToast();
  const [rango, setRango] = useState<RangoFechas>(() => rangoFechas());

  const { data, isLoading, error } = useQuery({
    queryKey: ["mejores-clientes", rango.inicio, rango.fin],
    queryFn: () => apiMejoresClientes(rango.inicio, rango.fin),
  });

  useEffect(() => {
    if (error)
      mostrarError(
        esApiError(error) ? error.mensajeParaUsuario() : String(error),
      );
  }, [error, mostrarError]);

  const columnas: Columna<MejorCliente>[] = [
    {
      key: "r",
      header: "Ranking",
      render: (v) => <span className="font-medium">{v.rankingMes}°</span>,
    },
    { key: "n", header: "Cliente", render: (v) => v.cliente },
    {
      key: "c",
      header: "Compras",
      align: "right",
      render: (v) => formatoNumero(v.numCompras),
    },
    {
      key: "t",
      header: "Total comprado",
      align: "right",
      render: (v) => formatoMoneda(v.totalComprado),
    },
    {
      key: "p",
      header: "Ticket promedio",
      align: "right",
      render: (v) => formatoMoneda(v.ticketPromedio),
    },
    {
      key: "h",
      header: "Ranking histórico",
      align: "right",
      render: (v) => v.rankingHistorico,
    },
  ];

  return (
    <div className="space-y-4">
      <ReporteHeader
        titulo="Mejores clientes"
        subtitulo={`Periodo: ${formatoFecha(rango.inicio)} – ${formatoFecha(rango.fin)}.`}
        rango={rango}
        onChange={setRango}
      />
      {isLoading && <Spinner />}
      {data && data.length > 0 && (
        <Card titulo="Ranking del periodo">
          <DataTable
            columnas={columnas}
            items={data}
            rowKey={(v) => v.clienteId}
            caption="Mejores clientes"
          />
        </Card>
      )}
      {data && data.length === 0 && !isLoading && (
        <EmptyState
          title="Sin clientes en el periodo"
          descripcion="Cambia el rango de fechas."
        />
      )}
      <CardListReportes />
    </div>
  );
}
