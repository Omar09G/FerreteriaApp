import http from "./client";
import type { Envelope } from "./types";

export interface ImagenSubida {
  url: string;
}

/**
 * Sube una foto de entidad (producto, cliente, proveedor, empleado) a MinIO.
 * El backend la renombra a UUID + extensión y devuelve la URL pública, que
 * luego se guarda como `fotoUrl`/`imagenUrl` en el create/update.
 * Contrato: POST /archivos/imagen (multipart, campo `archivo`) ->
 * { success, data: { url } }. 400 ARCHIVO_TIPO_NO_PERMITIDO / ARCHIVO_MUY_GRANDE.
 */
export async function apiSubirImagen(archivo: File): Promise<string> {
  const forma = new FormData();
  forma.append("archivo", archivo);
  const { data } = await http.post<Envelope<ImagenSubida>>(
    "/archivos/imagen",
    forma,
  );
  return data.data.url;
}

/** MIME aceptados por el backend (FotoStorageService). */
export const IMAGEN_MIME_ACEPTADOS = [
  "image/jpeg",
  "image/png",
  "image/webp",
] as const;

/** Tope del backend (minio.max-mb + multipart.max-file-size = 5MB). */
export const IMAGEN_MAX_BYTES = 5 * 1024 * 1024;

/** Allowlist de esquema para previsualizar (igual que el CHECK del backend).
 * Se acepta http además de https porque MinIO en dev sirve en http://localhost:9000;
 * en prod con página HTTPS el navegador bloquea el contenido mixto solo. */
export function esUrlImagenSegura(url: string): boolean {
  return /^(https?:|data:image\/)/i.test(url.trim());
}
