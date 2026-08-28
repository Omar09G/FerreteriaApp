package mx.ferreteria.api.com.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import mx.ferreteria.api.cat.entity.FormaPago;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.entity.Proveedor;
import mx.ferreteria.api.cat.repo.FormaPagoRepository;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.cat.repo.ProveedorRepository;
import mx.ferreteria.api.com.dto.ComDtos.CompraDetalleRequest;
import mx.ferreteria.api.com.dto.ComDtos.CompraRequest;
import mx.ferreteria.api.com.entity.Compra;
import mx.ferreteria.api.com.entity.CompraDetalle;
import mx.ferreteria.api.com.repo.CompraDetalleRepository;
import mx.ferreteria.api.com.repo.CompraRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.repo.AlmacenRepository;

@ExtendWith(MockitoExtension.class)
class CompraServiceTest {

    @Mock CompraRepository compraRepo;
    @Mock CompraDetalleRepository detalleRepo;
    @Mock ProveedorRepository proveedorRepo;
    @Mock AlmacenRepository almacenRepo;
    @Mock FormaPagoRepository formaPagoRepo;
    @Mock ProductoRepository productoRepo;
    @Mock JdbcTemplate jdbc;

    @InjectMocks
    CompraService service;

    private Compra sampleCompra(Long id) {
        return Compra.builder().compraId(id).folio("COMPRA-0001")
                .proveedorId(1).almacenId(1).formaPagoId(1)
                .fecha(Instant.parse("2026-01-15T10:00:00Z"))
                .subtotal(new BigDecimal("1000.00")).iva(new BigDecimal("160.00"))
                .total(new BigDecimal("1160.00")).estado("RECIBIDA")
                .usuarioId(1).build();
    }

    private CompraDetalle sampleDetalle(Long id) {
        return CompraDetalle.builder().compraDetalleId(id)
                .compraId(1L).productoId(10L)
                .cantidad(new BigDecimal("10.000"))
                .costoUnitario(new BigDecimal("100.00"))
                .importeLinea(new BigDecimal("1000.00")).build();
    }

    private void stubNombres() {
        when(proveedorRepo.findById(1)).thenReturn(Optional.of(
                Proveedor.builder().proveedorId(1).razonSocial("Ferritas SA").build()));
        when(almacenRepo.findById(1)).thenReturn(Optional.of(
                Almacen.builder().almacenId(1).nombre("Bodega Central").build()));
        when(formaPagoRepo.findById(1)).thenReturn(Optional.of(
                FormaPago.builder().formaPagoId(1).nombre("Contado").build()));
        when(productoRepo.findById(10L)).thenReturn(Optional.of(
                Producto.builder().productoId(10L).nombre("Taladro").build()));
    }

