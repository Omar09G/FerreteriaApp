package mx.ferreteria.api.cat.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class CatDtos {

        private CatDtos() {
        }

        // ── Marca ──────────────────────────────────────────────────────

        public record MarcaRequest(
                        @NotBlank @Size(max = 100) String nombre) {
        }

        public record MarcaResponse(
                        Integer marcaId,
                        String nombre) {
        }

        // ── UnidadMedida ───────────────────────────────────────────────

        public record UnidadMedidaRequest(
                        @NotBlank @Size(max = 10) String clave,
                        @NotBlank @Size(max = 50) String nombre,
                        Boolean permiteFraccion) {
        }

        public record UnidadMedidaResponse(
                        Integer unidadId,
                        String clave,
                        String nombre,
                        Boolean permiteFraccion) {
        }

        // ── Categoria ──────────────────────────────────────────────────

        public record CategoriaRequest(
                        @NotBlank @Size(max = 100) String nombre,
                        @Positive Integer categoriaPadreId) {
        }

        public record CategoriaResponse(
                        Integer categoriaId,
                        String nombre,
                        Integer categoriaPadreId,
                        String ruta,
                        Short nivel,
                        List<CategoriaResponse> hijos) {
        }

        // ── Proveedor ──────────────────────────────────────────────────

        public record ProveedorRequest(
                        @NotBlank @Size(max = 180) String razonSocial,
                        @Pattern(regexp = "^[A-ZÑ&]{3,4}[0-9]{6}[A-V1-9][A-Z0-9]{2}$") @Size(max = 13) String rfc,
                        @Size(max = 10) String regimenFiscal,
                        @Email @Size(max = 120) String email,
                        @Pattern(regexp = "^[0-9+()\\-\\s]{7,20}$") @Size(max = 20) String telefono,
                        @Min(0) Integer diasCredito,
                        @DecimalMin(value = "0", inclusive = true) BigDecimal limiteCredito) {
        }

        public record ProveedorResponse(
                        Integer proveedorId,
                        String razonSocial,
                        String rfc,
                        String regimenFiscal,
                        String email,
                        String telefono,
                        Integer diasCredito,
                        BigDecimal limiteCredito) {
        }

        // ── Cliente ────────────────────────────────────────────────────

        public record ClienteRequest(
                        @Pattern(regexp = "FISICA|MORAL") @Size(max = 10) String tipoPersona,
                        @NotBlank @Size(max = 180) String razonSocial,
                        @Size(max = 180) String nombreComercial,
                        @Pattern(regexp = "^[A-ZÑ&]{3,4}[0-9]{6}[A-V1-9][A-Z0-9]{2}$") @Size(max = 13) String rfc,
                        @Pattern(regexp = "^[0-9+()\\-\\s]{7,20}$") @Size(max = 20) String telefono,
                        @Email @Size(max = 120) String email,
                        @DecimalMin(value = "0", inclusive = true) BigDecimal limiteCredito,
                        @Min(0) Integer diasCredito,
                        Boolean esMayorista) {
        }

        public record ClienteResponse(
                        Long clienteId,
                        String tipoPersona,
                        String razonSocial,
                        String nombreComercial,
                        String rfc,
                        String telefono,
                        String email,
                        BigDecimal limiteCredito,
                        Integer diasCredito,
                        Boolean esMayorista) {
        }

        // ── Producto ───────────────────────────────────────────────────

        public record ProductoRequest(
                        @Size(max = 40) String codigo,
                        @NotBlank @Size(max = 20) @Pattern(regexp = "PRODUCTO|SERVICIO|HERRAMIENTA_RENTA") String tipo,
                        @NotBlank @Size(max = 180) String nombre,
                        @Size(max = 2000) String descripcion,
                        @NotNull @Positive Integer categoriaId,
                        @Positive Integer marcaId,
                        @NotNull @Positive Integer unidadMedidaId,
                        @DecimalMin(value = "0", inclusive = true) BigDecimal costoActual,
                        @DecimalMin(value = "0", inclusive = true) BigDecimal precioMenudeo,
                        @DecimalMin(value = "0", inclusive = true) BigDecimal precioMayoreo,
                        Boolean aplicaIva) {
        }

        public record ProductoResponse(
                        Long productoId,
                        String codigo,
                        String tipo,
                        String nombre,
                        String descripcion,
                        Integer categoriaId,
                        String categoriaNombre,
                        Integer marcaId,
                        String marcaNombre,
                        Integer unidadMedidaId,
                        String unidadMedidaClave,
                        BigDecimal costoActual,
                        BigDecimal precioMenudeo,
                        BigDecimal precioMayoreo,
                        Boolean aplicaIva,
                        BigDecimal stockActual) {

                public ProductoResponse withStock(BigDecimal stockActual) {
                        return new ProductoResponse(
                                        productoId, codigo, tipo, nombre, descripcion,
                                        categoriaId, categoriaNombre, marcaId, marcaNombre,
                                        unidadMedidaId, unidadMedidaClave, costoActual,
                                        precioMenudeo, precioMayoreo, aplicaIva, stockActual);
                }
        }
}
