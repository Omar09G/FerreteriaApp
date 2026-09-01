package mx.ferreteria.api.rh.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoOk;
import mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoResponse;
import mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoUpdateRequest;
import mx.ferreteria.api.rh.service.EmpleadoService;

/**
 * Contrato HTTP del CRUD de empleados. La autorización ADMINISTRADOR se
 * verifica en EmpleadoControllerSecurityTest (@PreAuthorize) y en los IT.
 */
@WebMvcTest(controllers = EmpleadoController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({mx.ferreteria.api.common.error.DbErrorTranslator.class,
        mx.ferreteria.api.common.web.WebMvcTestProps.class,
        EmpleadoControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class EmpleadoControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    EmpleadoService service;

    @TestConfiguration
    static class SliceConfig {
        @Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    private static final EmpleadoResponse E = new EmpleadoResponse(
            1, 3, "Vendedor", "Juan", "Pérez", "López", "CURP123", "NSS123", "555",
            "juan@x.mx", "Av 1", "Colonia", 1, "97000",
            LocalDate.of(2026, 1, 15), null, new BigDecimal("100.00"), true);

    @Test
    @DisplayName("GET /empleados -> 200 success:true, data arreglo + meta")
    void list_paginated() throws Exception {
        Mockito.when(service.list(any())).thenReturn(
                new PageImpl<>(List.of(E), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/empleados"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].empleadoId").value(1))
                .andExpect(jsonPath("$.data[0].puestoNombre").value("Vendedor"))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /empleados/{id} -> 200 con datos completos")
    void get_byId() throws Exception {
        Mockito.when(service.get(1)).thenReturn(E);

        mvc.perform(get("/api/v1/empleados/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nombre").value("Juan"))
                .andExpect(jsonPath("$.data.activo").value(true));
    }

    @Test
    @DisplayName("POST /empleados valido -> 200 crea empleado (+usuario si trae username) y devuelve registro")
    void create_valid() throws Exception {
        Mockito.when(service.create(any())).thenReturn(E);

        mvc.perform(post("/api/v1/empleados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"puestoId\":3,\"nombre\":\"Juan\",\"apellidoPaterno\":\"Pérez\","
                                + "\"telefono\":\"555\",\"email\":\"juan@x.mx\","
                                + "\"username\":\"juan.perez\",\"password\":\"Secreta123\","
                                + "\"roles\":[\"VENDEDOR\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.empleadoId").value(1));
        Mockito.verify(service).create(any());
    }

    @Test
    @DisplayName("POST /empleados sin nombre ni puesto -> 400 CAMPO_REQUERIDO")
    void create_invalid_badRequest() throws Exception {
        mvc.perform(post("/api/v1/empleados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.codigo").value("CAMPO_REQUERIDO"));
    }

    @Test
    @DisplayName("PATCH /empleados/{id} -> 200 actualiza con parche")
    void update_patch() throws Exception {
        Mockito.when(service.update(anyInt(), any(EmpleadoUpdateRequest.class))).thenReturn(E);

        mvc.perform(patch("/api/v1/empleados/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.empleadoId").value(1));
    }

    @Test
    @DisplayName("DELETE /empleados/{id} -> 200 soft-delete ok:true")
    void baja_ok() throws Exception {
        mvc.perform(delete("/api/v1/empleados/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ok").value(true));
        Mockito.verify(service).baja(1);
    }
}