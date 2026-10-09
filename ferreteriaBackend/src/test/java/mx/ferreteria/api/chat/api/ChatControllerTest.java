package mx.ferreteria.api.chat.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import mx.ferreteria.api.chat.dto.ChatDtos.ConversacionResponse;
import mx.ferreteria.api.chat.dto.ChatDtos.MensajeResponse;
import mx.ferreteria.api.chat.service.ChatService;
import mx.ferreteria.api.common.error.DbErrorTranslator;
import mx.ferreteria.api.common.web.WebMvcTestProps;

@WebMvcTest(controllers = ChatController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ DbErrorTranslator.class, WebMvcTestProps.class, ChatControllerTest.SliceConfig.class })
@MockitoBean(types = { mx.ferreteria.api.common.security.JwtAuthFilter.class,
                mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
                mx.ferreteria.api.common.security.JwtService.class })
class ChatControllerTest {

        @Autowired
        MockMvc mvc;

        @MockitoBean
        ChatService chatService;

        @org.springframework.boot.test.context.TestConfiguration
        static class SliceConfig {
                @org.springframework.context.annotation.Bean
                mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
                        return new mx.ferreteria.api.common.web.RequestIdProperties(
                                        mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
                }
        }

        @Test
        @DisplayName("GET /api/v1/chat/conversaciones -> 200 con lista")
        void conversaciones_returns200() throws Exception {
                when(chatService.listar(anyInt())).thenReturn(List.of(
                                new ConversacionResponse(1L, "DIRECTA", "cajero",
                                                List.of(), null, 0)));

                mvc.perform(get("/api/v1/chat/conversaciones"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data[0].titulo").value("cajero"));
        }

        @Test
        @DisplayName("POST /api/v1/chat/directas sin otroUsuarioId -> 400")
        void crearDirecta_sinBody400() throws Exception {
                mvc.perform(post("/api/v1/chat/directas")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET /api/v1/chat/{id}/mensajes -> 200 con página")
        void historial_returns200() throws Exception {
                when(chatService.historial(anyInt(), anyLong(), any()))
                                .thenReturn(new PageImpl<>(List.of(
                                                new MensajeResponse(1L, 2, "cajero", "Hola",
                                                                java.time.Instant.now())),
                                                PageRequest.of(0, 20), 1));

                mvc.perform(get("/api/v1/chat/1/mensajes"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data[0].cuerpo").value("Hola"))
                                .andExpect(jsonPath("$.meta.totalElements").value(1));
        }

        @Test
        @DisplayName("POST /api/v1/chat/{id}/mensajes con cuerpo vacío -> 400")
        void enviar_vacio400() throws Exception {
                mvc.perform(post("/api/v1/chat/1/mensajes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"cuerpo\":\"   \"}"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("DELETE /api/v1/chat/{id} -> 204")
        void salir_returns204() throws Exception {
                mvc.perform(delete("/api/v1/chat/1"))
                                .andExpect(status().isNoContent());
        }
}
