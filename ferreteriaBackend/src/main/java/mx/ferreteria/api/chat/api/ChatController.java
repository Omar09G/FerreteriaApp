package mx.ferreteria.api.chat.api;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.chat.dto.ChatDtos.ConversacionResponse;
import mx.ferreteria.api.chat.dto.ChatDtos.CrearDirectaRequest;
import mx.ferreteria.api.chat.dto.ChatDtos.CrearGrupoRequest;
import mx.ferreteria.api.chat.dto.ChatDtos.EnviarMensajeRequest;
import mx.ferreteria.api.chat.dto.ChatDtos.MensajeResponse;
import mx.ferreteria.api.chat.service.ChatService;
import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.common.web.PageQuery;

import java.util.List;

/**
 * Chat interno 1 a 1 y por grupos. El autor siempre sale del principal
 * autenticado (nunca del body) y toda lectura exige pertenencia: nadie ve
 * conversaciones ajenas.
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Validated
@PreAuthorize("isAuthenticated()")
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/conversaciones")
    @PreAuthorize("isAuthenticated()")
    public List<ConversacionResponse> conversaciones() {
        return chatService.listar(UserPrincipal.actual().usuarioId());
    }

    @PostMapping("/directas")
    @PreAuthorize("isAuthenticated()")
    public ConversacionResponse crearDirecta(@Valid @RequestBody CrearDirectaRequest req) {
        return chatService.crearDirecta(UserPrincipal.actual().usuarioId(), req.otroUsuarioId());
    }

    @PostMapping("/grupos")
    @PreAuthorize("isAuthenticated()")
    public ConversacionResponse crearGrupo(@Valid @RequestBody CrearGrupoRequest req) {
        return chatService.crearGrupo(UserPrincipal.actual().usuarioId(),
                req.titulo(), req.miembroIds());
    }

    @GetMapping("/{id}/mensajes")
    @PreAuthorize("isAuthenticated()")
    public Page<MensajeResponse> historial(
            @PathVariable("id") Long id,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        return chatService.historial(UserPrincipal.actual().usuarioId(), id,
                PageQuery.of(page, size, null).toPageable());
    }

    @PostMapping("/{id}/mensajes")
    @PreAuthorize("isAuthenticated()")
    public MensajeResponse enviar(@PathVariable("id") Long id,
            @Valid @RequestBody EnviarMensajeRequest req) {
        UserPrincipal up = UserPrincipal.actual();
        return chatService.enviar(up.usuarioId(), up.username(), id, req.cuerpo());
    }

    @PatchMapping("/{id}/leida")
    @PreAuthorize("isAuthenticated()")
    public void marcarLeida(@PathVariable("id") Long id) {
        chatService.marcarLeida(UserPrincipal.actual().usuarioId(), id);
    }
}
