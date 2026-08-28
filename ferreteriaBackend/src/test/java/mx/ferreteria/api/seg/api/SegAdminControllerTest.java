package mx.ferreteria.api.seg.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

import mx.ferreteria.api.seg.dto.SegAdminDtos.PermisoResponse;
import mx.ferreteria.api.seg.dto.SegAdminDtos.RolResponse;
import mx.ferreteria.api.seg.dto.SegAdminDtos.UsuarioResponse;
import mx.ferreteria.api.seg.service.SegAdminService;

/**
 * Contrato HTTP del CRUD de seguridad. La autorización ADMINISTRADOR se
 * verifica en SegAdminControllerSecurityTest (@PreAuthorize) y en los IT.
 */
@WebMvcTest(controllers = SegAdminController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({mx.ferreteria.api.common.error.DbErrorTranslator.class,
        SegAdminControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class SegAdminControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    SegAdminService service;

    @TestConfiguration
    static class SliceConfig {
        @Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    private static final UsuarioResponse U =
            new UsuarioResponse(11, "cajero1", "cajero1@x.mx", 42, true,
                    List.of("VENDEDOR"), null, null,
                    Instant.parse("2026-01-01T12:00:00Z"));

    @Test
    @DisplayName("GET /usuarios -> 200 success:true, data arreglo + meta de pagina")
    void listUsuarios_paginated() throws Exception {
        Mockito.when(service.listUsuarios(any())).thenReturn(
                new PageImpl<>(List.of(U), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/usuarios")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].username").value("cajero1"))
                .andExpect(jsonPath("$.data[0].roles[0]").value("VENDEDOR"))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    @DisplayName("POST /usuarios crea y devuelve data del usuario (sin password en JSON)")
    void createUsuario_returnsData() throws Exception {
        Mockito.when(service.createUsuario(any())).thenReturn(U);

        mvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cajero1\",\"email\":\"cajero1@x.mx\","
                                + "\"password\":\"Secreta123\",\"roles\":[\"VENDEDOR\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usuarioId").value(11));
    }

    @Test
    @DisplayName("PATCH /usuarios/{id}/password resetea password")
    void resetPassword_ok() throws Exception {
        Mockito.doNothing().when(service).resetPassword(anyInt(), any());
        Mockito.when(service.getUsuario(11)).thenReturn(U);

        mvc.perform(patch("/api/v1/usuarios/11/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevaPassword\":\"NuevaClave99\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("cajero1"));
    }

    @Test
    @DisplayName("PUT /usuarios/{id}/roles asigna roles")
    void setRoles_ok() throws Exception {
        Mockito.when(service.setRoles(anyInt(), any())).thenReturn(U);

        mvc.perform(put("/api/v1/usuarios/11/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"VENDEDOR\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("VENDEDOR"));
    }

    @Test
    @DisplayName("DELETE /usuarios/{id} -> 200 con data.ok:true")
    void deleteUsuario_ok() throws Exception {
        Mockito.doNothing().when(service).deleteUsuario(anyInt());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/usuarios/77"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ok").value(true));
    }

    @Test
    @DisplayName("PUT /roles/{id}/permisos devuelve clave de permisos reemplazados")
    void setPermisos_ok() throws Exception {
        Mockito.when(service.setPermisos(anyInt(), any())).thenReturn(List.of("V.VENDER"));

        mvc.perform(put("/api/v1/roles/5/permisos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permisos\":[\"V.VENDER\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0]").value("V.VENDER"));
    }

    @Test
    @DisplayName("GET /roles/{id} y /permisos/{id} devuelven recursos + datos basicos")
    void getRolAndPermiso() throws Exception {
        Mockito.when(service.getRol(5)).thenReturn(
                new RolResponse(5, "SUPERVISOR", "Supervisor", null, true, List.of()));
        Mockito.when(service.getPermiso(1)).thenReturn(
                new PermisoResponse(1, "V.VENDER", "Registrar ventas"));

        mvc.perform(get("/api/v1/roles/5")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clave").value("SUPERVISOR"));
        mvc.perform(get("/api/v1/permisos/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clave").value("V.VENDER"));
    }

    @Test
    @DisplayName("POST /usuarios sin username -> 400 CAMPO_REQUERIDO (validacion bean)")
    void createUsuario_blankUsername_badRequest() throws Exception {
        mvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.mx\",\"password\":\"Secreta123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.codigo").value("CAMPO_REQUERIDO"));
    }
}