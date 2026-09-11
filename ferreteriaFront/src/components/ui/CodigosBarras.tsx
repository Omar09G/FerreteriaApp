/**
 * Insignias con los códigos de barras de un producto.
 * No renderiza nada cuando no hay códigos (el backend aún no los envía),
 * así que es seguro usarlo en todas las filas de producto.
 */
export function CodigosBarras({
	codigos,
	max = 2,
}: {
	codigos?: string[] | null;
	max?: number;
}) {
	if (!codigos || codigos.length === 0) return null;
	const visibles = codigos.slice(0, max);
	return (
		<span className="ml-2 inline-flex flex-wrap items-center gap-1 align-middle">
			{visibles.map((c) => (
				<span
					key={c}
					className="rounded bg-line px-1 font-mono text-[11px] text-muted"
				>
					{c}
				</span>
			))}
			{codigos.length > max && (
				<span className="text-[11px] text-muted">
					+{codigos.length - max}
				</span>
			)}
		</span>
	);
}
