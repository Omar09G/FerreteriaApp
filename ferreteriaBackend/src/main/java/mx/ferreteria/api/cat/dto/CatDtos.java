package mx.ferreteria.api.cat.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
                        Integer categoriaPadreId) {
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
                        @Size(max = 13) String rfc,
                        @Size(max = 10) String regimenFiscal,
                        @Size(max = 120) String email,
                        @Size(max = 20) String telefono,
                        Integer diasCredito,
                        BigDecimal limiteCredito) {
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
                        @Size(max = 10) String tipoPersona,
                        @NotBlank @Size(max = 180) String razonSocial,
                        @Size(max = 180) String nombreComercial,
                        @Size(max = 13) String rfc,
                        @Size(max = 20) String telefono,
                        @Size(max = 120) String email,
                        BigDecimal limiteCredito,
                        Integer diasCredito,
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
                        @NotNull @Size(max = 20) String tipo,
                        @NotBlank @Size(max = 180) String nombre,
                        String descripcion,
                        @NotNull Integer categoriaId,
                        Integer marcaId,
                        @NotNull Integer unidadMedidaId,
                        BigDecimal costoActual,
                        BigDecimal precioMenudeo,
                        BigDecimal precioMayoreo,
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
