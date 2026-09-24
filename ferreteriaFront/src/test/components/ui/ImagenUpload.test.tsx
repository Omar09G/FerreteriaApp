import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { FotoMiniatura, ImagenUpload } from "@/components/ui/ImagenUpload";
import { apiSubirImagen } from "@/lib/api/archivos";

vi.mock("@/lib/api/archivos", async (importOriginal) => {
	const real = await importOriginal<typeof import("@/lib/api/archivos")>();
	return { ...real, apiSubirImagen: vi.fn() };
});

const subirMock = vi.mocked(apiSubirImagen);

beforeEach(() => {
	vi.clearAllMocks();
	Object.defineProperty(URL, "createObjectURL", {
		value: vi.fn(() => "blob:mock"),
		writable: true,
		configurable: true,
	});
	Object.defineProperty(URL, "revokeObjectURL", {
		value: vi.fn(),
		writable: true,
		configurable: true,
	});
});

function archivoValido() {
	return new File(["x"], "foto.jpg", { type: "image/jpeg" });
}

function inputFile(container: HTMLElement) {
	const input = container.querySelector('input[type="file"]') as HTMLInputElement;
	expect(input).not.toBeNull();
	return input;
}

describe("FotoMiniatura", () => {
	it("renderiza img con URL segura", () => {
		render(<FotoMiniatura url="https://cdn.tienda.com/f.jpg" alt="Prod" />);
		expect(screen.getByRole("img", { name: "Prod" })).toHaveAttribute(
			"src",
			"https://cdn.tienda.com/f.jpg",
		);
	});

	it("muestra placeholder con URL insegura o sin url", () => {
		const { rerender } = render(<FotoMiniatura url="javascript:alert(1)" alt="P" />);
		expect(screen.getByLabelText("Sin foto: P")).toBeInTheDocument();
		rerender(<FotoMiniatura url={null} alt="P" />);
		expect(screen.getByLabelText("Sin foto: P")).toBeInTheDocument();
	});
});

describe("ImagenUpload", () => {
	it("renderiza estado sin imagen", () => {
		render(<ImagenUpload value={null} onChange={vi.fn()} />);
		expect(screen.getByText("Sin imagen")).toBeInTheDocument();
		expect(screen.getByText("Elegir imagen")).toBeInTheDocument();
	});

	it("rechaza tipo no permitido", async () => {
		const onChange = vi.fn();
		const { container } = render(<ImagenUpload value={null} onChange={onChange} />);
		// fireEvent directo: user-event upload respeta el accept del input
		fireEvent.change(inputFile(container), {
			target: { files: [new File(["x"], "doc.pdf", { type: "application/pdf" })] },
		});
		expect(await screen.findByText("Tipo no permitido. Solo JPEG, PNG o WebP.")).toBeInTheDocument();
		expect(onChange).not.toHaveBeenCalled();
	});

	it("rechaza archivo mayor a 5MB", async () => {
		const user = userEvent.setup();
		const onChange = vi.fn();
		const { container } = render(<ImagenUpload value={null} onChange={onChange} />);
		const grande = new File([new Uint8Array(6 * 1024 * 1024)], "g.jpg", { type: "image/jpeg" });
		await user.upload(inputFile(container), grande);
		expect(await screen.findByText("La imagen supera el máximo de 5 MB.")).toBeInTheDocument();
		expect(onChange).not.toHaveBeenCalled();
	});

	it("sube archivo válido y llama onChange con la URL", async () => {
		const user = userEvent.setup();
		subirMock.mockResolvedValue("https://cdn.tienda.com/f.jpg");
		const onChange = vi.fn();
		const { container } = render(<ImagenUpload value={null} onChange={onChange} />);
		await user.upload(inputFile(container), archivoValido());
		expect(subirMock).toHaveBeenCalledTimes(1);
		expect(await screen.findByAltText("Vista previa")).toBeInTheDocument();
		expect(onChange).toHaveBeenCalledWith("https://cdn.tienda.com/f.jpg");
	});

	it("muestra error si la subida falla", async () => {
		const user = userEvent.setup();
		subirMock.mockRejectedValue(new Error("500"));
		const { container } = render(<ImagenUpload value={null} onChange={vi.fn()} />);
		await user.upload(inputFile(container), archivoValido());
		expect(await screen.findByText("No se pudo subir la imagen.")).toBeInTheDocument();
	});

	it("quitar llama onChange con null", async () => {
		const user = userEvent.setup();
		const onChange = vi.fn();
		render(
			<ImagenUpload value="https://cdn.tienda.com/f.jpg" onChange={onChange} />,
		);
		await user.click(screen.getByTitle("Quitar imagen"));
		expect(onChange).toHaveBeenCalledWith(null);
	});
});
