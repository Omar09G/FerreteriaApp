package mx.ferreteria.api.cfg.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class TicketConfigDtos {

    private TicketConfigDtos() {}

    public record TicketConfigRequest(
            @Size(max = 500) String logotipoUrl,
            Boolean mostrarLogotipo,
            @Size(min = 3, max = 180) String nombreNegocio,
            @Size(max = 250) String direccion,
            @Size(max = 10) String cp,
            @Pattern(regexp = "^$|^[A-ZÑ&]{3,4}[0-9]{6}[A-V1-9][0-9A-Z]{2}$", flags = Pattern.Flag.CASE_INSENSITIVE) String rfc,
            @Size(max = 20) String telefono,
            @Size(max = 120) String email,
            @Size(max = 120) String sitioWeb,
            @Size(max = 40) String tituloDocumento,
            Boolean mostrarDatosCliente,
            Boolean mostrarNumeroFactura,
            Boolean mostrarCaja,
            Boolean mostrarFechaHora,
            Boolean mostrarVendedor,
            Boolean mostrarDesgloseIva,
            Boolean mostrarDescuento,
            Boolean mostrarCambio,
            @Size(max = 500) String mensajePie,
            @Size(max = 500) String pieSecundario,
            Short anchoPapelMm,
            Short fontSizePt,
            Integer almacenId
    ) {}

    public record TicketConfigResponse(
            Integer ticketConfigId,
            Integer almacenId,
            String logotipoUrl,
            Boolean mostrarLogotipo,
            String nombreNegocio,
            String direccion,
            String cp,
            String rfc,
            String telefono,
            String email,
            String sitioWeb,
            String tituloDocumento,
            Boolean mostrarDatosCliente,
            Boolean mostrarNumeroFactura,
            Boolean mostrarCaja,
            Boolean mostrarFechaHora,
            Boolean mostrarVendedor,
            Boolean mostrarDesgloseIva,
            Boolean mostrarDescuento,
            Boolean mostrarCambio,
            String mensajePie,
            String pieSecundario,
            Short anchoPapelMm,
            Short fontSizePt,
            String actualizadoEn,
            Integer actualizadoPor
    ) {}
}
