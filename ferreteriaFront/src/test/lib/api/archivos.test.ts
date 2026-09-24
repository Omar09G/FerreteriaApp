import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  apiSubirImagen,
  esUrlImagenSegura,
  IMAGEN_MAX_BYTES,
  IMAGEN_MIME_ACEPTADOS,
} from "@/lib/api/archivos";

vi.mock("@/lib/api/client", () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}));

import http from "@/lib/api/client";

type HttpMock = {
  post: ReturnType<typeof vi.fn>;
};

const mock = http as unknown as HttpMock;

beforeEach(() => {
  vi.clearAllMocks();
});

describe("apiSubirImagen", () => {
  it("envía FormData con el archivo a /archivos/imagen y devuelve la url", async () => {
    const archivo = new File(["bytes"], "foto.jpg", { type: "image/jpeg" });
    mock.post.mockResolvedValueOnce({
      data: { success: true, data: { url: "https://cdn/foto.jpg" } },
    });
    await expect(apiSubirImagen(archivo)).resolves.toBe("https://cdn/foto.jpg");
    expect(mock.post).toHaveBeenCalledWith("/archivos/imagen", expect.any(FormData));
    const forma = mock.post.mock.calls[0][1] as FormData;
    expect(forma.get("archivo")).toBe(archivo);
  });

  it("propaga el error del backend (tipo no permitido / muy grande)", async () => {
    const archivo = new File(["x"], "doc.pdf", { type: "application/pdf" });
    const err = new Error("ARCHIVO_TIPO_NO_PERMITIDO");
    mock.post.mockRejectedValueOnce(err);
    await expect(apiSubirImagen(archivo)).rejects.toBe(err);
  });
});

describe("esUrlImagenSegura", () => {
  it("acepta https, http (MinIO dev) y data:image", () => {
    expect(esUrlImagenSegura("https://cdn/foto.jpg")).toBe(true);
    expect(esUrlImagenSegura("http://localhost:9000/foto.jpg")).toBe(true);
    expect(esUrlImagenSegura("data:image/png;base64,AAA")).toBe(true);
    expect(esUrlImagenSegura("  https://cdn/foto.jpg  ")).toBe(true);
  });

  it("rechaza otros esquemas", () => {
    expect(esUrlImagenSegura("ftp://srv/foto.jpg")).toBe(false);
    expect(esUrlImagenSegura("javascript:alert(1)")).toBe(false);
    expect(esUrlImagenSegura("data:text/html,hi")).toBe(false);
    expect(esUrlImagenSegura("")).toBe(false);
  });
});

describe("constantes de imágenes", () => {
  it("acepta jpeg/png/webp y tope de 5MB", () => {
    expect([...IMAGEN_MIME_ACEPTADOS]).toEqual([
      "image/jpeg",
      "image/png",
      "image/webp",
    ]);
    expect(IMAGEN_MAX_BYTES).toBe(5 * 1024 * 1024);
  });
});
