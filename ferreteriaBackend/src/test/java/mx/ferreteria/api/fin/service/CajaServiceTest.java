package mx.ferreteria.api.fin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import mx.ferreteria.api.cat.entity.FormaPago;
import mx.ferreteria.api.cat.repo.FormaPagoRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.fin.dto.FinDtos.CorteRequest;
import mx.ferreteria.api.fin.dto.FinDtos.EsperadoCajaResponse;
import mx.ferreteria.api.fin.dto.FinDtos.MovimientoCajaRequest;
import mx.ferreteria.api.fin.dto.FinDtos.TurnoAperturaRequest;
import mx.ferreteria.api.fin.entity.Caja;
import mx.ferreteria.api.fin.entity.CorteCaja;
import mx.ferreteria.api.fin.entity.MovimientoCaja;
import mx.ferreteria.api.fin.entity.TurnoCaja;
import mx.ferreteria.api.fin.repo.CajaRepository;
import mx.ferreteria.api.fin.repo.CorteCajaRepository;
import mx.ferreteria.api.fin.repo.MovimientoCajaRepository;
import mx.ferreteria.api.fin.repo.TurnoCajaRepository;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.repo.AlmacenRepository;

@ExtendWith(MockitoExtension.class)
class CajaServiceTest {

    @Mock CajaRepository cajaRepo;
    @Mock TurnoCajaRepository turnoRepo;
    @Mock MovimientoCajaRepository movRepo;
    @Mock FormaPagoRepository formaPagoRepo;
    @Mock CorteCajaRepository corteRepo;
    @Mock AlmacenRepository almacenRepo;
    @Mock JdbcTemplate jdbc;

    @InjectMocks
    CajaService service;

    private Caja sampleCaja(Integer id, String nombre) {
        return Caja.builder().cajaId(id).nombre(nombre).almacenId(1).activa(true).build();
    }

    private TurnoCaja sampleTurno(Long id, String estado) {
        return TurnoCaja.builder().turnoCajaId(id).cajaId(1).usuarioId(1)
                .aperturaEn(Instant.now()).montoApertura(new BigDecimal("5000.00"))
                .estado(estado).build();
    }

    private MovimientoCaja sampleMov(Long id) {
        return MovimientoCaja.builder().movimientoId(id).turnoCajaId(1L)
                .tipo("SALIDA").concepto("GASTO_OPERATIVO")
                .monto(new BigDecimal("100.00")).creadoEn(Instant.now()).build();
    }

    private CorteCaja sampleCorte(Long id) {
        return CorteCaja.builder().corteId(id).turnoCajaId(1L).cajaId(1).almacenId(1)
                .usuarioId(1).usuarioCierreId(1).fecha(LocalDate.now())
                .aperturaEn(Instant.now()).cierreEn(Instant.now())
                .subtotal(new BigDecimal("1000.00")).iva(new BigDecimal("160.00"))
                .totalVendido(new BigDecimal("1160.00")).costoVentas(new BigDecimal("600.00"))
                .fondoApertura(new BigDecimal("5000.00"))
                .entradasEfectivo(new BigDecimal("1160.00")).salidasEfectivo(BigDecimal.ZERO)
                .dineroEsperado(new BigDecimal("6160.00")).dineroContado(new BigDecimal("6160.00"))
                .diferencia(BigDecimal.ZERO).build();
    }

    // ─── listCajas ──────────────────────────────────────────────────

