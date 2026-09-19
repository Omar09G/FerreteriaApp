import { useEffect, useRef, useState } from "react";
import { CampoWidget } from "@/components/ui/Input";
import {
  apiSubirImagen,
  esUrlImagenSegura,
  IMAGEN_MAX_BYTES,
  IMAGEN_MIME_ACEPTADOS,
} from "@/lib/api/archivos";
import { esApiError } from "@/lib/api/client";
import { X } from "lucide-react";

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
        {/* 
        Regresamos a 'div' para evitar que 'disabled' bloquee los botones internos.
        Se añade 'role="button"' y 'tabIndex' para que sea accesible con el teclado solo si no hay imagen.
      */}
        <div
          role={!preview ? "button" : undefined}
          tabIndex={!preview && !disabled && !subiendo ? 0 : undefined}
          onClick={() => !preview && !subiendo && inputRef.current?.click()}
          onKeyDown={(e) => {
            if (!preview && !subiendo && (e.key === "Enter" || e.key === " ")) {
              e.preventDefault();
              inputRef.current?.click();
            }
          }}
          aria-label={
            preview ? "Vista previa de la imagen" : "Elegir e insertar imagen"
          }
          className={`relative group h-24 w-24 shrink-0 flex items-center justify-center overflow-hidden rounded-xl border border-line bg-warmbg transition-all duration-200 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500
          ${!preview && !disabled && !subiendo ? "cursor-pointer hover:border-indigo-500" : "cursor-default"}
        `}
        >
          {preview ? (
            <>
              {/* Imagen real */}
              <img
                src={preview}
                alt="Vista previa"
                className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
              />

              {/* Capa Hover: Los botones internos ahora sí responderán perfectamente */}
              {!disabled && !subiendo && (
                <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity duration-200 flex items-center justify-center gap-2">
                  {/* Botón Cambiar */}
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      inputRef.current?.click();
                    }}
                    className="p-1.5 bg-white text-slate-900 rounded-md shadow-md text-xs font-medium hover:bg-slate-100 transition-transform transform scale-90 group-hover:scale-100 hover:scale-105 duration-150 cursor-pointer"
                    title="Cambiar imagen"
                  >
                    Editar
                  </button>

                  {/* Botón Quitar (X) */}
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      quitar();
                    }}
                    className="p-1.5 bg-red-600 text-white rounded-md shadow-md text-xs font-bold hover:bg-red-700 transition-transform transform scale-90 group-hover:scale-100 hover:scale-105 duration-150 cursor-pointer"
                    title="Quitar imagen"
                  >
                    <X className="w-4 h-4" />
                  </button>
                </div>
              )}
            </>
          ) : (
            /* Estado sin imagen */
            <span className="px-1 text-center text-xs text-muted font-medium select-none">
              {subiendo ? "..." : "Sin imagen"}
            </span>
          )}

          {/* Spinner de carga superpuesto si está subiendo */}
          {subiendo && (
            <div className="absolute inset-0 bg-warmbg/70 flex items-center justify-center">
              <div className="w-5 h-5 border-2 border-ink border-t-transparent rounded-full animate-spin" />
            </div>
          )}
        </div>

        {/* Sección lateral: Solo muestra el botón principal si NO hay imagen */}
        {!preview && (
          <div className="flex flex-col gap-1.5">
            <button
              type="button"
              disabled={disabled || subiendo}
              onClick={() => inputRef.current?.click()}
              className="rounded-md border border-line bg-surface px-3 py-1.5 text-sm text-ink hover:bg-warmbg disabled:opacity-50 transition-colors"
            >
              {subiendo ? "Subiendo…" : "Elegir imagen"}
            </button>
          </div>
        )}

        {/* Input oculto */}
        <input
          ref={inputRef}
          type="file"
          accept={IMAGEN_MIME_ACEPTADOS.join(",")}
          className="hidden"
          disabled={disabled || subiendo}
          onChange={(e) => void manejarArchivo(e.target.files?.[0])}
        />
      </div>
    </CampoWidget>
  );
}
