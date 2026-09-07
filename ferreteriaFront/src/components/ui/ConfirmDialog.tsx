import { Dialog } from "@/components/ui/Dialog";

interface ConfirmDialogProps {
	open: boolean;
	title: string;
	children?: React.ReactNode;
	confirmLabel?: string;
	cancelLabel?: string;
	tone?: "danger" | "success" | "primary";
	busy?: boolean;
	onCancel: () => void;
	onConfirm: () => void;
}

const CONFIRM_CLASS: Record<NonNullable<ConfirmDialogProps["tone"]>, string> = {
	danger: "bg-red-600 text-white hover:bg-red-700",
	success: "bg-green-600 text-white hover:bg-green-700",
	primary: "bg-blue-600 text-white hover:bg-blue-700",
};

export function ConfirmDialog({
	open,
	title,
	children,
	confirmLabel = "Confirmar",
	cancelLabel = "Cancelar",
	tone = "danger",
	busy = false,
	onCancel,
	onConfirm,
}: ConfirmDialogProps) {
	return (
		<Dialog
			open={open}
			onClose={onCancel}
			title={title}
			footer={
				<>
					<button
						type="button"
						onClick={onCancel}
						disabled={busy}
						className="rounded-md border border-line bg-surface px-4 py-2 text-sm font-medium text-ink hover:bg-warmbg disabled:opacity-50"
					>
						{cancelLabel}
					</button>
					<button
						type="button"
						onClick={onConfirm}
						disabled={busy}
						className={`rounded-md px-4 py-2 text-sm font-medium disabled:opacity-50 ${CONFIRM_CLASS[tone]}`}
					>
						{busy ? "Procesando…" : confirmLabel}
					</button>
				</>
			}
		>
			{children ?? <p className="text-sm text-muted">¿Confirmar acción?</p>}
		</Dialog>
	);
}