    @Test
    @DisplayName("listCajas: retorna cajas activas con nombre de almacen")
    void listCajas_returnsActivas() {
        when(cajaRepo.findByActivaTrue()).thenReturn(List.of(
                sampleCaja(1, "Caja Central"), sampleCaja(2, "Caja Norte")));
        when(almacenRepo.findById(1)).thenReturn(Optional.of(
                Almacen.builder().almacenId(1).nombre("Almacen Central").build()));

        var result = service.listCajas();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).nombre()).isEqualTo("Caja Central");
        assertThat(result.get(0).almacenNombre()).isEqualTo("Almacen Central");
    }

    // ─── abrirTurno ─────────────────────────────────────────────────

    @Test
    @DisplayName("abrirTurno: sin turno abierto guarda nuevo turno")
    void abrirTurno_ok() {
        when(cajaRepo.findById(1)).thenReturn(Optional.of(sampleCaja(1, "Caja Central")));
        when(turnoRepo.findByCajaIdAndEstado(1, "ABIERTO")).thenReturn(Optional.empty());
        when(turnoRepo.save(any(TurnoCaja.class))).thenReturn(sampleTurno(10L, "ABIERTO"));

        var resp = service.abrirTurno(new TurnoAperturaRequest(1, new BigDecimal("5000.00")));

        assertThat(resp.turnoCajaId()).isEqualTo(10L);
        assertThat(resp.estado()).isEqualTo("ABIERTO");
        verify(turnoRepo).save(any(TurnoCaja.class));
    }

    @Test
    @DisplayName("abrirTurno: ya existe turno abierto -> ReglaNegocioException")
    void abrirTurno_alreadyOpen() {
        when(cajaRepo.findById(1)).thenReturn(Optional.of(sampleCaja(1, "Caja Central")));
        when(turnoRepo.findByCajaIdAndEstado(1, "ABIERTO"))
                .thenReturn(Optional.of(sampleTurno(7L, "ABIERTO")));

        assertThatThrownBy(() -> service.abrirTurno(new TurnoAperturaRequest(1, new BigDecimal("5000.00"))))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("abrirTurno: caja inexistente -> RecursoNoEncontradoException")
    void abrirTurno_cajaNotFound() {
        when(cajaRepo.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.abrirTurno(new TurnoAperturaRequest(999, new BigDecimal("100.00"))))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ─── movimientos ────────────────────────────────────────────────

    @Test
    @DisplayName("registrarMovimiento: valida turno y guarda movimiento")
    void registrarMovimiento_ok() {
        when(turnoRepo.findById(1L)).thenReturn(Optional.of(sampleTurno(1L, "ABIERTO")));
        when(movRepo.save(any(MovimientoCaja.class))).thenReturn(sampleMov(5L));

        var resp = service.registrarMovimiento(1L,
                new MovimientoCajaRequest("SALIDA", "GASTO_OPERATIVO",
                        new BigDecimal("100.00"), 1, null, null));

        assertThat(resp.movimientoId()).isEqualTo(5L);
        assertThat(resp.concepto()).isEqualTo("GASTO_OPERATIVO");
    }

    @Test
    @DisplayName("registrarMovimiento: concepto no permitido -> ReglaNegocioException")
    void registrarMovimiento_conceptoInvalido() {
        assertThatThrownBy(() -> service.registrarMovimiento(1L,
                new MovimientoCajaRequest("ENTRADA", "DInero",
                        new BigDecimal("10000.00"), null, null, null)))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("listMovimientos: retorna lista ordenada")
    void listMovimientos_returnsList() {
        when(movRepo.findByTurnoCajaIdOrderByCreadoEnAsc(1L))
                .thenReturn(List.of(sampleMov(1L), sampleMov(2L)));

        var result = service.listMovimientos(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).movimientoId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("listMovimientos: enriquece forma de pago y folio del abono a proveedor")
    void listMovimientos_abonoEnriqueceFormaYFolio() {
        MovimientoCaja abono = MovimientoCaja.builder().movimientoId(7L).turnoCajaId(1L)
                .tipo("SALIDA").concepto("PAGO_PROVEEDOR")
                .monto(new BigDecimal("3500.00"))
                .formaPagoId(4).refTabla("com.pagos_proveedor").refId(99L)
                .creadoEn(Instant.now()).build();
        when(movRepo.findByTurnoCajaIdOrderByCreadoEnAsc(1L))
                .thenReturn(List.of(abono));
        when(formaPagoRepo.findById(4)).thenReturn(Optional.of(
                FormaPago.builder().formaPagoId(4).nombre("Transferencia SPEI").build()));
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class), eq(99L)))
                .thenReturn("C-00000003");

        var result = service.listMovimientos(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).formaPagoNombre()).isEqualTo("Transferencia SPEI");
        assertThat(result.get(0).refDescripcion()).isEqualTo("C-00000003");
    }

    @Test
    @DisplayName("obtenerEsperado: calcula apertura + entradas - salidas efectivo")
    void obtenerEsperado_computaEsperado() {
        when(turnoRepo.findById(1L)).thenReturn(Optional.of(sampleTurno(1L, "ABIERTO")));
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(1L)))
                .thenReturn(new EsperadoCajaResponse(
                        new BigDecimal("5000.00"), new BigDecimal("1160.00"), new BigDecimal("3500.00"),
                        new BigDecimal("2660.00")));

        var resp = service.obtenerEsperado(1L);

        assertThat(resp.esperado()).isEqualByComparingTo("2660.00");
        assertThat(resp.montoApertura()).isEqualByComparingTo("5000.00");
        assertThat(resp.entradasEfectivo()).isEqualByComparingTo("1160.00");
        assertThat(resp.salidasEfectivo()).isEqualByComparingTo("3500.00");
    }

    @Test
    @DisplayName("obtenerEsperado: turno no existe -> RecursoNoEncontradoException")
    void obtenerEsperado_turnoNotFound() {
        when(turnoRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerEsperado(99L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ─── corte ──────────────────────────────────────────────────────

    @Test
    @DisplayName("cerrarTurno: llama fn_cerrar_turno y re-lee el corte")
    void cerrarTurno_ok() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(1L), any(), any(), any()))
                .thenReturn(1L);
        when(cajaRepo.findById(1)).thenReturn(Optional.of(sampleCaja(1, "Caja Central")));
        when(almacenRepo.findById(1)).thenReturn(Optional.of(
                Almacen.builder().almacenId(1).nombre("Almacen Central").build()));
        when(corteRepo.findById(1L)).thenReturn(Optional.of(sampleCorte(1L)));

        var resp = service.cerrarTurno(1L, new CorteRequest(new BigDecimal("6160.00"), null));

        assertThat(resp.corteId()).isEqualTo(1L);
        assertThat(resp.resultadoCaja()).isEqualTo("CUADRADO");
        verify(jdbc).queryForObject(eq("SELECT fin.fn_cerrar_turno(?, ?, ?, ?)"),
                eq(Long.class), eq(1L), eq(new BigDecimal("6160.00")), eq(0), isNull());
    }

    @Test
    @DisplayName("cerrarTurno: corte no encontrado -> RecursoNoEncontradoException")
    void cerrarTurno_corteNotFound() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(1L), any(), any(), any()))
                .thenReturn(99L);
        when(corteRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cerrarTurno(1L,
                new CorteRequest(new BigDecimal("6160.00"), null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("listCortes: retorna pagina de cortes")
    void listCortes_returnsPage() {
        Pageable pg = PageRequest.of(0, 10);
        CorteCaja c = sampleCorte(1L);
        when(corteRepo.findAllByRangoFecha(isNull(), isNull(), eq(pg)))
                .thenReturn(new PageImpl<>(List.of(c), pg, 1));
        when(cajaRepo.findById(1)).thenReturn(Optional.of(sampleCaja(1, "Caja Central")));
        when(almacenRepo.findById(1)).thenReturn(Optional.of(
                Almacen.builder().almacenId(1).nombre("Almacen Central").build()));

        var result = service.listCortes(null, null, pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).resultadoCaja()).isEqualTo("CUADRADO");
    }

    @Test
    @DisplayName("listCortes: filtra por rango de fechas")
    void listCortes_conRangoFechas() {
        Pageable pg = PageRequest.of(0, 10);
        LocalDate desde = LocalDate.of(2026, 8, 1);
        LocalDate hasta = LocalDate.of(2026, 8, 31);
        when(corteRepo.findAllByRangoFecha(desde, hasta, pg))
                .thenReturn(new PageImpl<>(List.of(sampleCorte(2L)), pg, 1));
        when(cajaRepo.findById(1)).thenReturn(Optional.of(sampleCaja(1, "Caja Central")));
        when(almacenRepo.findById(1)).thenReturn(Optional.of(
                Almacen.builder().almacenId(1).nombre("Almacen Central").build()));

        var result = service.listCortes(desde, hasta, pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).corteId()).isEqualTo(2L);
        verify(corteRepo).findAllByRangoFecha(desde, hasta, pg);
    }
}