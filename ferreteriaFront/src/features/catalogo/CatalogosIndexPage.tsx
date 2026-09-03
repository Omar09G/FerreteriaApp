import { useQuery } from "@tanstack/react-query";
import { Boxes, FolderOpen, Settings2 } from "lucide-react";
import { Link } from "react-router-dom";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { apiCatalogosPaneles } from "@/lib/api/catalogos";
import { Card } from "@/components/ui/Card";
import { Spinner } from "@/components/ui/Spinner";

const ICONOS: Record<string, React.ReactNode> = {
	estados: <FolderOpen className="h-5 w-5" />,
	ciudades: <FolderOpen className="h-5 w-5" />,
	puestos: <Settings2 className="h-5 w-5" />,
	motivos_movimiento: <Settings2 className="h-5 w-5" />,
	tipos_gasto: <Settings2 className="h-5 w-5" />,
	formas_pago: <Settings2 className="h-5 w-5" />,
};

export default function CatalogosIndexPage() {
	useDocumentTitle("Catálogos");

	const { data: paneles, isLoading } = useQuery({
		queryKey: ["catalogos-paneles"],
		queryFn: apiCatalogosPaneles,
		staleTime: Infinity,
	});

	if (isLoading) return <Spinner />;

	return (
		<div className="space-y-6 p-6">
			<div>
				<h1 className="text-xl font-semibold text-ink">Catálogos</h1>
				<p className="text-sm text-muted">
					Administración de catálogos maestros. Solo administradores pueden
					modificar.
				</p>
			</div>

			{!paneles || paneles.length === 0 ? (
				<Card>No hay catálogos disponibles.</Card>
			) : (
				<div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
					{paneles.map((p) => (
						<Link
							key={p.clave}
							to={`/catalogos/${p.clave}`}
							className="group rounded-lg border border-line bg-surface p-4 transition-colors hover:border-primary hover:bg-warmbg"
						>
							<div className="flex items-center gap-3">
								<span className="rounded-md bg-orange-100 p-2 text-primary">
									{ICONOS[p.clave] ?? <Boxes className="h-5 w-5" />}
								</span>
								<div className="min-w-0">
									<p className="truncate font-medium text-ink group-hover:text-primary">
										{p.nombre}
									</p>
									<p className="truncate text-xs text-muted">{p.tabla}</p>
								</div>
							</div>
							<div className="mt-3 flex items-center gap-1 text-xs text-muted">
								<span>Editar catálogo</span>
								<span className="text-primary">→</span>
							</div>
						</Link>
					))}
				</div>
			)}
		</div>
	);
}
