import type { ReactNode } from "react";
import { Inbox } from "lucide-react";

import { useT } from "@/i18n";

export function EmptyState({
	title,
	descripcion,
	action,
}: {
	title?: string;
	descripcion?: string;
	action?: ReactNode;
}) {
	const t = useT();
	return (
		<div className="flex flex-col items-center justify-center gap-2 py-12 text-center text-muted">
			<Inbox className="h-8 w-8 text-line" aria-hidden />
			<p className="text-sm font-medium text-ink">
				{title ?? t("comun.sinResultados")}
			</p>
			{descripcion && <p className="max-w-sm text-sm">{descripcion}</p>}
			{action}
		</div>
	);
}
