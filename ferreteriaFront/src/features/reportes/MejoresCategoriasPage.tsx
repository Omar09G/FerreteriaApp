import { useEffect, useState } from "react";
import CardListReportes from "./CardListReportes";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { useToast } from "@/components/ui/Toast";
import { useQuery } from "@tanstack/react-query";
import { apiMejoresCategorias } from "@/lib/api/reportes";
import { esApiError } from "@/lib/api/client";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { Card } from "@/components/ui/Card";
import { Spinner } from "@/components/ui/Spinner";
import type { MejoresCategorias } from "@/lib/api/types";
import { formatoMoneda, formatoNumero, formatoFecha } from "@/lib/format";
import { Badge } from "@/components/ui/Badge";
import { ReporteHeader } from "./ReporteHeader";
import { rangoFechas, type RangoFechas } from "@/lib/rango";

export default function MejoresCategoriasPage() {
  useDocumentTitle("Mejores categorías");
  const { error: mostrarError } = useToast();
  const [rango, setRango] = useState<RangoFechas>(() => rangoFechas());

  const { data, isLoading, error } = useQuery({
    queryKey: ["mejores-categorias"],
    queryFn: () => apiMejoresCategorias(),
  });

  useEffect(() => {
    if (error)
      mostrarError(
        esApiError(error) ? error.mensajeParaUsuario() : String(error),
      );
  }, [error, mostrarError]);

  const columnas: Columna<MejoresCategorias>[] = [
    {
      key: "rankingMes",
      header: "Ranking del mes",
      render: (v) => formatoNumero(v.rankingMes),
    },
    { key: "mes", header: "Mes", render: (v) => formatoFecha(v.mes) },
    { key: "categoria", header: "Categoría", render: (v) => v.categoria },
    {
      key: "unidadesVendidas",
      header: "Unidades vendidas",
      align: "right",
      render: (v) => (
        <Badge tone="danger">{formatoNumero(v.unidadesVendidas)}</Badge>
      ),
    },
    {
      key: "ingreso",
      header: "Ingreso total",
      align: "right",
      render: (v) => formatoMoneda(v.ingreso),
    },
    {
      key: "utilidad",
      header: "Utilidad total",
      align: "right",
      render: (v) =>
        //Case utilizada > 500 ? Badge verde, entre 100 y 500 ? Badge amarillo, < 100 ? Badge rojo
        v.utilidad > 499 ? (
          <Badge tone="success">{formatoMoneda(v.utilidad)}</Badge>
        ) : v.utilidad >= 100 && v.utilidad <= 499 ? (
          <Badge tone="info">{formatoMoneda(v.utilidad)}</Badge>
        ) : (
          <Badge tone="warning">{formatoMoneda(v.utilidad)}</Badge>
        ),
    },
  ];

  return (
    <div className="space-y-4">
      <ReporteHeader
        titulo="Productos sin Movimiento"
        subtitulo="Cuadratura de cortes por día en el periodo."
        rango={rango}
        onChange={setRango}
      />
      {isLoading && <Spinner />}
      {data && data.length > 0 && (
        <Card titulo="Ranking de mejor categoría vendida por mes">
          <DataTable
            columnas={columnas}
            items={data}
            rowKey={(v) => v.rankingMes}
            caption="Mejores categorías"
          />
        </Card>
      )}
      <CardListReportes />
    </div>
  );
}
