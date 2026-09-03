import { ChevronLeft, ChevronRight } from "lucide-react";

import { useT } from "@/i18n";
import type { PageResult } from "@/lib/api/types";

interface PaginationProps {
	meta: PageResult;
	onPage: (page: number) => void;
}

/** Paginación derivada de meta {page, size, totalElements, totalPages}. */
export function Pagination({ meta, onPage }: PaginationProps) {
	const t = useT();
	const { page, totalPages, totalElements } = meta;
	if (totalPages <= 1) return null;

	const numeros: number[] = [];
	const desde = Math.max(0, page - 2);
	const hasta = Math.min(totalPages - 1, page + 2);
	for (let i = desde; i <= hasta; i++) numeros.push(i);

	return (
		<nav
			className="flex items-center justify-between gap-2 border-t border-line px-1 py-2 text-sm"
			aria-label={t("paginacion.aria")}
		>
			<span className="text-muted">
				{t("paginacion.registros", { n: totalElements })}
			</span>
			<div className="flex items-center gap-1">
				<button
					type="button"
					disabled={page === 0}
					onClick={() => onPage(page - 1)}
					aria-label={t("paginacion.anterior")}
					className="rounded border border-line p-1 text-muted hover:bg-warmbg disabled:opacity-40"
				>
					<ChevronLeft className="h-4 w-4" />
				</button>
				{numeros[0] > 0 && <span className="px-1 text-muted">…</span>}
				{numeros.map((n) => (
					<button
						key={n}
						type="button"
						onClick={() => onPage(n)}
						aria-current={n === page ? "page" : undefined}
						className={`min-w-8 rounded border px-2 py-1 ${n === page ? "border-primary bg-primary text-white" : "border-line hover:bg-warmbg"}`}
					>
						{n + 1}
					</button>
				))}
				{numeros[numeros.length - 1] < totalPages - 1 && (
					<span className="px-1 text-muted">…</span>
				)}
				<button
					type="button"
					disabled={page >= totalPages - 1}
					onClick={() => onPage(page + 1)}
					aria-label={t("paginacion.siguiente")}
					className="rounded border border-line p-1 text-muted hover:bg-warmbg disabled:opacity-40"
				>
					<ChevronRight className="h-4 w-4" />
				</button>
			</div>
		</nav>
	);
}
