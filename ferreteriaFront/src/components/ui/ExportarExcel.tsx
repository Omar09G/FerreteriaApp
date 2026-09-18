import { useState } from "react";
import { Download } from "lucide-react";

import { Button } from "./Button";
import type { Columna } from "./DataTable";
import { getFechaActualLocal } from "@/lib/format";


/**
 * Botón reutilizable para exportar a Excel lo visible en un DataTable.
 * Reutiliza las mismas `columnas` e `items`: solo las columnas con
 * accesorio `exportar` salen en el archivo (texto mostrado, no ReactNode).
 */
export function ExportarExcel<T>({
  columnas,
  items,
  archivo,
}: {
  columnas: Columna<T>[];
  items: T[] | undefined;
  archivo: string;
}) {
  const [generando, setGenerando] = useState(false);
  const exportables = columnas.filter((c) => c.exportar !== undefined);
  const deshabilitado = generando || !items || items.length === 0 || exportables.length === 0;

  const exportar = async () => {
    if (!items || items.length === 0) return;
    setGenerando(true);
    try {
      const XLSX = await import("xlsx");
      const encabezados = exportables.map((c) =>
        typeof c.header === "string" ? c.header : (c.tituloExportar ?? c.key),
      );
      const filas = items.map((item) =>
        exportables.map((c) => c.exportar!(item) ?? ""),
      );
      const libro = XLSX.utils.book_new();
      const hoja = XLSX.utils.aoa_to_sheet([encabezados, ...filas]);
      XLSX.utils.book_append_sheet(libro, hoja, "datos");
      XLSX.writeFile(libro, `${archivo}-${getFechaActualLocal()}.xlsx`);
    } finally {
      setGenerando(false);
    }
  };

  return (
    <Button
      type="button"
      variant="ghost"
      disabled={deshabilitado}
      onClick={() => void exportar()}
      title={
        exportables.length === 0
          ? "Sin columnas exportables"
          : "Exportar página visible a Excel"
      }
    >
      <Download className="h-4 w-4" /> Excel
    </Button>
  );
}
