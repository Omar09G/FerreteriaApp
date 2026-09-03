import type { ReactNode } from "react";

import { EmptyState } from "./EmptyState";
import { Spinner } from "./Spinner";

export interface Columna<T> {
	key: string;
	header: ReactNode;
	render: (item: T) => ReactNode;
	align?: "left" | "right";
	className?: string;
}

interface DataTableProps<T> {
	columnas: Columna<T>[];
	items: T[] | undefined;
	loading?: boolean;
	rowKey: (item: T) => string | number;
	emptyTitle?: string;
	emptyDescripcion?: string;
	caption?: string;
}

export function DataTable<T>({
	columnas,
	items,
	loading,
	rowKey,
	emptyTitle,
	emptyDescripcion,
	caption,
}: DataTableProps<T>) {
	if (loading) {
		return <Spinner />;
	}

	if (!items || items.length === 0) {
		return <EmptyState title={emptyTitle} descripcion={emptyDescripcion} />;
	}

	return (
		<div className="overflow-x-auto">
			<table className="w-full min-w-full border-collapse text-sm">
				{caption && <caption className="sr-only">{caption}</caption>}
				<thead>
					<tr className="border-b border-line text-left text-xs uppercase tracking-wide text-muted">
						{columnas.map((c) => (
							<th
								key={c.key}
								scope="col"
								className={`px-3 py-2 font-medium ${c.align === "right" ? "text-right" : ""} ${c.className ?? ""}`}
							>
								{c.header}
							</th>
						))}
					</tr>
				</thead>
				<tbody className="divide-y divide-line">
					{items.map((item) => (
						<tr key={rowKey(item)} className="hover:bg-orange-50/40">
							{columnas.map((c) => (
								<td
									key={c.key}
									className={`px-3 py-2 align-middle ${c.align === "right" ? "text-right tabular-nums" : ""} ${c.className ?? ""}`}
								>
									{c.render(item)}
								</td>
							))}
						</tr>
					))}
				</tbody>
			</table>
		</div>
	);
}
