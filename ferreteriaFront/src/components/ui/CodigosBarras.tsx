/**
 * Insignias con los códigos de barras de un producto.
 * No renderiza nada cuando no hay códigos (el backend aún no los envía),
 * así que es seguro usarlo en todas las filas de producto.
 *
 * - `lista` (default): hasta `max` insignias + contador. Para buscadores.
 * - `compacto`: una sola línea sin wrap (primer código truncado + contador),
 *   para columnas de tabla donde las insignias rompían el diseño.
 */
export function CodigosBarras({
	codigos,
	max = 2,
	variante = "lista",
}: {
	codigos?: string[] | null;
	max?: number;
	variante?: "lista" | "compacto";
}) {
	if (!codigos || codigos.length === 0) return null;
	if (variante === "compacto") {
		return (
			<span
				className="inline-flex max-w-[16ch] items-center gap-1 align-middle"
				title={codigos.join(", ")}
			>
				<span className="truncate font-mono text-xs text-muted">
					{codigos[0]}
				</span>
				{codigos.length > 1 && (
					<span className="shrink-0 rounded bg-line px-1 text-[11px] text-muted">
						+{codigos.length - 1}
					</span>
				)}
			</span>
		);
	}
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
