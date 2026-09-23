package mx.ferreteria.api.rh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.common.time.ZonaHoraria;
import mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoResumen;
import mx.ferreteria.api.rh.dto.RhDtos.GenerarQuincenaRequest;
import mx.ferreteria.api.rh.dto.RhDtos.NominaRequest;
import mx.ferreteria.api.rh.entity.Nomina;
import mx.ferreteria.api.rh.repo.EmpleadoRepository;
import mx.ferreteria.api.rh.repo.NominaRepository;
import mx.ferreteria.api.rh.service.EmpleadoGateway.EmpleadoSueldo;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NominaServiceTest {

    @Mock NominaRepository nominaRepo;
    @Mock EmpleadoRepository empleadoRepo;
    @Mock EmpleadoGateway empleadoGateway;
    @Mock org.springframework.context.ApplicationEventPublisher events;

    // NOTA: sin @InjectMocks a proposito. EmpleadoRepository implementa
    // EmpleadoGateway, asi que la inyeccion por constructor es ambigua y
    // Mockito cablea el mock del repo en el slot del gateway (los stubs del
    // gateway parecerian no tener efecto). Construccion explicita = determinista.
    NominaService service;

    @BeforeEach
    void setUp() {
        service = new NominaService(nominaRepo, empleadoRepo, empleadoGateway, events);
    }

    @AfterEach
    void limpiaSeguridad() {
        SecurityContextHolder.clearContext();
    }

    private static EmpleadoResumen resumen(int id, String nombre) {
        return new EmpleadoResumen(id, nombre, "Puesto", "a@b.com", "555", true, null);
    }

    private Nomina sampleNomina(Long id, String estado) {
        return Nomina.builder().nominaId(id).empleadoId(7)
                .periodoIni(LocalDate.of(2026, 1, 1))
                .periodoFin(LocalDate.of(2026, 1, 15))
                .diasPagados(new BigDecimal("15.0"))
                .percepciones(new BigDecimal("6000.00"))
                .deducciones(new BigDecimal("800.00"))
                .netoPagar(new BigDecimal("5200.00"))
                .estado(estado)
                .fechaPago("PAGADA".equals(estado)
                        ? java.time.Instant.parse("2026-01-16T10:00:00Z") : null)
                .usuarioRegistraId(1)
                .notas("Quincena 1").build();
    }

    private void autenticaComo(int usuarioId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(usuarioId, "gerente", 1, List.of("GERENTE")),
                        null, List.of()));
    }

    /** save() que asigna ids secuenciales y acumula lo guardado (para findAllById). */
    private Answer<Nomina> guardaConIds(AtomicLong seq, List<Nomina> guardadas) {
        return inv -> {
            Nomina n = inv.getArgument(0);
            n.setNominaId(seq.getAndIncrement());
            guardadas.add(n);
            return n;
        };
    }

    @Test
    @DisplayName("list: filtra por estado y enriquece nombre de empleado")
    void list_conEstado() {
        Pageable pg = PageRequest.of(0, 20);
        doReturn(new PageImpl<>(List.of(sampleNomina(1L, "PENDIENTE")), pg, 1))
                .when(nominaRepo).filtrar("PENDIENTE", null, null, pg);
        doReturn(Map.of(7, resumen(7, "Juan Perez")))
                .when(empleadoGateway).resumenByIds(any());
        doReturn(Map.of(7, resumen(7, "Juan Perez")))
                .when(empleadoRepo).resumenByIds(any());
        // fallback per-id also stubs for safety
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(anyInt());
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoRepo).resumenById(anyInt());
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(7);
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoRepo).resumenById(7);

        var result = service.list("PENDIENTE", null, null, pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).empleado()).isEqualTo("Juan Perez");
        assertThat(result.getContent().get(0).netoPagar()).isEqualByComparingTo("5200.00");
    }

    @Test
    @DisplayName("getById: nomina inexistente -> RecursoNoEncontradoException")
    void getById_notFound() {
        doReturn(Optional.empty()).when(nominaRepo).findById(99L);

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("create: empleado existe guarda nomina con neto calculado por BD")
    void create_ok() {
        Nomina saved = sampleNomina(10L, "PENDIENTE");
        doReturn(true).when(empleadoGateway).existsAndActivo(7);
        doReturn(true).when(empleadoGateway).existsAndActivo(7);
        doReturn(true).when(empleadoGateway).existsAndActivo(anyInt());
        doReturn(true).when(empleadoRepo).existsAndActivo(7);
        doReturn(true).when(empleadoRepo).existsAndActivo(7);
        doReturn(true).when(empleadoRepo).existsAndActivo(anyInt());
        doReturn(saved).when(nominaRepo).save(any(Nomina.class));
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(7);
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(7);
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(anyInt());
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoRepo).resumenById(7);
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoRepo).resumenById(7);
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoRepo).resumenById(anyInt());

        NominaRequest req = new NominaRequest(
                7, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15),
                new BigDecimal("15.0"), new BigDecimal("6000.00"),
                new BigDecimal("800.00"), "Quincena 1");

        var resp = service.create(req);

        assertThat(resp.nominaId()).isEqualTo(10L);
        assertThat(resp.estado()).isEqualTo("PENDIENTE");
        assertThat(resp.netoPagar()).isEqualByComparingTo("5200.00");
    }

    @Test
    @DisplayName("create: empleado inexistente -> RecursoNoEncontradoException")
    void create_empleadoInexistente() {
        doReturn(false).when(empleadoGateway).existsAndActivo(404);
        doReturn(false).when(empleadoGateway).existsAndActivo(404);
        doReturn(false).when(empleadoRepo).existsAndActivo(404);
        doReturn(false).when(empleadoRepo).existsAndActivo(404);

        NominaRequest req = new NominaRequest(
                404, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15),
                new BigDecimal("15.0"), new BigDecimal("6000.00"),
                new BigDecimal("800.00"), null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("marcarPagada: pasa a PAGADA con fecha")
    void marcarPagada_ok() {
        doReturn(Optional.of(sampleNomina(1L, "PENDIENTE"))).when(nominaRepo).findById(1L);
        doReturn(sampleNomina(1L, "PAGADA")).when(nominaRepo).save(any(Nomina.class));
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(7);
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(7);
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(anyInt());
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoRepo).resumenById(7);
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoRepo).resumenById(7);
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoRepo).resumenById(anyInt());

        var resp = service.marcarPagada(1L);

        assertThat(resp.estado()).isEqualTo("PAGADA");
        assertThat(resp.fechaPago()).isNotNull();
        // Aviso al módulo notif vía evento de dominio (sin depender de él)
        var cap = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(cap.capture());
        assertThat(cap.getValue()).isInstanceOfSatisfying(NominaPagadaEvent.class,
                e -> assertThat(e.nominaId()).isEqualTo(1L));
    }

    @Test
    @DisplayName("pagarLote: publica evento solo por cada nomina efectivamente pagada")
    void pagarLote_publicaEventos() {
        doReturn(Optional.of(sampleNomina(1L, "PENDIENTE"))).when(nominaRepo).findById(1L);
        doReturn(Optional.of(sampleNomina(2L, "PAGADA"))).when(nominaRepo).findById(2L);
        doReturn(Optional.empty()).when(nominaRepo).findById(3L);
        doReturn(sampleNomina(1L, "PAGADA")).when(nominaRepo).save(any(Nomina.class));
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(anyInt());

        var resp = service.pagarLote(new mx.ferreteria.api.rh.dto.RhDtos.PagarLoteRequest(List.of(1L, 2L, 3L)));

        assertThat(resp.pagadas()).isEqualTo(1);
        assertThat(resp.omitidas()).isEqualTo(2);
        var cap = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(cap.capture());
        assertThat(cap.getValue()).isInstanceOfSatisfying(NominaPagadaEvent.class,
                e -> assertThat(e.nominaId()).isEqualTo(1L));
    }

    @Test
    @DisplayName("marcarPagada: ya pagada -> ReglaNegocioException")
    void marcarPagada_yaPagada() {
        doReturn(Optional.of(sampleNomina(1L, "PAGADA"))).when(nominaRepo).findById(1L);

        assertThatThrownBy(() -> service.marcarPagada(1L))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("cancelar: desde PENDIENTE pasa a CANCELADA")
    void cancelar_ok() {
        doReturn(Optional.of(sampleNomina(1L, "PENDIENTE"))).when(nominaRepo).findById(1L);
        doReturn(sampleNomina(1L, "CANCELADA")).when(nominaRepo).save(any(Nomina.class));
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(7);
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(7);
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(anyInt());
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoRepo).resumenById(7);
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoRepo).resumenById(7);
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoRepo).resumenById(anyInt());

        var resp = service.cancelar(1L);

        assertThat(resp.estado()).isEqualTo("CANCELADA");
    }

    @Test
    @DisplayName("cancelar: ya pagada no puede cancelarse -> ReglaNegocioException")
    void cancelar_pagada() {
        doReturn(Optional.of(sampleNomina(1L, "PAGADA"))).when(nominaRepo).findById(1L);

        assertThatThrownBy(() -> service.cancelar(1L))
                .isInstanceOf(ReglaNegocioException.class);
    }

    // ── list: ramas faltantes ─────────────────────────────────────────

    @Test
    @DisplayName("list: pagina vacia retorna vacio sin consultar empleados")
    void list_vacia() {
        Pageable pg = PageRequest.of(0, 20);
        doReturn(new PageImpl<>(List.of(), pg, 0)).when(nominaRepo).filtrar(null, null, null, pg);

        var result = service.list(null, null, null, pg);

        assertThat(result.getContent()).isEmpty();
        verifyNoInteractions(empleadoGateway);
    }

    @Test
    @DisplayName("list: sin batch usa fallback por id")
    void list_fallbackPorId() {
        Pageable pg = PageRequest.of(0, 20);
        doReturn(new PageImpl<>(List.of(sampleNomina(1L, "PENDIENTE")), pg, 1))
                .when(nominaRepo).filtrar(null, null, null, pg);
        doReturn(Map.of()).when(empleadoGateway).resumenByIds(any());
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(anyInt());

        var result = service.list(null, null, null, pg);

        assertThat(result.getContent().get(0).empleado()).isEqualTo("Juan Perez");
    }

    @Test
    @DisplayName("list: empleado sin resumen deja nombre nulo")
    void list_nombreAusente() {
        Pageable pg = PageRequest.of(0, 20);
        doReturn(new PageImpl<>(List.of(sampleNomina(1L, "PENDIENTE")), pg, 1))
                .when(nominaRepo).filtrar(null, null, null, pg);
        doReturn(Map.of()).when(empleadoGateway).resumenByIds(any());
        doReturn(Optional.empty()).when(empleadoGateway).resumenById(anyInt());

        var result = service.list(null, null, null, pg);

        assertThat(result.getContent().get(0).empleado()).isNull();
    }

    // ── getById: rama encontrada ──────────────────────────────────────

    @Test
    @DisplayName("getById: encontrada retorna respuesta con nombre")
    void getById_ok() {
        doReturn(Optional.of(sampleNomina(1L, "PENDIENTE"))).when(nominaRepo).findById(1L);
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(7);

        var resp = service.getById(1L);

        assertThat(resp.nominaId()).isEqualTo(1L);
        assertThat(resp.empleado()).isEqualTo("Juan Perez");
    }

    @Test
    @DisplayName("getById: sin resumen de empleado retorna nombre nulo")
    void getById_sinResumen() {
        doReturn(Optional.of(sampleNomina(1L, "PENDIENTE"))).when(nominaRepo).findById(1L);

        var resp = service.getById(1L);

        assertThat(resp.empleado()).isNull();
    }

    @Test
    @DisplayName("getById: inexistente reporta RECURSO_NO_ENCONTRADO")
    void getById_codigoError() {
        doReturn(Optional.empty()).when(nominaRepo).findById(99L);

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOfSatisfying(RecursoNoEncontradoException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    // ── create: ramas faltantes ───────────────────────────────────────

    @Test
    @DisplayName("create: empleado inexistente reporta RECURSO_NO_ENCONTRADO")
    void create_codigoError() {
        doReturn(false).when(empleadoGateway).existsAndActivo(404);

        NominaRequest req = new NominaRequest(404, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 15), new BigDecimal("15.0"),
                new BigDecimal("6000.00"), new BigDecimal("800.00"), null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOfSatisfying(RecursoNoEncontradoException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    @Test
    @DisplayName("create: con usuario autenticado registra su id")
    void create_conUsuario() {
        autenticaComo(5);
        doReturn(true).when(empleadoGateway).existsAndActivo(7);
        doReturn(sampleNomina(10L, "PENDIENTE")).when(nominaRepo).save(any(Nomina.class));

        var resp = service.create(new NominaRequest(7, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 15), new BigDecimal("15.0"),
                new BigDecimal("6000.00"), new BigDecimal("800.00"), "n"));

        var cap = ArgumentCaptor.forClass(Nomina.class);
        verify(nominaRepo).save(cap.capture());
        assertThat(cap.getValue().getUsuarioRegistraId()).isEqualTo(5);
        assertThat(resp.nominaId()).isEqualTo(10L);
    }

    // ── generarQuincena: validaciones ─────────────────────────────────

    @Test
    @DisplayName("generarQuincena: quincena nula -> VALOR_INVALIDO")
    void generarQuincena_quincenaNula() {
        assertThatThrownBy(() -> service.generarQuincena(new GenerarQuincenaRequest(2026, 1, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALOR_INVALIDO));
    }

    @Test
    @DisplayName("generarQuincena: quincena distinta de PRIMERA/SEGUNDA -> VALOR_INVALIDO")
    void generarQuincena_quincenaInvalida() {
        assertThatThrownBy(() -> service.generarQuincena(new GenerarQuincenaRequest(2026, 1, "TERCERA")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALOR_INVALIDO));
    }

    @Test
    @DisplayName("generarQuincena: mes 13 -> VALOR_INVALIDO")
    void generarQuincena_mesInvalidoAlto() {
        assertThatThrownBy(() -> service.generarQuincena(new GenerarQuincenaRequest(2026, 13, "PRIMERA")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALOR_INVALIDO));
    }

    @Test
    @DisplayName("generarQuincena: mes 0 -> VALOR_INVALIDO")
    void generarQuincena_mesInvalidoCero() {
        assertThatThrownBy(() -> service.generarQuincena(new GenerarQuincenaRequest(2026, 0, "SEGUNDA")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALOR_INVALIDO));
    }

    @Test
    @DisplayName("generarQuincena: sin empleados activos -> VALOR_INVALIDO")
    void generarQuincena_sinEmpleados() {
        doReturn(List.of()).when(empleadoRepo).findActivosConSueldo();

        assertThatThrownBy(() -> service.generarQuincena(new GenerarQuincenaRequest(2026, 1, "PRIMERA")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALOR_INVALIDO));
    }

    // ── generarQuincena: caminos felices ──────────────────────────────

    @Test
    @DisplayName("generarQuincena: PRIMERA crea 1-15 con percepciones = sueldo*dias")
    void generarQuincena_primera() {
        List<Nomina> guardadas = new ArrayList<>();
        doAnswer(guardaConIds(new AtomicLong(100L), guardadas)).when(nominaRepo).save(any(Nomina.class));
        doReturn(List.of(new EmpleadoSueldo(7, new BigDecimal("400.00")),
                new EmpleadoSueldo(8, new BigDecimal("500.00"))))
                .when(empleadoRepo).findActivosConSueldo();
        doReturn(List.of()).when(nominaRepo).findEmpleadoIdsByPeriodo(any(), any(), any());
        doReturn(Map.of(7, resumen(7, "Juan Perez"), 8, resumen(8, "Ana Lopez")))
                .when(empleadoGateway).resumenByIds(any());
        doAnswer(inv -> new ArrayList<>(guardadas)).when(nominaRepo).findAllById(any());

        var resp = service.generarQuincena(new GenerarQuincenaRequest(2026, 2, "PRIMERA"));

        assertThat(resp.creadas()).isEqualTo(2);
        assertThat(resp.omitidas()).isZero();
        assertThat(resp.periodoIni()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(resp.periodoFin()).isEqualTo(LocalDate.of(2026, 2, 15));
        assertThat(guardadas.get(0).getPercepciones()).isEqualByComparingTo("6000.00");
        assertThat(guardadas.get(1).getPercepciones()).isEqualByComparingTo("7500.00");
        // sin autenticacion (SYSTEM id 0) se registra 1
        assertThat(guardadas).allSatisfy(n -> assertThat(n.getUsuarioRegistraId()).isEqualTo(1));
        assertThat(resp.nominas()).hasSize(2);
        assertThat(resp.nominas().get(0).empleado()).isEqualTo("Juan Perez");
        verify(nominaRepo).flush();
    }

    @Test
    @DisplayName("generarQuincena: acepta minusculas/espacios y registra usuario autenticado (SEGUNDA)")
    void generarQuincena_segundaNormalizada() {
        autenticaComo(5);
        List<Nomina> guardadas = new ArrayList<>();
        doAnswer(guardaConIds(new AtomicLong(200L), guardadas)).when(nominaRepo).save(any(Nomina.class));
        doReturn(List.of(new EmpleadoSueldo(7, new BigDecimal("400.00"))))
                .when(empleadoRepo).findActivosConSueldo();
        doReturn(List.of()).when(nominaRepo).findEmpleadoIdsByPeriodo(any(), any(), any());
        doReturn(Map.of(7, resumen(7, "Juan Perez"))).when(empleadoGateway).resumenByIds(any());
        doAnswer(inv -> new ArrayList<>(guardadas)).when(nominaRepo).findAllById(any());

        var resp = service.generarQuincena(new GenerarQuincenaRequest(2026, 2, "  segunda "));

        assertThat(resp.periodoIni()).isEqualTo(LocalDate.of(2026, 2, 16));
        assertThat(resp.periodoFin()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(guardadas.get(0).getDiasPagados()).isEqualByComparingTo("13");
        assertThat(guardadas.get(0).getPercepciones()).isEqualByComparingTo("5200.00");
        assertThat(guardadas.get(0).getUsuarioRegistraId()).isEqualTo(5);
        assertThat(resp.creadas()).isEqualTo(1);
    }

    @Test
    @DisplayName("generarQuincena: anio/mes nulos toman el dia actual")
    void generarQuincena_defaultsHoy() {
        List<Nomina> guardadas = new ArrayList<>();
        doAnswer(guardaConIds(new AtomicLong(300L), guardadas)).when(nominaRepo).save(any(Nomina.class));
        doReturn(List.of(new EmpleadoSueldo(7, new BigDecimal("400.00"))))
                .when(empleadoRepo).findActivosConSueldo();
        doReturn(List.of()).when(nominaRepo).findEmpleadoIdsByPeriodo(any(), any(), any());
        doReturn(Map.of(7, resumen(7, "Juan Perez"))).when(empleadoGateway).resumenByIds(any());
        doAnswer(inv -> new ArrayList<>(guardadas)).when(nominaRepo).findAllById(any());

        var resp = service.generarQuincena(new GenerarQuincenaRequest(null, null, "PRIMERA"));

        YearMonth ym = YearMonth.from(ZonaHoraria.hoy());
        assertThat(resp.periodoIni()).isEqualTo(ym.atDay(1));
        assertThat(resp.periodoFin()).isEqualTo(ym.atDay(15));
        assertThat(resp.creadas()).isEqualTo(1);
    }

    @Test
    @DisplayName("generarQuincena: duplicados existentes se omiten sin recargar")
    void generarQuincena_todosOmitidos() {
        doReturn(List.of(new EmpleadoSueldo(7, new BigDecimal("400.00")),
                new EmpleadoSueldo(8, new BigDecimal("500.00"))))
                .when(empleadoRepo).findActivosConSueldo();
        doReturn(List.of(7, 8)).when(nominaRepo).findEmpleadoIdsByPeriodo(any(), any(), any());

        var resp = service.generarQuincena(new GenerarQuincenaRequest(2026, 1, "PRIMERA"));

        assertThat(resp.creadas()).isZero();
        assertThat(resp.omitidas()).isEqualTo(2);
        assertThat(resp.nominas()).isEmpty();
        verify(nominaRepo, never()).save(any(Nomina.class));
    }

    @Test
    @DisplayName("generarQuincena: fallo al guardar cuenta como omitida")
    void generarQuincena_saveFalla() {
        List<Nomina> guardadas = new ArrayList<>();
        AtomicLong seq = new AtomicLong(400L);
        doAnswer((Answer<Nomina>) inv -> {
            Nomina n = inv.getArgument(0);
            if (n.getEmpleadoId() == 8) {
                throw new RuntimeException("falla inserto");
            }
            n.setNominaId(seq.getAndIncrement());
            guardadas.add(n);
            return n;
        }).when(nominaRepo).save(any(Nomina.class));
        doReturn(List.of(new EmpleadoSueldo(7, new BigDecimal("400.00")),
                new EmpleadoSueldo(8, new BigDecimal("500.00"))))
                .when(empleadoRepo).findActivosConSueldo();
        doReturn(List.of()).when(nominaRepo).findEmpleadoIdsByPeriodo(any(), any(), any());
        doReturn(Map.of(7, resumen(7, "Juan Perez"))).when(empleadoGateway).resumenByIds(any());
        doAnswer(inv -> new ArrayList<>(guardadas)).when(nominaRepo).findAllById(any());

        var resp = service.generarQuincena(new GenerarQuincenaRequest(2026, 1, "PRIMERA"));

        assertThat(resp.creadas()).isEqualTo(1);
        assertThat(resp.omitidas()).isEqualTo(1);
        assertThat(resp.nominas()).hasSize(1);
    }

    // ── pagarLote: ramas faltantes ────────────────────────────────────

    @Test
    @DisplayName("pagarLote: ids nulos -> CAMPO_REQUERIDO")
    void pagarLote_idsNulos() {
        assertThatThrownBy(() -> service.pagarLote(
                new mx.ferreteria.api.rh.dto.RhDtos.PagarLoteRequest(null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CAMPO_REQUERIDO));
    }

    @Test
    @DisplayName("pagarLote: ids vacios -> CAMPO_REQUERIDO")
    void pagarLote_idsVacios() {
        assertThatThrownBy(() -> service.pagarLote(
                new mx.ferreteria.api.rh.dto.RhDtos.PagarLoteRequest(List.of())))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CAMPO_REQUERIDO));
    }

    @Test
    @DisplayName("pagarLote: nada pagable retorna vacio sin eventos")
    void pagarLote_todoOmitido() {
        doReturn(Optional.empty()).when(nominaRepo).findById(3L);
        doReturn(Optional.of(sampleNomina(2L, "PAGADA"))).when(nominaRepo).findById(2L);

        var resp = service.pagarLote(
                new mx.ferreteria.api.rh.dto.RhDtos.PagarLoteRequest(List.of(2L, 3L)));

        assertThat(resp.pagadas()).isZero();
        assertThat(resp.omitidas()).isEqualTo(2);
        assertThat(resp.nominas()).isEmpty();
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("pagarLote: CANCELADA se omite")
    void pagarLote_canceladaOmitida() {
        doReturn(Optional.of(sampleNomina(9L, "CANCELADA"))).when(nominaRepo).findById(9L);

        var resp = service.pagarLote(
                new mx.ferreteria.api.rh.dto.RhDtos.PagarLoteRequest(List.of(9L)));

        assertThat(resp.pagadas()).isZero();
        assertThat(resp.omitidas()).isEqualTo(1);
        verify(events, never()).publishEvent(any());
    }

    // ── marcarPagada / cancelar: ramas faltantes ──────────────────────

    @Test
    @DisplayName("marcarPagada: inexistente -> RECURSO_NO_ENCONTRADO")
    void marcarPagada_noExiste() {
        doReturn(Optional.empty()).when(nominaRepo).findById(99L);

        assertThatThrownBy(() -> service.marcarPagada(99L))
                .isInstanceOfSatisfying(RecursoNoEncontradoException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    @Test
    @DisplayName("marcarPagada: ya pagada reporta REGISTRO_DUPLICADO")
    void marcarPagada_codigoDuplicado() {
        doReturn(Optional.of(sampleNomina(1L, "PAGADA"))).when(nominaRepo).findById(1L);

        assertThatThrownBy(() -> service.marcarPagada(1L))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.REGISTRO_DUPLICADO));
    }

    @Test
    @DisplayName("marcarPagada: cancelada -> VALOR_INVALIDO")
    void marcarPagada_cancelada() {
        doReturn(Optional.of(sampleNomina(1L, "CANCELADA"))).when(nominaRepo).findById(1L);

        assertThatThrownBy(() -> service.marcarPagada(1L))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALOR_INVALIDO));
    }

    @Test
    @DisplayName("cancelar: inexistente -> RECURSO_NO_ENCONTRADO")
    void cancelar_noExiste() {
        doReturn(Optional.empty()).when(nominaRepo).findById(99L);

        assertThatThrownBy(() -> service.cancelar(99L))
                .isInstanceOfSatisfying(RecursoNoEncontradoException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    @Test
    @DisplayName("cancelar: ya cancelada reporta REGISTRO_DUPLICADO")
    void cancelar_yaCancelada() {
        doReturn(Optional.of(sampleNomina(1L, "CANCELADA"))).when(nominaRepo).findById(1L);

        assertThatThrownBy(() -> service.cancelar(1L))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.REGISTRO_DUPLICADO));
    }

    @Test
    @DisplayName("cancelar: pagada reporta VALOR_INVALIDO")
    void cancelar_codigoPagada() {
        doReturn(Optional.of(sampleNomina(1L, "PAGADA"))).when(nominaRepo).findById(1L);

        assertThatThrownBy(() -> service.cancelar(1L))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALOR_INVALIDO));
    }

    // ── helpers paquete-privados ──────────────────────────────────────

    @Test
    @DisplayName("findExistingEmpleadoIds: nulo retorna vacio sin consultar")
    void findExisting_nulo() {
        assertThat(service.findExistingEmpleadoIds(LocalDate.now(), LocalDate.now(), null)).isEmpty();
        verifyNoInteractions(nominaRepo);
    }

    @Test
    @DisplayName("findExistingEmpleadoIds: vacio retorna vacio sin consultar")
    void findExisting_vacio() {
        assertThat(service.findExistingEmpleadoIds(LocalDate.now(), LocalDate.now(), List.of())).isEmpty();
        verifyNoInteractions(nominaRepo);
    }

    @Test
    @DisplayName("findExistingEmpleadoIds: mapea filas a conjunto")
    void findExisting_ok() {
        LocalDate ini = LocalDate.of(2026, 1, 1);
        LocalDate fin = LocalDate.of(2026, 1, 15);
        doReturn(List.of(7, 9)).when(nominaRepo).findEmpleadoIdsByPeriodo(ini, fin, List.of(7, 8, 9));

        assertThat(service.findExistingEmpleadoIds(ini, fin, List.of(7, 8, 9))).containsExactlyInAnyOrder(7, 9);
    }

    @Test
    @DisplayName("fetchNombresBatch: nulo retorna vacio")
    void fetchNombres_nulo() {
        assertThat(service.fetchNombresBatch(null)).isEmpty();
        verifyNoInteractions(empleadoGateway);
    }

    @Test
    @DisplayName("fetchNombresBatch: vacio retorna vacio")
    void fetchNombres_vacio() {
        assertThat(service.fetchNombresBatch(List.of())).isEmpty();
        verifyNoInteractions(empleadoGateway);
    }

    @Test
    @DisplayName("fetchNombresBatch: gateway nulo retorna vacio")
    void fetchNombres_gatewayNulo() {
        doReturn(null).when(empleadoGateway).resumenByIds(any());

        assertThat(service.fetchNombresBatch(List.of(7))).isEmpty();
    }

    @Test
    @DisplayName("fetchNombresBatch: gateway vacio retorna vacio")
    void fetchNombres_gatewayVacio() {
        doReturn(Map.of()).when(empleadoGateway).resumenByIds(any());

        assertThat(service.fetchNombresBatch(List.of(7))).isEmpty();
    }

    @Test
    @DisplayName("fetchNombresBatch: mapea id a nombre completo")
    void fetchNombres_ok() {
        doReturn(Map.of(7, resumen(7, "Juan Perez"))).when(empleadoGateway).resumenByIds(any());

        assertThat(service.fetchNombresBatch(List.of(7))).containsEntry(7, "Juan Perez");
    }
}
