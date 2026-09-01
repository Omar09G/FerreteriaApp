import { Link } from "react-router-dom";
import {
  CalendarClock,
  CircleDollarSign,
  Clock3,
  LayoutDashboard,
  Medal,
  ShoppingCart,
  Users,
  UserCheck,
} from "lucide-react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { Card } from "@/components/ui/Card";

const ENTRADAS = [
  {
    a: "/reportes/ventas-totales",
    etiqueta: "Ventas totales",
    descripcion: "Tendencia diaria, total e utilidad.",
    icono: <ShoppingCart className="h-5 w-5" />,
  },
  {
    a: "/reportes/horas-pico",
    etiqueta: "Horas pico",
    descripcion: "Horas de mayor actividad.",
    icono: <Clock3 className="h-5 w-5" />,
  },
  {
    a: "/reportes/mejores-dias",
    etiqueta: "Mejores días",
    descripcion: "Días de la semana con más venta.",
    icono: <CalendarClock className="h-5 w-5" />,
  },
  {
    a: "/reportes/top-productos",
    etiqueta: "Top productos",
    descripcion: "Productos más vendidos y utilidad.",
    icono: <Medal className="h-5 w-5" />,
  },
  {
    a: "/reportes/mejores-clientes",
    etiqueta: "Mejores clientes",
    descripcion: "Clientes con mayor compra.",
    icono: <Users className="h-5 w-5" />,
  },
  {
    a: "/reportes/mejores-vendedores",
    etiqueta: "Mejores vendedores",
    descripcion: "Vendedores con mejor desempeño.",
    icono: <UserCheck className="h-5 w-5" />,
  },
  {
    a: "/reportes/cierre-diario",
    etiqueta: "Cierre diario",
    descripcion: "Cuadratura de cortes por día.",
    icono: <CircleDollarSign className="h-5 w-5" />,
  },
  {
    a: "/reportes/mejores-categorias",
    etiqueta: "Mejores Categorías",
    descripcion: "Categorías con mayor venta y utilidad.",
    icono: <Medal className="h-5 w-5" />,
  },
  {
    a: "/reportes/productos-sin-movimiento",
    etiqueta: "Productos sin Movimiento",
    descripcion: "Productos sin venta en el periodo.",
    icono: <ShoppingCart className="h-5 w-5" />,
  },
  {
    a: "/dashboard",
    etiqueta: "Panel de control",
    descripcion: "Indicadores clave del periodo.",
    icono: <LayoutDashboard className="h-5 w-5" />,
  },
];

export default function ReportesIndexPage() {
  useDocumentTitle("Reportes");
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-bold text-ink">Reportes</h1>
        <p className="text-sm text-muted">
          Todos los reportes usan el rango de fechas (por defecto, hoy).
        </p>
      </header>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {ENTRADAS.map((e) => (
          <Link key={e.a} to={e.a} className="block">
            <Card className="h-full p-4 transition-colors hover:border-primary">
              <div className="flex items-start gap-3">
                <span
                  className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-orange-100 text-primary"
                  aria-hidden
                >
                  {e.icono}
                </span>
                <div>
                  <p className="font-semibold text-ink">{e.etiqueta}</p>
                  <p className="text-sm text-muted">{e.descripcion}</p>
                </div>
              </div>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
