import { Card } from "@/components/ui/Card";
import { Link } from "react-router-dom";

export default function CardListReportes() {
  return (
    <Card
      titulo={`Periodo seleccionado`}
      actions={
        <Link to="/reportes" className="text-sm text-primary hover:underline">
          Ver reportes →
        </Link>
      }
    >
      <p className="text-sm text-muted">
        Consulta los reportes detallados (ventas por hora, días de mayor venta,
        productos y clientes) desde la sección Reportes, con el mismo rango de
        fechas.
      </p>
    </Card>
  );
}
