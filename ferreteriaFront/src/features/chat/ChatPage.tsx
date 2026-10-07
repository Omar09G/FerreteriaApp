import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";

import { useT } from "@/i18n";
import {
	apiConversaciones,
	apiCrearDirecta,
	apiCrearGrupo,
	apiEnviarMensaje,
	apiHistorial,
	apiMarcarChatLeida,
} from "@/lib/api/chat";
import { apiUsuarios } from "@/lib/api/admin";
import type { ChatConversacion, Usuario } from "@/lib/api/types";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Dialog } from "@/components/ui/Dialog";
import { EmptyState } from "@/components/ui/EmptyState";
import { Input, Select } from "@/components/ui/Input";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { useAuthStore } from "@/store/auth";

/** Chat interno 1 a 1 y por grupos (el push llega por SSE; polling de respaldo). */
export default function ChatPage() {
	const t = useT();
	const { error: mostrarError, success: mostrarExito } = useToast();
	const queryClient = useQueryClient();
	const yo = useAuthStore((s) => s.usuario);
	const [activaId, setActivaId] = useState<number | null>(null);
	const [busqueda, setBusqueda] = useState("");
	const [grupoAbierto, setGrupoAbierto] = useState(false);
	const [tituloGrupo, setTituloGrupo] = useState("");
	const [miembrosGrupo, setMiembrosGrupo] = useState<number[]>([]);
	const [texto, setTexto] = useState("");
	const fondoRef = useRef<HTMLDivElement>(null);

	const conversacionesQ = useQuery({
		queryKey: ["chat", "conversaciones"],
		queryFn: apiConversaciones,
	});
	const contactosQ = useQuery({
		queryKey: ["chat", "contactos"],
		queryFn: () => apiUsuarios(0, 50),
		staleTime: 60_000,
	});
	const historialQ = useQuery({
		queryKey: ["chat", activaId, 0],
		queryFn: () => apiHistorial(activaId as number, { page: 0, size: 30 }),
		enabled: activaId !== null,
		refetchInterval: 5_000,
	});

	const activa: ChatConversacion | undefined = conversacionesQ.data?.find(
		(c) => c.conversacionId === activaId,
	);
	const mensajes = useMemo(
		() => [...(historialQ.data?.data ?? [])].reverse(),
		[historialQ.data],
	);
	const contactos: Usuario[] = useMemo(() => {
		const lista = contactosQ.data?.data ?? [];
		const q = busqueda.trim().toLowerCase();
		return lista.filter(
			(u) =>
				u.usuarioId !== yo?.usuarioId &&
				(!q || u.username.toLowerCase().includes(q)),
		);
	}, [contactosQ.data, busqueda, yo]);

	useEffect(() => {
		// scrollTo no existe en jsdom (tests): llamada opcional.
		fondoRef.current?.scrollTo?.({ top: fondoRef.current.scrollHeight });
	}, [mensajes.length, activaId]);

	useEffect(() => {
		if (activaId === null) return;
		void apiMarcarChatLeida(activaId).catch(() => undefined);
	}, [activaId]);

	const abrirDirecta = (otroId: number) => {
		void apiCrearDirecta({ otroUsuarioId: otroId })
			.then((c) => {
				void queryClient.invalidateQueries({ queryKey: ["chat"] });
				setActivaId(c.conversacionId);
			})
			.catch(() => mostrarError(t("chat.errorAbrir")));
	};

	const crearGrupo = () => {
		if (!tituloGrupo.trim() || miembrosGrupo.length === 0) return;
		void apiCrearGrupo({ titulo: tituloGrupo.trim(), miembroIds: miembrosGrupo })
			.then((c) => {
				void queryClient.invalidateQueries({ queryKey: ["chat"] });
				setActivaId(c.conversacionId);
				setGrupoAbierto(false);
				setTituloGrupo("");
				setMiembrosGrupo([]);
				mostrarExito(t("chat.grupoCreado"));
			})
			.catch(() => mostrarError(t("chat.errorAbrir")));
	};

	const enviar = () => {
		if (activaId === null || !texto.trim()) return;
		const cuerpo = texto.trim();
		setTexto("");
		void apiEnviarMensaje(activaId, { cuerpo })
			.then(() => {
				void queryClient.invalidateQueries({ queryKey: ["chat"] });
			})
			.catch(() => {
				mostrarError(t("chat.errorEnviar"));
				setTexto(cuerpo);
			});
	};

	return (
		<div className="grid gap-3 lg:grid-cols-[280px_1fr]">
			<Card titulo={t("chat.conversaciones")}>
				<Input
					label={t("chat.buscarContacto")}
					value={busqueda}
					onChange={(e) => setBusqueda(e.target.value)}
					placeholder={t("chat.buscarContactoPh")}
				/>
				{busqueda.trim() && (
					<ul className="mt-1 max-h-40 overflow-y-auto rounded border border-line">
						{contactos.map((u) => (
							<li key={u.usuarioId}>
								<button
									type="button"
									onClick={() => abrirDirecta(u.usuarioId)}
									className="block w-full px-2 py-1.5 text-left text-sm hover:bg-warmbg"
								>
									{u.username}
								</button>
							</li>
						))}
						{contactos.length === 0 && (
							<li className="px-2 py-1.5 text-sm text-muted">{t("chat.sinContactos")}</li>
						)}
					</ul>
				)}
				<div className="mt-2">
					<Button variant="secondary" size="sm" onClick={() => setGrupoAbierto(true)}>
						{t("chat.nuevoGrupo")}
					</Button>
				</div>
				<div className="mt-2">
					{conversacionesQ.isFetching && <Spinner />}
					<ul className="divide-y divide-line">
						{(conversacionesQ.data ?? []).map((c) => (
							<li key={c.conversacionId}>
								<button
									type="button"
									onClick={() => setActivaId(c.conversacionId)}
									className={`block w-full px-2 py-2 text-left hover:bg-warmbg ${activaId === c.conversacionId ? "bg-warmbg" : ""}`}
								>
									<span className="flex items-center justify-between gap-2">
										<span className="truncate text-sm font-medium">{c.titulo}</span>
										{c.noLeidos > 0 && (
											<span className="flex h-4 min-w-4 items-center justify-center rounded-full bg-red-600 px-1 text-[10px] font-bold text-white">
												{c.noLeidos > 99 ? "99+" : c.noLeidos}
											</span>
										)}
									</span>
									{c.ultimoMensaje && (
										<span className="block truncate text-xs text-muted">
											{c.ultimoMensaje.autorNombre}: {c.ultimoMensaje.cuerpo}
										</span>
									)}
								</button>
							</li>
						))}
					</ul>
					{!conversacionesQ.isFetching && (conversacionesQ.data ?? []).length === 0 && (
						<EmptyState title={t("chat.titulo")} descripcion={t("chat.sinConversaciones")} />
					)}
				</div>
			</Card>

			<Card
				titulo={activa ? activa.titulo : t("chat.titulo")}
				actions={
					activa && (
						<Button variant="ghost" size="sm" onClick={() => setActivaId(null)}>
							{t("chat.cerrar")}
						</Button>
					)
				}
			>
				{!activa && (
					<EmptyState title={t("chat.titulo")} descripcion={t("chat.eligirConversacion")} />
				)}
				{activa && (
					<>
						<div ref={fondoRef} className="max-h-[50vh] min-h-40 overflow-y-auto rounded border border-line bg-warmbg/40 p-2">
							{historialQ.isFetching && mensajes.length === 0 && <Spinner />}
							{mensajes.map((m) => {
								const mio = m.autorId === yo?.usuarioId;
								return (
									<div key={m.mensajeId} className={`mb-1.5 flex ${mio ? "justify-end" : "justify-start"}`}>
										<div
											className={`max-w-[80%] rounded-lg px-2.5 py-1.5 text-sm shadow-sm ${mio ? "bg-primary text-white" : "bg-surface"}`}
										>
											{!mio && (
												<p className="text-[11px] font-semibold text-primary">{m.autorNombre}</p>
											)}
											<p className="whitespace-pre-wrap break-words">{m.cuerpo}</p>
											<p className={`text-right text-[10px] ${mio ? "text-white/80" : "text-muted"}`}>
												{new Date(m.creadaEn).toLocaleString("es-MX", {
													day: "2-digit",
													month: "2-digit",
													hour: "2-digit",
													minute: "2-digit",
												})}
											</p>
										</div>
									</div>
								);
							})}
						</div>
						<div className="mt-2 flex gap-2">
							<Input
								label={t("chat.escribir")}
								value={texto}
								onChange={(e) => setTexto(e.target.value)}
								placeholder={t("chat.escribirPh")}
								onKeyDown={(e) => {
									if (e.key === "Enter" && !e.shiftKey) {
										e.preventDefault();
										enviar();
									}
								}}
							/>
							<div className="pt-6">
								<Button onClick={enviar} disabled={!texto.trim()}>
									{t("chat.enviar")}
								</Button>
							</div>
						</div>
					</>
				)}
			</Card>

			<Dialog
				title={t("chat.nuevoGrupo")}
				open={grupoAbierto}
				onClose={() => setGrupoAbierto(false)}
			>
				<Input
					label={t("chat.tituloGrupo")}
					value={tituloGrupo}
					onChange={(e) => setTituloGrupo(e.target.value)}
				/>
				<div className="mt-2">
					<Select
						label={t("chat.miembros")}
						multiple
						value={miembrosGrupo.map(String)}
						onChange={(e) => {
							const sel = Array.from(e.target.selectedOptions).map((o) => Number(o.value));
							setMiembrosGrupo(sel);
						}}
					>
						{(contactosQ.data?.data ?? [])
							.filter((u) => u.usuarioId !== yo?.usuarioId)
							.map((u) => (
								<option key={u.usuarioId} value={u.usuarioId}>
									{u.username}
								</option>
							))}
					</Select>
				</div>
				<div className="mt-3 flex justify-end gap-2">
					<Button variant="secondary" onClick={() => setGrupoAbierto(false)}>
						{t("appshell.acciones.cancelar")}
					</Button>
					<Button onClick={crearGrupo} disabled={!tituloGrupo.trim() || miembrosGrupo.length === 0}>
						{t("chat.crear")}
					</Button>
				</div>
			</Dialog>
		</div>
	);
}