    @Test
    @DisplayName("list: filtra por almacen y enriquece nombres")
    void list_byAlmacen() {
        Pageable pg = PageRequest.of(0, 20);
        Compra c = sampleCompra(1L);
        when(compraRepo.findByAlmacenIdOrderByFechaDesc(eq(1), eq(pg)))
                .thenReturn(new PageImpl<>(List.of(c), pg, 1));
        stubNombres();
        when(detalleRepo.findByCompraIdOrderByCompraDetalleId(1L))
                .thenReturn(List.of(sampleDetalle(1L)));

        var result = service.list(1, null, null, null, pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).proveedor()).isEqualTo("Ferritas SA");
        assertThat(result.getContent().get(0).almacen()).isEqualTo("Bodega Central");
        assertThat(result.getContent().get(0).detalles()).hasSize(1);
        assertThat(result.getContent().get(0).detalles().get(0).producto()).isEqualTo("Taladro");
    }

    @Test
    @DisplayName("getById: compra inexistente -> RecursoNoEncontradoException")
    void getById_notFound() {
        when(compraRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("create: guarda cabecera + detalles y re-lee folio asignado por BD")
    void create_ok() {
        Compra saved = sampleCompra(50L);
        when(proveedorRepo.findById(1)).thenReturn(Optional.of(
                Proveedor.builder().proveedorId(1).razonSocial("Ferritas SA").build()));
        when(almacenRepo.findById(1)).thenReturn(Optional.of(
                Almacen.builder().almacenId(1).nombre("Bodega Central").build()));
        when(formaPagoRepo.findById(1)).thenReturn(Optional.of(
                FormaPago.builder().formaPagoId(1).nombre("Contado").build()));
        when(compraRepo.save(any(Compra.class))).thenReturn(saved);
        when(compraRepo.findById(50L)).thenReturn(Optional.of(saved));
        stubNombres();
        when(detalleRepo.findByCompraIdOrderByCompraDetalleId(50L))
                .thenReturn(List.of(sampleDetalle(1L)));

        CompraRequest req = new CompraRequest(
                1, 1, 1, "F-0001", null, null, "Primera compra",
                List.of(new CompraDetalleRequest(10L, new BigDecimal("10.000"),
                        new BigDecimal("100.00"))));

        var resp = service.create(req);

        assertThat(resp.compraId()).isEqualTo(50L);
        assertThat(resp.folio()).isEqualTo("COMPRA-0001");
        assertThat(resp.estado()).isEqualTo("RECIBIDA");
        verify(detalleRepo).save(any(CompraDetalle.class));
        verify(compraRepo).flush();
    }

    @Test
    @DisplayName("create: proveedor inexistente -> RecursoNoEncontradoException")
    void create_proveedorInvalido() {
        when(proveedorRepo.findById(999)).thenReturn(Optional.empty());

        CompraRequest req = new CompraRequest(
                999, 1, 1, null, null, null, null,
                List.of(new CompraDetalleRequest(10L, new BigDecimal("1.000"),
                        new BigDecimal("10.00"))));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("cuentasPagar: sin filtro consulta toda la vista")
    void cuentasPagar_sinFiltro() {
        var v = new mx.ferreteria.api.com.dto.ComDtos.CuentasPagarResponse(
                1L, "COMPRA-0001", "Ferritas SA",
                new BigDecimal("1160.00"), new BigDecimal("600.00"),
                new BigDecimal("560.00"), java.time.LocalDate.now(), 5, "PENDIENTE");
        when(jdbc.query(anyString(), any(BeanPropertyRowMapper.class))).thenReturn(List.of(v));

        var result = service.cuentasPagar(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).proveedor()).isEqualTo("Ferritas SA");
    }

    @Test
    @DisplayName("cuentasPagar: con estado pasa el parametro")
    void cuentasPagar_conEstado() {
        var v = new mx.ferreteria.api.com.dto.ComDtos.CuentasPagarResponse(
                1L, "COMPRA-0001", "Ferritas SA",
                new BigDecimal("1160.00"), new BigDecimal("600.00"),
                new BigDecimal("560.00"), java.time.LocalDate.now(), 5, "PENDIENTE");
        when(jdbc.query(anyString(), any(BeanPropertyRowMapper.class), eq("PENDIENTE")))
                .thenReturn(List.of(v));

        var result = service.cuentasPagar("PENDIENTE");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).estado()).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("facturasVencidas y pendientes: consultan sus vistas")
    void facturasVencidasYPendientes() {
        var v = new mx.ferreteria.api.com.dto.ComDtos.FacturaVencidaResponse(
                1L, "COMPRA-0001", "F-0001", 1, "Ferritas SA", "555-0100",
                java.time.LocalDate.now().minusDays(30),
                new BigDecimal("1160.00"), new BigDecimal("1160.00"),
                BigDecimal.ZERO, java.time.LocalDate.now().minusDays(10),
                10, "10-20 dias");
        when(jdbc.query(eq("SELECT * FROM com.vw_facturas_vencidas"),
                any(BeanPropertyRowMapper.class))).thenReturn(List.of(v));
        when(jdbc.query(eq("SELECT * FROM com.vw_facturas_pendientes"),
                any(BeanPropertyRowMapper.class))).thenReturn(java.util.Collections.emptyList());

        assertThat(service.facturasVencidas()).hasSize(1);
        assertThat(service.facturasPendientes()).isEmpty();
    }

    @Test
    @DisplayName("facturasProveedor: consulta vista con filtro de proveedor")
    void facturasProveedor() {
        var v = new mx.ferreteria.api.com.dto.ComDtos.FacturaProveedorResponse(
                1, 1, "Ferritas SA", "COMPRA-0001", "F-0001",
                java.time.LocalDate.now().minusDays(5),
                new BigDecimal("1000.00"), new BigDecimal("160.00"),
                new BigDecimal("1160.00"), new BigDecimal("1160.00"),
                new BigDecimal("1160.00"), BigDecimal.ZERO,
                "CONTADO", java.time.LocalDate.now().plusDays(55));
        when(jdbc.query(anyString(), any(BeanPropertyRowMapper.class), eq(1)))
                .thenReturn(List.of(v));

        var result = service.facturasProveedor(1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).proveedor()).isEqualTo("Ferritas SA");
    }
}