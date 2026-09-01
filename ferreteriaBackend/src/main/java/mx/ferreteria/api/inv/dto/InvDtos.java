package mx.ferreteria.api.inv.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public final class InvDtos {
    private InvDtos() {}

    // --- Almacen ---
    public record AlmacenRequest(
        @NotBlank @Size(max = 100) String nombre,
        String direccion,
        @Size(max = 20) String telefono,
        Boolean esPuntoVenta
    ) {}
    public record AlmacenResponse(
        Integer almacenId, String nombre, String direccion,
        String telefono, Boolean esPuntoVenta, Boolean activo
    ) {}
    public record AlmacenEstadoRequest(
        @NotNull Boolean activo
    ) {}

    // --- Inventario (stock por almacen) ---
    public record InventarioResponse(
        Long productoId, String productoNombre, String productoCodigo,
        Integer almacenId, String almacenNombre,
        BigDecimal stock, BigDecimal stockMinimo, BigDecimal stockMaximo,
        BigDecimal reservado
    ) {}

    // --- MovimientoInventario ---
    public record MovimientoInventarioRequest(
        @NotNull Long productoId,
        @NotNull Integer almacenId,
        @NotBlank String tipo,
        @NotNull @Min(1) BigDecimal cantidad,
        BigDecimal costoUnitario,
        @NotNull Integer motivoId,
        @Size(max = 40) String refTabla,
        Long refId,
        String nota
    ) {}
    public record MovimientoInventarioResponse(
        Long movimientoId, Long productoId, String productoNombre,
        Integer almacenId, String almacenNombre,
        String tipo, BigDecimal cantidad, BigDecimal costoUnitario,
        Integer motivoId, String motivoNombre,
        String refTabla, Long refId, Long trasladoId,
        String nota, Integer usuarioId, Instant creadoEn
    ) {}

    // --- Traslado ---
    public record TrasladoRequest(
        @NotNull Integer almacenOrigen,
        @NotNull Integer almacenDestino,
        @NotNull List<TrasladoDetalleRequest> detalles
    ) {}
    public record TrasladoDetalleRequest(
        @NotNull Long productoId,
        @NotNull @Min(1) BigDecimal cantidad
    ) {}
    public record TrasladoResponse(
        Long trasladoId, String folio,
        Integer almacenOrigen, String almacenOrigenNombre,
        Integer almacenDestino, String almacenDestinoNombre,
        String estado, Integer usuarioId, Instant creadoEn,
        List<TrasladoDetalleResponse> detalles
    ) {}
    public record TrasladoDetalleResponse(
        Long productoId, String productoNombre,
        BigDecimal cantidad
    ) {}

    // --- ConteoFisico ---
    public record ConteoFisicoRequest(
        @NotNull Integer almacenId,
        String observaciones,
        @NotNull List<ConteoFisicoDetalleRequest> detalles
    ) {}
    public record ConteoFisicoDetalleRequest(
        @NotNull Long productoId,
        @NotNull BigDecimal cantidadFisica
    ) {}
    public record ConteoFisicoResponse(
        Long conteoId, Integer almacenId, String almacenNombre,
        String estado, Integer usuarioId, String observaciones
    ) {}
    public record ConteoFisicoDetalleResponse(
        Long productoId, String productoNombre,
        BigDecimal cantidadSistema, BigDecimal cantidadFisica,
        BigDecimal diferencia
    ) {}
}
