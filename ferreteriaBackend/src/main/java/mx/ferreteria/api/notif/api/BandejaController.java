package mx.ferreteria.api.notif.api;

import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.security.AuthCookieProperties;
import mx.ferreteria.api.common.security.JwtService;
import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.notif.dto.BandejaDtos.BandejaResponse;
import mx.ferreteria.api.notif.dto.BandejaDtos.EliminadasResponse;
import mx.ferreteria.api.notif.dto.BandejaDtos.NoLeidasResponse;
import mx.ferreteria.api.notif.service.BandejaService;
import mx.ferreteria.api.notif.service.RealtimePushService;

/**
 * Bandeja de notificaciones en tiempo real (SSE) del usuario autenticado.
 * El usuario siempre sale del principal (nunca de parámetros): nadie puede
 * leer ni marcar la bandeja de otro (sin IDOR).
 */
@RestController
@RequestMapping("/api/v1/notificaciones")
@RequiredArgsConstructor
@Validated
@PreAuthorize("isAuthenticated()")
public class BandejaController {

    private final BandejaService bandejaService;
    private final RealtimePushService pushService;
    private final JwtService jwtService;
    private final AuthCookieProperties cookieProps;

    /**
     * Stream de eventos (text/event-stream). El front reconecta solo con
     * EventSource y recupera lo perdido desde el historial. La suscripción
     * queda ligada a la sesión del refresh HttpOnly para poder excluirla de
     * su propio desplazamiento single-session.
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter stream(HttpServletRequest request) {
        return pushService.suscribir(UserPrincipal.actual().usuarioId(), sesionDe(request));
    }

    /**
     * Sesión del suscriptor (claim ses del refresh). Null si no hay cookie o
     * el refresh no parsea: ese emisor recibe todo (comportamiento previo).
     */
    private Integer sesionDe(HttpServletRequest request) {
        try {
            if (request.getCookies() == null) {
                return null;
            }
            for (var c : request.getCookies()) {
                if (cookieProps.refreshName().equals(c.getName())
                        && c.getValue() != null && !c.getValue().isBlank()) {
                    return jwtService.parseRefresh(c.getValue())
                            .get(JwtService.CLAIM_SES, Integer.class);
                }
            }
        } catch (RuntimeException ignored) {
            // refresh ilegible/expirado: suscripción sin sesión (recibe todo)
        }
        return null;
    }

    /** Historial paginado (?page=0&size=20), más recientes primero. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<BandejaResponse> listar(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        return bandejaService.listar(UserPrincipal.actual().usuarioId(),
                PageQuery.of(page, size, null).toPageable());
    }

    /** Contador para la campana del front. */
    @GetMapping("/no-leidas")
    @PreAuthorize("isAuthenticated()")
    public NoLeidasResponse noLeidas() {
        return bandejaService.noLeidas(UserPrincipal.actual().usuarioId());
    }

    @PatchMapping("/{id}/leida")
    @PreAuthorize("isAuthenticated()")
    public BandejaResponse marcarLeida(@PathVariable("id") Long id) {
        return bandejaService.marcarLeida(UserPrincipal.actual().usuarioId(), id);
    }

    @PatchMapping("/leidas")
    @PreAuthorize("isAuthenticated()")
    public NoLeidasResponse marcarTodasLeidas() {
        bandejaService.marcarTodasLeidas(UserPrincipal.actual().usuarioId());
        return new NoLeidasResponse(0);
    }

    /** Borra el historial ya leído del usuario autenticado. */
    @DeleteMapping("/leidas")
    @PreAuthorize("isAuthenticated()")
    public EliminadasResponse eliminarLeidas() {
        return new EliminadasResponse(
                bandejaService.eliminarLeidas(UserPrincipal.actual().usuarioId()));
    }
}
