package mx.ferreteria.api.com.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.com.dto.ComDtos;
import mx.ferreteria.api.com.service.CompraService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class CuentasPagarController {

    private final CompraService service;

    @GetMapping("/cuentas-pagar")
    public List<ComDtos.CuentasPagarResponse> cuentasPagar(
            @RequestParam(required = false) String estado) {
        return service.cuentasPagar(estado);
    }

    @GetMapping("/reportes/facturas-vencidas")
    public List<ComDtos.FacturaVencidaResponse> facturasVencidas() {
        return service.facturasVencidas();
    }

    @GetMapping("/reportes/facturas-pendientes")
    public List<ComDtos.FacturaPendienteResponse> facturasPendientes() {
        return service.facturasPendientes();
    }

    @GetMapping("/facturas-proveedor/{proveedorId}")
    public List<ComDtos.FacturaProveedorResponse> facturasProveedor(
            @PathVariable Integer proveedorId) {
        return service.facturasProveedor(proveedorId);
    }

    @PostMapping("/cuentas-pagar/{cuentaPagarId}/abonos")
    public ResponseEntity<ComDtos.PagoProveedorResponse> abonar(
            @PathVariable Long cuentaPagarId,
            @Valid @RequestBody ComDtos.PagoProveedorRequest body) {
        ComDtos.PagoProveedorResponse resp = service.abonar(cuentaPagarId, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }
}