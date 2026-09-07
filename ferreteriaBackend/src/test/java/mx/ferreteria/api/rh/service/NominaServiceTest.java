package mx.ferreteria.api.rh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoResumen;
import mx.ferreteria.api.rh.dto.RhDtos.NominaRequest;
import mx.ferreteria.api.rh.entity.Nomina;
import mx.ferreteria.api.rh.repo.EmpleadoRepository;
import mx.ferreteria.api.rh.repo.NominaRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NominaServiceTest {

    @Mock NominaRepository nominaRepo;
    @Mock EmpleadoRepository empleadoRepo;
    @Mock EmpleadoGateway empleadoGateway;

    @InjectMocks
    NominaService service;

    private static EmpleadoResumen resumen(int id, String nombre) {
        return new EmpleadoResumen(id, nombre, "Puesto", "a@b.com", "555", true);
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
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(eq(7));
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoRepo).resumenById(eq(7));

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
        doReturn(true).when(empleadoGateway).existsAndActivo(eq(7));
        doReturn(true).when(empleadoGateway).existsAndActivo(anyInt());
        doReturn(true).when(empleadoRepo).existsAndActivo(7);
        doReturn(true).when(empleadoRepo).existsAndActivo(eq(7));
        doReturn(true).when(empleadoRepo).existsAndActivo(anyInt());
        doReturn(saved).when(nominaRepo).save(any(Nomina.class));
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(7);
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(eq(7));
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(anyInt());
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoRepo).resumenById(7);
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoRepo).resumenById(eq(7));
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
        doReturn(false).when(empleadoGateway).existsAndActivo(eq(404));
        doReturn(false).when(empleadoRepo).existsAndActivo(404);
        doReturn(false).when(empleadoRepo).existsAndActivo(eq(404));

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
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(eq(7));
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(anyInt());
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoRepo).resumenById(7);
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoRepo).resumenById(eq(7));
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoRepo).resumenById(anyInt());

        var resp = service.marcarPagada(1L);

        assertThat(resp.estado()).isEqualTo("PAGADA");
        assertThat(resp.fechaPago()).isNotNull();
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
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(eq(7));
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoGateway).resumenById(anyInt());
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoRepo).resumenById(7);
        doReturn(Optional.of(resumen(7, "Juan Perez"))).when(empleadoRepo).resumenById(eq(7));
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
}
