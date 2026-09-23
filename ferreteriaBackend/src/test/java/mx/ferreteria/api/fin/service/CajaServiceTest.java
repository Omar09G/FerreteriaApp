package mx.ferreteria.api.fin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import mx.ferreteria.api.cat.entity.FormaPago;
import mx.ferreteria.api.cat.repo.FormaPagoRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.fin.dto.FinDtos.CajaRequest;
import mx.ferreteria.api.fin.dto.FinDtos.CorteRequest;
import mx.ferreteria.api.fin.dto.FinDtos.MovimientoCajaRequest;
import mx.ferreteria.api.fin.dto.FinDtos.TurnoAperturaRequest;
import mx.ferreteria.api.fin.entity.Caja;
import mx.ferreteria.api.fin.entity.CorteCaja;
import mx.ferreteria.api.fin.entity.MovimientoCaja;
import mx.ferreteria.api.fin.entity.TurnoCaja;
import mx.ferreteria.api.fin.repo.CajaReportRepository;
import mx.ferreteria.api.fin.repo.CajaRepository;
import mx.ferreteria.api.fin.repo.CorteCajaRepository;
import mx.ferreteria.api.fin.repo.MovimientoCajaRepository;
import mx.ferreteria.api.fin.repo.TurnoCajaRepository;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.repo.AlmacenRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CajaServiceTest {

    @Mock CajaRepository cajaRepo;
    @Mock TurnoCajaRepository turnoRepo;
    @Mock MovimientoCajaRepository movRepo;
    @Mock FormaPagoRepository formaPagoRepo;
    @Mock CorteCajaRepository corteRepo;
    @Mock AlmacenRepository almacenRepo;
    @Mock CajaReportRepository reportRepo;

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
        when(almacenRepo.findAllById(any())).thenReturn(List.of(
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
        doReturn("C-00000003").when(reportRepo).findFolioPagoProveedor(99L);

        var result = service.listMovimientos(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).formaPagoNombre()).isEqualTo("Transferencia SPEI");
        assertThat(result.get(0).refDescripcion()).isEqualTo("C-00000003");
    }

    @Test
    @DisplayName("obtenerEsperado: calcula apertura + entradas - salidas efectivo")
    void obtenerEsperado_computaEsperado() {
        when(turnoRepo.findById(1L)).thenReturn(Optional.of(sampleTurno(1L, "ABIERTO")));
        doReturn(new CajaService.ResumenTurnoRow(
                new BigDecimal("5000.00"), new BigDecimal("1160.00"), new BigDecimal("3500.00")))
                .when(reportRepo).findResumenTurno(1L);

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
        doReturn(1L).when(reportRepo).cerrarTurno(eq(1L), eq(new BigDecimal("6160.00")), eq(0), isNull());
        when(cajaRepo.findById(1)).thenReturn(Optional.of(sampleCaja(1, "Caja Central")));
        when(almacenRepo.findById(1)).thenReturn(Optional.of(
                Almacen.builder().almacenId(1).nombre("Almacen Central").build()));
        when(corteRepo.findById(1L)).thenReturn(Optional.of(sampleCorte(1L)));

        var resp = service.cerrarTurno(1L, new CorteRequest(new BigDecimal("6160.00"), null));

        assertThat(resp.corteId()).isEqualTo(1L);
        assertThat(resp.resultadoCaja()).isEqualTo("CUADRADO");
        verify(reportRepo).cerrarTurno(eq(1L), eq(new BigDecimal("6160.00")), eq(0), isNull());
    }

    @Test
    @DisplayName("cerrarTurno: corte no encontrado -> RecursoNoEncontradoException")
    void cerrarTurno_corteNotFound() {
        doReturn(99L).when(reportRepo).cerrarTurno(eq(1L), any(), any(), any());
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

    // ─── listCajas: ramas vacía / única / almacén faltante ──────────

    @Test
    @DisplayName("listCajas: sin cajas retorna lista vacía")
    void listCajas_empty() {
        when(cajaRepo.findByActivaTrue()).thenReturn(List.of());

        assertThat(service.listCajas()).isEmpty();
    }

    @Test
    @DisplayName("listCajas: una sola caja resuelve almacén por findById")
    void listCajas_single_conAlmacen() {
        when(cajaRepo.findByActivaTrue()).thenReturn(List.of(sampleCaja(1, "Caja Única")));
        when(almacenRepo.findById(1)).thenReturn(Optional.of(
                Almacen.builder().almacenId(1).nombre("Almacen Central").build()));

        var result = service.listCajas();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).almacenNombre()).isEqualTo("Almacen Central");
    }

    @Test
    @DisplayName("listCajas: una sola caja con almacén inexistente -> nombre null")
    void listCajas_single_sinAlmacen() {
        when(cajaRepo.findByActivaTrue()).thenReturn(List.of(sampleCaja(1, "Caja Única")));
        when(almacenRepo.findById(1)).thenReturn(Optional.empty());

        var result = service.listCajas();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).almacenNombre()).isNull();
    }

    @Test
    @DisplayName("listCajas: varias cajas, una sin almacén -> nombre null solo en esa")
    void listCajas_multi_conAlmacenFaltante() {
        Caja sinAlmacen = Caja.builder().cajaId(9).nombre("Caja Huérfana")
                .almacenId(99).activa(true).build();
        when(cajaRepo.findByActivaTrue()).thenReturn(List.of(sampleCaja(1, "Caja Central"), sinAlmacen));
        when(almacenRepo.findAllById(any())).thenReturn(List.of(
                Almacen.builder().almacenId(1).nombre("Almacen Central").build()));

        var result = service.listCajas();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).almacenNombre()).isEqualTo("Almacen Central");
        assertThat(result.get(1).almacenNombre()).isNull();
    }

    // ─── crearCaja ──────────────────────────────────────────────────

    @Test
    @DisplayName("crearCaja: nombre duplicado -> VALOR_DUPLICADO")
    void crearCaja_duplicado() {
        when(cajaRepo.findByNombre("Caja Central")).thenReturn(Optional.of(sampleCaja(1, "Caja Central")));

        assertThatThrownBy(() -> service.crearCaja(new CajaRequest("Caja Central", 1, true)))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(e -> assertThat(((ReglaNegocioException) e).errorCode())
                        .isEqualTo(ErrorCode.VALOR_DUPLICADO));
    }

    @Test
    @DisplayName("crearCaja: nombre libre guarda y resuelve almacén")
    void crearCaja_ok() {
        when(cajaRepo.findByNombre("Caja Sur")).thenReturn(Optional.empty());
        when(cajaRepo.save(any(Caja.class))).thenReturn(
                Caja.builder().cajaId(3).nombre("Caja Sur").almacenId(1).activa(true).build());
        when(almacenRepo.findById(1)).thenReturn(Optional.of(
                Almacen.builder().almacenId(1).nombre("Almacen Central").build()));

        var resp = service.crearCaja(new CajaRequest("Caja Sur", 1, true));

        assertThat(resp.cajaId()).isEqualTo(3);
        assertThat(resp.almacenNombre()).isEqualTo("Almacen Central");
        verify(cajaRepo).save(any(Caja.class));
    }

    // ─── actualizarCaja ─────────────────────────────────────────────

    @Test
    @DisplayName("actualizarCaja: inexistente -> RECURSO_NO_ENCONTRADO")
    void actualizarCaja_notFound() {
        when(cajaRepo.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizarCaja(999, new CajaRequest("X", 1, true)))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNoEncontradoException) e).errorCode())
                        .isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    @Test
    @DisplayName("actualizarCaja: renombre a nombre ocupado -> VALOR_DUPLICADO")
    void actualizarCaja_renombreDuplicado() {
        when(cajaRepo.findById(1)).thenReturn(Optional.of(sampleCaja(1, "Vieja")));
        when(cajaRepo.findByNombre("Ocupado")).thenReturn(Optional.of(sampleCaja(2, "Ocupado")));

        assertThatThrownBy(() -> service.actualizarCaja(1, new CajaRequest("Ocupado", 1, true)))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(e -> assertThat(((ReglaNegocioException) e).errorCode())
                        .isEqualTo(ErrorCode.VALOR_DUPLICADO));
    }

    @Test
    @DisplayName("actualizarCaja: mismo nombre no valida duplicado y guarda")
    void actualizarCaja_mismoNombre_ok() {
        when(cajaRepo.findById(1)).thenReturn(Optional.of(sampleCaja(1, "Caja Central")));
        when(cajaRepo.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));
        when(almacenRepo.findById(1)).thenReturn(Optional.empty());

        var resp = service.actualizarCaja(1, new CajaRequest("Caja Central", 1, false));

        assertThat(resp.nombre()).isEqualTo("Caja Central");
        assertThat(resp.activa()).isFalse();
        verify(cajaRepo).save(any(Caja.class));
    }

    @Test
    @DisplayName("actualizarCaja: renombre a nombre libre guarda")
    void actualizarCaja_renombreLibre_ok() {
        when(cajaRepo.findById(1)).thenReturn(Optional.of(sampleCaja(1, "Vieja")));
        when(cajaRepo.findByNombre("Nueva")).thenReturn(Optional.empty());
        when(cajaRepo.save(any(Caja.class))).thenReturn(sampleCaja(1, "Nueva"));
        when(almacenRepo.findById(1)).thenReturn(Optional.empty());

        var resp = service.actualizarCaja(1, new CajaRequest("Nueva", 1, true));

        assertThat(resp.nombre()).isEqualTo("Nueva");
    }

    // ─── actualizarCajaEstado ───────────────────────────────────────

    @Test
    @DisplayName("actualizarCajaEstado: inexistente -> RECURSO_NO_ENCONTRADO")
    void actualizarCajaEstado_notFound() {
        when(cajaRepo.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizarCajaEstado(999, new CajaRequest("X", 1, false)))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNoEncontradoException) e).errorCode())
                        .isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    @Test
    @DisplayName("actualizarCajaEstado: solo cambia activa y guarda")
    void actualizarCajaEstado_ok() {
        Caja caja = sampleCaja(1, "Caja Central");
        when(cajaRepo.findById(1)).thenReturn(Optional.of(caja));
        when(cajaRepo.save(any(Caja.class))).thenReturn(caja);
        when(almacenRepo.findById(1)).thenReturn(Optional.empty());

        var resp = service.actualizarCajaEstado(1, new CajaRequest("Otro Nombre", 2, false));

        assertThat(resp.activa()).isFalse();
        assertThat(caja.getNombre()).isEqualTo("Caja Central");
        assertThat(caja.getAlmacenId()).isEqualTo(1);
    }

    // ─── getCerradoTurno ────────────────────────────────────────────

    @Test
    @DisplayName("getCerradoTurno: turno inexistente -> RECURSO_NO_ENCONTRADO")
    void getCerradoTurno_notFound() {
        when(turnoRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCerradoTurno(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNoEncontradoException) e).errorCode())
                        .isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    @Test
    @DisplayName("getCerradoTurno: retorna turno con nombre de caja")
    void getCerradoTurno_ok() {
        when(turnoRepo.findById(5L)).thenReturn(Optional.of(sampleTurno(5L, "CERRADO")));
        when(cajaRepo.findById(1)).thenReturn(Optional.of(sampleCaja(1, "Caja Central")));

        var resp = service.getCerradoTurno(5L);

        assertThat(resp.turnoCajaId()).isEqualTo(5L);
        assertThat(resp.cajaNombre()).isEqualTo("Caja Central");
        assertThat(resp.estado()).isEqualTo("CERRADO");
    }

    @Test
    @DisplayName("getCerradoTurno: caja eliminada -> nombre null")
    void getCerradoTurno_cajaFaltante() {
        when(turnoRepo.findById(5L)).thenReturn(Optional.of(sampleTurno(5L, "CERRADO")));
        when(cajaRepo.findById(1)).thenReturn(Optional.empty());

        var resp = service.getCerradoTurno(5L);

        assertThat(resp.cajaNombre()).isNull();
    }

    // ─── listTurnos ─────────────────────────────────────────────────

    @Test
    @DisplayName("listTurnos: página vacía retorna vacía")
    void listTurnos_empty() {
        Pageable pg = PageRequest.of(0, 10);
        when(turnoRepo.findByCajaIdOrderByAperturaEnDesc(1, pg))
                .thenReturn(new PageImpl<>(List.of(), pg, 0));

        var result = service.listTurnos(1, pg);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("listTurnos: un turno resuelve caja por findById")
    void listTurnos_single_conCaja() {
        Pageable pg = PageRequest.of(0, 10);
        when(turnoRepo.findByCajaIdOrderByAperturaEnDesc(1, pg))
                .thenReturn(new PageImpl<>(List.of(sampleTurno(1L, "ABIERTO")), pg, 1));
        when(cajaRepo.findById(1)).thenReturn(Optional.of(sampleCaja(1, "Caja Central")));

        var result = service.listTurnos(1, pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).cajaNombre()).isEqualTo("Caja Central");
    }

    @Test
    @DisplayName("listTurnos: un turno con caja inexistente -> nombre null")
    void listTurnos_single_sinCaja() {
        Pageable pg = PageRequest.of(0, 10);
        when(turnoRepo.findByCajaIdOrderByAperturaEnDesc(1, pg))
                .thenReturn(new PageImpl<>(List.of(sampleTurno(1L, "ABIERTO")), pg, 1));
        when(cajaRepo.findById(1)).thenReturn(Optional.empty());

        var result = service.listTurnos(1, pg);

        assertThat(result.getContent().get(0).cajaNombre()).isNull();
    }

    @Test
    @DisplayName("listTurnos: varios turnos resuelven cajas en lote, faltante -> null")
    void listTurnos_multi_conCajaFaltante() {
        Pageable pg = PageRequest.of(0, 10);
        TurnoCaja t2 = TurnoCaja.builder().turnoCajaId(2L).cajaId(2).usuarioId(1)
                .aperturaEn(Instant.now()).montoApertura(new BigDecimal("1000.00"))
                .estado("CERRADO").build();
        when(turnoRepo.findByCajaIdOrderByAperturaEnDesc(1, pg))
                .thenReturn(new PageImpl<>(List.of(sampleTurno(1L, "ABIERTO"), t2), pg, 2));
        when(cajaRepo.findAllById(any())).thenReturn(List.of(sampleCaja(1, "Caja Central")));

        var result = service.listTurnos(1, pg);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).cajaNombre()).isEqualTo("Caja Central");
        assertThat(result.getContent().get(1).cajaNombre()).isNull();
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    // ─── getTurnoActual ─────────────────────────────────────────────

    @Test
    @DisplayName("getTurnoActual: caja inexistente -> RECURSO_NO_ENCONTRADO")
    void getTurnoActual_cajaNotFound() {
        when(cajaRepo.existsById(999)).thenReturn(false);

        assertThatThrownBy(() -> service.getTurnoActual(999))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNoEncontradoException) e).errorCode())
                        .isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    @Test
    @DisplayName("getTurnoActual: sin turno abierto -> RECURSO_NO_ENCONTRADO")
    void getTurnoActual_sinTurnoAbierto() {
        when(cajaRepo.existsById(1)).thenReturn(true);
        when(turnoRepo.findByCajaIdAndEstado(1, "ABIERTO")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTurnoActual(1))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNoEncontradoException) e).errorCode())
                        .isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    @Test
    @DisplayName("getTurnoActual: retorna turno abierto con nombre de caja")
    void getTurnoActual_ok() {
        when(cajaRepo.existsById(1)).thenReturn(true);
        when(turnoRepo.findByCajaIdAndEstado(1, "ABIERTO"))
                .thenReturn(Optional.of(sampleTurno(11L, "ABIERTO")));
        when(cajaRepo.findById(1)).thenReturn(Optional.of(sampleCaja(1, "Caja Central")));

        var resp = service.getTurnoActual(1);

        assertThat(resp.turnoCajaId()).isEqualTo(11L);
        assertThat(resp.cajaNombre()).isEqualTo("Caja Central");
    }

    // ─── turnoAbierto ───────────────────────────────────────────────

    @Test
    @DisplayName("turnoAbierto: null -> false sin tocar BD")
    void turnoAbierto_null() {
        assertThat(service.turnoAbierto(null)).isFalse();
    }

    @Test
    @DisplayName("turnoAbierto: turno inexistente -> false")
    void turnoAbierto_notFound() {
        when(turnoRepo.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.turnoAbierto(99L)).isFalse();
    }

    @Test
    @DisplayName("turnoAbierto: estado ABIERTO -> true")
    void turnoAbierto_abierto() {
        when(turnoRepo.findById(1L)).thenReturn(Optional.of(sampleTurno(1L, "ABIERTO")));

        assertThat(service.turnoAbierto(1L)).isTrue();
    }

    @Test
    @DisplayName("turnoAbierto: estado CERRADO -> false")
    void turnoAbierto_cerrado() {
        when(turnoRepo.findById(2L)).thenReturn(Optional.of(sampleTurno(2L, "CERRADO")));

        assertThat(service.turnoAbierto(2L)).isFalse();
    }

    // ─── resolverTurnoAbierto ───────────────────────────────────────

    @Test
    @DisplayName("resolverTurnoAbierto: cajaId null -> null")
    void resolverTurnoAbierto_null() {
        assertThat(service.resolverTurnoAbierto(null, 1)).isNull();
    }

    @Test
    @DisplayName("resolverTurnoAbierto: sin turno abierto -> TURNO_NO_ABIERTO")
    void resolverTurnoAbierto_sinTurno() {
        when(turnoRepo.findByCajaIdAndEstado(5, "ABIERTO")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolverTurnoAbierto(5, 1))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(e -> assertThat(((ReglaNegocioException) e).errorCode())
                        .isEqualTo(ErrorCode.TURNO_NO_ABIERTO));
    }

    @Test
    @DisplayName("resolverTurnoAbierto: caja inexistente -> RECURSO_NO_ENCONTRADO")
    void resolverTurnoAbierto_cajaNotFound() {
        when(turnoRepo.findByCajaIdAndEstado(5, "ABIERTO"))
                .thenReturn(Optional.of(sampleTurno(7L, "ABIERTO")));
        when(cajaRepo.findById(5)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolverTurnoAbierto(5, 1))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNoEncontradoException) e).errorCode())
                        .isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    @Test
    @DisplayName("resolverTurnoAbierto: almacén distinto -> CAJA_ALMACEN_INCOMPATIBLE")
    void resolverTurnoAbierto_almacenIncompatible() {
        TurnoCaja turno = TurnoCaja.builder().turnoCajaId(7L).cajaId(5).usuarioId(1)
                .aperturaEn(Instant.now()).montoApertura(BigDecimal.ZERO).estado("ABIERTO").build();
        Caja cajaOtroAlmacen = Caja.builder().cajaId(5).nombre("Caja 5").almacenId(2).activa(true).build();
        when(turnoRepo.findByCajaIdAndEstado(5, "ABIERTO")).thenReturn(Optional.of(turno));
        when(cajaRepo.findById(5)).thenReturn(Optional.of(cajaOtroAlmacen));

        assertThatThrownBy(() -> service.resolverTurnoAbierto(5, 1))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(e -> assertThat(((ReglaNegocioException) e).errorCode())
                        .isEqualTo(ErrorCode.CAJA_ALMACEN_INCOMPATIBLE));
    }

    @Test
    @DisplayName("resolverTurnoAbierto: mismo almacén retorna id del turno")
    void resolverTurnoAbierto_ok() {
        TurnoCaja turno = TurnoCaja.builder().turnoCajaId(7L).cajaId(5).usuarioId(1)
                .aperturaEn(Instant.now()).montoApertura(BigDecimal.ZERO).estado("ABIERTO").build();
        Caja caja = Caja.builder().cajaId(5).nombre("Caja 5").almacenId(1).activa(true).build();
        when(turnoRepo.findByCajaIdAndEstado(5, "ABIERTO")).thenReturn(Optional.of(turno));
        when(cajaRepo.findById(5)).thenReturn(Optional.of(caja));

        assertThat(service.resolverTurnoAbierto(5, 1)).isEqualTo(7L);
    }

    @Test
    @DisplayName("resolverTurnoAbierto: caja sin almacén no valida compatibilidad")
    void resolverTurnoAbierto_cajaSinAlmacen_ok() {
        TurnoCaja turno = TurnoCaja.builder().turnoCajaId(8L).cajaId(6).usuarioId(1)
                .aperturaEn(Instant.now()).montoApertura(BigDecimal.ZERO).estado("ABIERTO").build();
        Caja caja = Caja.builder().cajaId(6).nombre("Caja 6").almacenId(null).activa(true).build();
        when(turnoRepo.findByCajaIdAndEstado(6, "ABIERTO")).thenReturn(Optional.of(turno));
        when(cajaRepo.findById(6)).thenReturn(Optional.of(caja));

        assertThat(service.resolverTurnoAbierto(6, 99)).isEqualTo(8L);
    }

    // ─── registrarMovimiento: ramas faltantes ───────────────────────

    @Test
    @DisplayName("registrarMovimiento: turno inexistente -> RECURSO_NO_ENCONTRADO")
    void registrarMovimiento_turnoNotFound() {
        when(turnoRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarMovimiento(99L,
                new MovimientoCajaRequest("SALIDA", "GASTO_OPERATIVO",
                        new BigDecimal("100.00"), null, null, null)))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNoEncontradoException) e).errorCode())
                        .isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    @Test
    @DisplayName("registrarMovimiento: enriquece forma de pago y folio proveedor")
    void registrarMovimiento_conFormaYFolio() {
        when(turnoRepo.findById(1L)).thenReturn(Optional.of(sampleTurno(1L, "ABIERTO")));
        MovimientoCaja saved = MovimientoCaja.builder().movimientoId(6L).turnoCajaId(1L)
                .tipo("SALIDA").concepto("GASTO_OPERATIVO")
                .monto(new BigDecimal("3500.00"))
                .formaPagoId(4).refTabla("com.pagos_proveedor").refId(99L)
                .creadoEn(Instant.now()).build();
        when(movRepo.save(any(MovimientoCaja.class))).thenReturn(saved);
        when(formaPagoRepo.findById(4)).thenReturn(Optional.of(
                FormaPago.builder().formaPagoId(4).nombre("Transferencia SPEI").build()));
        doReturn("C-00000003").when(reportRepo).findFolioPagoProveedor(99L);

        var resp = service.registrarMovimiento(1L,
                new MovimientoCajaRequest("SALIDA", "GASTO_OPERATIVO",
                        new BigDecimal("3500.00"), 4, "com.pagos_proveedor", 99L));

        assertThat(resp.formaPagoNombre()).isEqualTo("Transferencia SPEI");
        assertThat(resp.refDescripcion()).isEqualTo("C-00000003");
    }

    @Test
    @DisplayName("registrarMovimiento: forma inexistente y ref ajena -> nulls sin folio")
    void registrarMovimiento_formaFaltanteYRefAjena() {
        when(turnoRepo.findById(1L)).thenReturn(Optional.of(sampleTurno(1L, "ABIERTO")));
        MovimientoCaja saved = MovimientoCaja.builder().movimientoId(7L).turnoCajaId(1L)
                .tipo("ENTRADA").concepto("OTRO_INGRESO")
                .monto(new BigDecimal("50.00"))
                .formaPagoId(77).refTabla("com.pagos_proveedor").refId(null)
                .creadoEn(Instant.now()).build();
        when(movRepo.save(any(MovimientoCaja.class))).thenReturn(saved);
        when(formaPagoRepo.findById(77)).thenReturn(Optional.empty());

        var resp = service.registrarMovimiento(1L,
                new MovimientoCajaRequest("ENTRADA", "OTRO_INGRESO",
                        new BigDecimal("50.00"), 77, "com.pagos_proveedor", null));

        assertThat(resp.formaPagoNombre()).isNull();
        assertThat(resp.refDescripcion()).isNull();
    }

    // ─── listMovimientos: ramas faltantes ───────────────────────────

    @Test
    @DisplayName("listMovimientos: sin movimientos retorna vacía")
    void listMovimientos_empty() {
        when(movRepo.findByTurnoCajaIdOrderByCreadoEnAsc(1L)).thenReturn(List.of());

        assertThat(service.listMovimientos(1L)).isEmpty();
    }

    @Test
    @DisplayName("listMovimientos: lote mezcla formas conocidas/faltantes y refs")
    void listMovimientos_multi_enriquecido() {
        MovimientoCaja conTodo = MovimientoCaja.builder().movimientoId(1L).turnoCajaId(1L)
                .tipo("SALIDA").concepto("GASTO_OPERATIVO").monto(new BigDecimal("3500.00"))
                .formaPagoId(4).refTabla("com.pagos_proveedor").refId(99L)
                .creadoEn(Instant.now()).build();
        MovimientoCaja formaFaltanteSinRef = MovimientoCaja.builder().movimientoId(2L).turnoCajaId(1L)
                .tipo("SALIDA").concepto("GASTO_OPERATIVO").monto(new BigDecimal("10.00"))
                .formaPagoId(9).refTabla("com.pagos_proveedor").refId(null)
                .creadoEn(Instant.now()).build();
        MovimientoCaja sinFormaNiRef = MovimientoCaja.builder().movimientoId(3L).turnoCajaId(1L)
                .tipo("ENTRADA").concepto("OTRO_INGRESO").monto(new BigDecimal("20.00"))
                .formaPagoId(null).refTabla(null).refId(null)
                .creadoEn(Instant.now()).build();
        when(movRepo.findByTurnoCajaIdOrderByCreadoEnAsc(1L))
                .thenReturn(List.of(conTodo, formaFaltanteSinRef, sinFormaNiRef));
        when(formaPagoRepo.findAllById(any())).thenReturn(List.of(
                FormaPago.builder().formaPagoId(4).nombre("Efectivo").build()));
        doReturn("C-00000003").when(reportRepo).findFolioPagoProveedor(99L);

        var result = service.listMovimientos(1L);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).formaPagoNombre()).isEqualTo("Efectivo");
        assertThat(result.get(0).refDescripcion()).isEqualTo("C-00000003");
        assertThat(result.get(1).formaPagoNombre()).isNull();
        assertThat(result.get(1).refDescripcion()).isNull();
        assertThat(result.get(2).formaPagoNombre()).isNull();
        assertThat(result.get(2).refDescripcion()).isNull();
    }

    // ─── cerrarTurno: SOBRANTE / FALTANTE ───────────────────────────

    private CorteCaja corteConDiferencia(Long id, BigDecimal diferencia) {
        return CorteCaja.builder().corteId(id).turnoCajaId(1L).cajaId(1).almacenId(1)
                .usuarioId(1).usuarioCierreId(1).fecha(LocalDate.now())
                .aperturaEn(Instant.now()).cierreEn(Instant.now())
                .subtotal(new BigDecimal("1000.00")).iva(new BigDecimal("160.00"))
                .descuentos(BigDecimal.ZERO)
                .totalVendido(new BigDecimal("1160.00")).costoVentas(new BigDecimal("600.00"))
                .fondoApertura(new BigDecimal("5000.00"))
                .entradasEfectivo(new BigDecimal("1160.00")).salidasEfectivo(BigDecimal.ZERO)
                .dineroEsperado(new BigDecimal("6160.00")).dineroContado(new BigDecimal("6170.00"))
                .diferencia(diferencia).build();
    }

    @Test
    @DisplayName("cerrarTurno: diferencia positiva -> SOBRANTE")
    void cerrarTurno_sobrante() {
        doReturn(7L).when(reportRepo).cerrarTurno(eq(1L), any(), any(), any());
        when(corteRepo.findById(7L)).thenReturn(
                Optional.of(corteConDiferencia(7L, new BigDecimal("10.00"))));
        when(cajaRepo.findById(1)).thenReturn(Optional.of(sampleCaja(1, "Caja Central")));
        when(almacenRepo.findById(1)).thenReturn(Optional.of(
                Almacen.builder().almacenId(1).nombre("Almacen Central").build()));

        var resp = service.cerrarTurno(1L, new CorteRequest(new BigDecimal("6170.00"), "cierre"));

        assertThat(resp.resultadoCaja()).isEqualTo("SOBRANTE");
    }

    @Test
    @DisplayName("cerrarTurno: diferencia negativa y catálogos faltantes -> FALTANTE con nulls")
    void cerrarTurno_faltanteSinNombres() {
        doReturn(8L).when(reportRepo).cerrarTurno(eq(1L), any(), any(), any());
        when(corteRepo.findById(8L)).thenReturn(
                Optional.of(corteConDiferencia(8L, new BigDecimal("-5.00"))));
        when(cajaRepo.findById(1)).thenReturn(Optional.empty());
        when(almacenRepo.findById(1)).thenReturn(Optional.empty());

        var resp = service.cerrarTurno(1L, new CorteRequest(new BigDecimal("6155.00"), null));

        assertThat(resp.resultadoCaja()).isEqualTo("FALTANTE");
        assertThat(resp.cajaNombre()).isNull();
        assertThat(resp.almacenNombre()).isNull();
    }

    // ─── listCortes: vacía / lote ───────────────────────────────────

    @Test
    @DisplayName("listCortes: página vacía retorna vacía")
    void listCortes_empty() {
        Pageable pg = PageRequest.of(0, 10);
        when(corteRepo.findAllByRangoFecha(isNull(), isNull(), eq(pg)))
                .thenReturn(new PageImpl<>(List.of(), pg, 0));

        var result = service.listCortes(null, null, pg);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("listCortes: lote mezcla SOBRANTE/FALTANTE y catálogos faltantes")
    void listCortes_multi() {
        Pageable pg = PageRequest.of(0, 10);
        CorteCaja sobrante = corteConDiferencia(10L, new BigDecimal("10.00"));
        CorteCaja faltante = CorteCaja.builder().corteId(11L).turnoCajaId(2L).cajaId(2).almacenId(2)
                .usuarioId(1).usuarioCierreId(1).fecha(LocalDate.now())
                .aperturaEn(Instant.now()).cierreEn(Instant.now())
                .subtotal(BigDecimal.ZERO).iva(BigDecimal.ZERO).descuentos(BigDecimal.ZERO)
                .totalVendido(BigDecimal.ZERO).costoVentas(BigDecimal.ZERO)
                .fondoApertura(BigDecimal.ZERO)
                .entradasEfectivo(BigDecimal.ZERO).salidasEfectivo(BigDecimal.ZERO)
                .dineroEsperado(BigDecimal.ZERO).dineroContado(new BigDecimal("-5.00"))
                .diferencia(new BigDecimal("-5.00")).build();
        CorteCaja cuadrado = corteConDiferencia(12L, BigDecimal.ZERO);
        when(corteRepo.findAllByRangoFecha(isNull(), isNull(), eq(pg)))
                .thenReturn(new PageImpl<>(List.of(sobrante, faltante, cuadrado), pg, 3));
        when(cajaRepo.findAllById(any())).thenReturn(List.of(sampleCaja(1, "Caja Central")));
        when(almacenRepo.findAllById(any())).thenReturn(List.of(
                Almacen.builder().almacenId(1).nombre("Almacen Central").build()));

        var result = service.listCortes(null, null, pg);

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).resultadoCaja()).isEqualTo("SOBRANTE");
        assertThat(result.getContent().get(0).cajaNombre()).isEqualTo("Caja Central");
        assertThat(result.getContent().get(0).almacenNombre()).isEqualTo("Almacen Central");
        assertThat(result.getContent().get(1).resultadoCaja()).isEqualTo("FALTANTE");
        assertThat(result.getContent().get(1).cajaNombre()).isNull();
        assertThat(result.getContent().get(1).almacenNombre()).isNull();
        assertThat(result.getContent().get(2).resultadoCaja()).isEqualTo("CUADRADO");
        assertThat(result.getTotalElements()).isEqualTo(3);
    }
}