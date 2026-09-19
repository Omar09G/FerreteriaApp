import { useEffect, useRef, useState } from "react";
import { CampoWidget } from "@/components/ui/Input";
import {
  apiSubirImagen,
  esUrlImagenSegura,
  IMAGEN_MAX_BYTES,
  IMAGEN_MIME_ACEPTADOS,
} from "@/lib/api/archivos";
import { esApiError } from "@/lib/api/client";

interface ImagenUploadProps {
  label?: string;
  hint?: string;
  /** URL actual guardada (fotoUrl/imagenUrl). Null = sin imagen. */
  value: string | null | undefined;
  /** Se llama con la URL pública tras subir, o null al quitar. */
  onChange: (url: string | null) => void;
  disabled?: boolean;
}

/**
 * Miniatura para tablas y detalles. Solo renderiza la imagen si pasa la
 * allowlist (https:/data:image/); si no, un bloque neutro. Nunca hace fetch
 * de esquemas inseguros (javascript:, file:).
 */
/**
 * Miniatura para tablas y detalles. Solo renderiza la imagen si pasa la
 * allowlist (https:/data:image/); si no, un bloque neutro. Nunca hace fetch
 * de esquemas inseguros (javascript:, file:).
 */
export function FotoMiniatura({
  url,
  alt,
  redonda = true,
}: {
  url: string | null | undefined;
  alt: string;
  redonda?: boolean;
}) {
  const segura = url != null && esUrlImagenSegura(url) ? url : null;

  // Clases base compartidas para mantener consistencia en dimensiones y formas
  const clasesBase = `h-9 w-9 shrink-0 object-cover transition-all duration-200 ease-in-out ${
    redonda ? "rounded-full" : "rounded-lg"
  }`;

  if (!segura) {
    return (
      <span
        aria-label={`Sin foto: ${alt}`}
        className={`${clasesBase} border border-slate-200 bg-slate-50 shadow-inner flex items-center justify-center`}
      >
        {/* Icono minimalista opcional de reemplazo (placeholder) */}
        <svg
          className="h-4 w-4 text-slate-400"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={1.5}
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z"
          />
        </svg>
      </span>
    );
  }

  return (
    <img
      src={segura}
      alt={alt}
      loading="lazy"
      className={`${clasesBase} ring-1 ring-black/5 shadow-sm hover:scale-105`}
    />
  );
}

/**
 * Subida + previsualización de foto de entidad, reutilizable en
 * productos/clientes/proveedores/empleados. Flujo en 2 pasos: sube a
 * POST /archivos/imagen y entrega la URL para el create/update.
 * Valida tipo (jpeg/png/webp) y tamaño (5MB) en cliente además del backend.
 */
export function ImagenUpload({
  label = "Foto",
  hint = "JPEG, PNG o WebP · máx. 5 MB",
  value,
  onChange,
  disabled = false,
}: ImagenUploadProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [subiendo, setSubiendo] = useState(false);
  const [error, setError] = useState<string | null>(null);
  /** Preview local del archivo recién elegido (antes/durante la subida). */
  const [previewLocal, setPreviewLocal] = useState<string | null>(null);

  useEffect(() => {
    return () => {
      if (previewLocal) URL.revokeObjectURL(previewLocal);
    };
  }, [previewLocal]);

  const urlSegura = value != null && esUrlImagenSegura(value) ? value : null;
  const preview = previewLocal ?? urlSegura;

  async function manejarArchivo(archivo: File | undefined) {
    if (!archivo) return;
    setError(null);
    if (
      !IMAGEN_MIME_ACEPTADOS.includes(
        archivo.type as (typeof IMAGEN_MIME_ACEPTADOS)[number],
      )
    ) {
      setError("Tipo no permitido. Solo JPEG, PNG o WebP.");
      return;
    }
    if (archivo.size > IMAGEN_MAX_BYTES) {
      setError("La imagen supera el máximo de 5 MB.");
      return;
    }
    const local = URL.createObjectURL(archivo);
    setPreviewLocal(local);
    setSubiendo(true);
    try {
      const url = await apiSubirImagen(archivo);
      onChange(url);
    } catch (e) {
      setError(
        esApiError(e) ? e.mensajeParaUsuario() : "No se pudo subir la imagen.",
      );
      setPreviewLocal(null);
      URL.revokeObjectURL(local);
    } finally {
      setSubiendo(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  }

  function quitar() {
    setPreviewLocal(null);
    setError(null);
    onChange(null);
    if (inputRef.current) inputRef.current.value = "";
  }

  return (
    <CampoWidget label={label} hint={hint} error={error ?? undefined}>
      <div className="flex items-center gap-3">
        <div className="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-md border border-line bg-warmbg">
          {preview ? (
            <img
              src={preview}
              alt="Vista previa"
              className="h-full w-full object-cover"
            />
          ) : (
            <span className="px-1 text-center text-[10px] text-muted">
              Sin imagen
            </span>
          )}
        </div>
        <div className="flex flex-col gap-1.5">
          <div className="flex gap-2">
            <button
              type="button"
              disabled={disabled || subiendo}
              onClick={() => inputRef.current?.click()}
              className="rounded-md border border-line bg-surface px-3 py-1.5 text-sm text-ink hover:bg-warmbg disabled:opacity-50"
            >
              {subiendo ? "Subiendo…" : preview ? "Cambiar" : "Elegir imagen"}
            </button>
            {preview && !subiendo && (
              <button
                type="button"
                disabled={disabled}
                onClick={quitar}
                className="rounded-md border border-line bg-surface px-3 py-1.5 text-sm text-red-600 hover:bg-warmbg disabled:opacity-50"
              >
                Quitar
              </button>
            )}
          </div>
          <input
            ref={inputRef}
            type="file"
            accept={IMAGEN_MIME_ACEPTADOS.join(",")}
            className="hidden"
            disabled={disabled || subiendo}
            onChange={(e) => void manejarArchivo(e.target.files?.[0])}
          />
        </div>
      </div>
    </CampoWidget>
  );
}
