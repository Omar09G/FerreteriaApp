import { Loader2 } from "lucide-react";

import { useT } from "@/i18n";

export function Spinner({ label }: { label?: string }) {
	const t = useT();
	return (
		<div
			className="flex items-center justify-center gap-2 py-8 text-muted"
			role="status"
			aria-live="polite"
		>
			<Loader2 className="h-5 w-5 animate-spin text-primary" aria-hidden />
			<span className="text-sm">{label ?? t("comun.cargando")}</span>
		</div>
	);
}
