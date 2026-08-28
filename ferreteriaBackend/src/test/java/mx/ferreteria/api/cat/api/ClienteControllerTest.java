package mx.ferreteria.api.cat.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import mx.ferreteria.api.cat.dto.CatDtos.ClienteRequest;
import mx.ferreteria.api.cat.dto.CatDtos.ClienteResponse;
import mx.ferreteria.api.cat.service.ClienteService;
import mx.ferreteria.api.common.error.DbErrorTranslator;

@WebMvcTest(controllers = ClienteController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ DbErrorTranslator.class, ClienteControllerTest.SliceConfig.class })
@MockBean({ mx.ferreteria.api.common.security.JwtAuthFilter.class,
                mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
                mx.ferreteria.api.common.security.JwtService.class })
class ClienteControllerTest {

        @Autowired
        MockMvc mvc;

        @MockBean
        ClienteService service;

        @org.springframework.boot.test.context.TestConfiguration
        static class SliceConfig {
                @org.springframework.context.annotation.Bean
                mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
                        return new mx.ferreteria.api.common.web.RequestIdProperties(
                                        mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
                }
        }

        private ClienteResponse sampleCliente() {
                return new ClienteResponse(1L, "FISICA", "Juan Perez", null,
                                "PEPJ800101ABC", "5512345678", "juan@test.com",
                                new BigDecimal("50000.00"), 30, false);
        }

        // ── GET /api/v1/clientes ────────────────────────────────────────

        @Test
        @DisplayName("GET /api/v1/clientes -> 200 con envelope {success, data, meta}")
        void list_returns200WithEnvelope() throws Exception {
                ClienteResponse r1 = sampleCliente();
                ClienteResponse r2 = new ClienteResponse(2L, "MORAL", "Empresa SA", null,
                                null, null, null, BigDecimal.ZERO, 0, true);
                when(service.list(eq(null), any()))
                                .thenReturn(new PageImpl<>(List.of(r1, r2), PageRequest.of(0, 20), 2));

                mvc.perform(get("/api/v1/clientes"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data").isArray())
                                .andExpect(jsonPath("$.data.length()").value(2))
                                .andExpect(jsonPath("$.meta.totalElements").value(2));
        }

        // ── GET /api/v1/clientes/{id} ──────────────────────────────────

        @Test
        @DisplayName("GET /api/v1/clientes/1 -> 200 con entidad")
        void getById_found() throws Exception {
                when(service.getById(1L)).thenReturn(sampleCliente());

                mvc.perform(get("/api/v1/clientes/1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.clienteId").value(1))
                                .andExpect(jsonPath("$.data.razonSocial").value("Juan Perez"));
        }

        // ── POST /api/v1/clientes ───────────────────────────────────────

        @Test
        @DisplayName("POST /api/v1/clientes valido -> 201 con entidad creada")
        void create_valid() throws Exception {
                when(service.create(any(ClienteRequest.class))).thenReturn(sampleCliente());

                mvc.perform(post("/api/v1/clientes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"razonSocial\":\"Juan Perez\",\"rfc\":\"PEPJ800101ABC\"}"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.razonSocial").value("Juan Perez"));
        }

        @Test
        @DisplayName("POST /api/v1/clientes razonSocial blank -> 400 CAMPO_REQUERIDO")
        void create_blankRazonSocial() throws Exception {
                mvc.perform(post("/api/v1/clientes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"razonSocial\":\"\"}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.success").value(false))
                                .andExpect(jsonPath("$.errorCode").value(400))
                                .andExpect(jsonPath("$.codigo").value("CAMPO_REQUERIDO"));
        }

        // ── PUT /api/v1/clientes/{id} ──────────────────────────────────

        @Test
        @DisplayName("PUT /api/v1/clientes/1 -> 200 con entidad actualizada")
        void update_found() throws Exception {
                when(service.update(eq(1L), any(ClienteRequest.class))).thenReturn(sampleCliente());

                mvc.perform(put("/api/v1/clientes/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"razonSocial\":\"Juan Perez\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.razonSocial").value("Juan Perez"));
        }

        // ── DELETE /api/v1/clientes/{id} ───────────────────────────────

        @Test
        @DisplayName("DELETE /api/v1/clientes/1 -> 204 No Content")
        void deactivate() throws Exception {
                doNothing().when(service).deactivate(1L);

                mvc.perform(delete("/api/v1/clientes/1"))
                                .andExpect(status().isNoContent());
        }
}
