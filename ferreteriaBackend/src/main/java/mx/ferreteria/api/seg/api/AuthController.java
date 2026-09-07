package mx.ferreteria.api.seg.api;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.security.web.csrf.CsrfToken;

import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.common.web.RateLimited;
import mx.ferreteria.api.seg.dto.AuthDtos.ChangePasswordRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.LoginRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.LogoutOk;
import mx.ferreteria.api.seg.dto.AuthDtos.MeResponse;
import mx.ferreteria.api.seg.dto.AuthDtos.PasswordOk;
import mx.ferreteria.api.seg.dto.AuthDtos.RefreshRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.RegisterRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.RegisterResponse;
import mx.ferreteria.api.seg.dto.AuthDtos.TokenResponse;
import mx.ferreteria.api.seg.service.AuthService;
import mx.ferreteria.api.seg.service.AuthService.LoginResult;
import mx.ferreteria.api.seg.service.RequestMeta;

/**
 * Autenticación M1: login, rotación de refresh, logout, registro público
 * (ENCARGADO_CAJA), cambio de password y perfil (PLAN §5/§6).
 *
 * <p>El refresh token se entrega vía cookie HttpOnly ({@code rt}) en login y
 * refresh; en logout se elimina con Max-Age=0. El access token sigue en el body
 * (TTL corto) para que JS lo envíe en {@code Authorization: Bearer}.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @RateLimited("auth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login OK; cookies `at` y `rt` emitidas"),
            @ApiResponse(responseCode = "401", description = "Credenciales invalidas (CREDENCIALES_INVALIDAS)"),
            @ApiResponse(responseCode = "423", description = "Cuenta bloqueada por intentos fallidos (CUENTA_BLOQUEADA)"),
            @ApiResponse(responseCode = "429", description = "Rate limit excedido (capacidad/minuto)")
    })
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req,
            HttpServletRequest http) {
        LoginResult result = authService.login(req, meta(http));
        return ResponseEntity.ok()
                .header("Set-Cookie",
                        authService.buildRefreshCookie(result.refreshRaw()).toString())
                .header("Set-Cookie",
                        authService.buildAccessCookie(result.body().accessToken()).toString())
                .body(result.body());
    }

    /**
     * Alta pública sin roles (usuario debe recibir rol por ADMINISTRADOR antes
     * de operar). Nunca asigna ADMIN: ver AuthService.register.
     * BACK-UI-001: responde 201 Created porque crea un recurso (ven.Usuarios /
     * seg.Usuarios). 200 OK se reservaría para el login.
     */
    @PostMapping("/register")
    @RateLimited("auth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario y empleado creados"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos (password corta, email duplicado)"),
            @ApiResponse(responseCode = "409", description = "Username/email duplicado (REGISTRO_DUPLICADO)"),
            @ApiResponse(responseCode = "429", description = "Rate limit excedido")
    })
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    /**
     * Cambio de password del usuario autenticado (requiere password actual).
     */
    @PostMapping("/change-password")
    public ResponseEntity<PasswordOk> changePassword(
            @Valid @RequestBody ChangePasswordRequest req, java.security.Principal principal) {
        Object source = principal instanceof org.springframework.security.core.Authentication auth
                ? auth.getPrincipal()
                : principal;
        if (source instanceof UserPrincipal up) {
            return ResponseEntity.ok(authService.changePassword(up, req));
        }
        return ResponseEntity.status(401).build();
    }

    @PostMapping("/refresh")
    @RateLimited("auth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nuevo par access/refresh emitido"),
            @ApiResponse(responseCode = "401", description = "Token invalido/expirado (TOKEN_EXPIRADO)"),
            @ApiResponse(responseCode = "429", description = "Rate limit excedido")
    })
    public ResponseEntity<TokenResponse> refresh(
            @Valid @RequestBody(required = false) RefreshRequest req,
            HttpServletRequest http) {
        LoginResult result = authService.refresh(
                req == null ? null : req.refreshToken(), meta(http), http);
        return ResponseEntity.ok()
                .header("Set-Cookie",
                        authService.buildRefreshCookie(result.refreshRaw()).toString())
                .header("Set-Cookie",
                        authService.buildAccessCookie(result.body().accessToken()).toString())
                .body(result.body());
    }

    @PostMapping("/logout")
    @RateLimited("auth")
    public ResponseEntity<LogoutOk> logout(
            @Valid @RequestBody(required = false) RefreshRequest req,
            HttpServletRequest http) {
        boolean ok = authService.logout(req == null ? null : req.refreshToken(), http);
        return ResponseEntity.ok()
                .header("Set-Cookie", authService.clearRefreshCookie().toString())
                .header("Set-Cookie", authService.clearAccessCookie().toString())
                .body(new LogoutOk(ok));
    }

    private mx.ferreteria.api.seg.service.RequestMeta meta(HttpServletRequest h) {
        // BACK-REND-024: XFF solo se respeta si hay un proxy de confianza. Ver
        // mx.ferreteria.api.common.web.RateLimitInterceptor.ipCliente. Por ahora
        // se aplica la misma regla fail-closed: getRemoteAddr() (IP real TCP).
        String ip = h.getRemoteAddr();
        return new RequestMeta(ip, h.getHeader("User-Agent"));
    }

    /**
     * Perfil del token actual: requiere Bearer válido (401 vía entry point si no).
     */
    /**
     * Acepta el Authentication del filtro (producción) o un Principal directo
     * (tests).
     */
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(java.security.Principal principal) {
        Object source = principal instanceof org.springframework.security.core.Authentication auth
                ? auth.getPrincipal()
                : principal;
        if (source instanceof UserPrincipal up) {
            return ResponseEntity.ok(authService.me(up));
        }
        return ResponseEntity.status(401).build();
    }

    /**
     * Emite la cookie {@code XSRF-TOKEN} (no HttpOnly, JS-readable) para que
     * el frontend SPA pueda hacer doble-submit en POST/PUT/PATCH/DELETE.
     * El frontend debe llamar este endpoint al montar la app, antes del
     * primer login. Idempotente: cada GET renueva el token.
     */
    @GetMapping("/csrf-init")
    public ResponseEntity<Void> csrfInit(HttpServletRequest req) {
        var token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            // Forzar la materialización del token: con
            // CsrfTokenRequestAttributeHandler la generación es perezosa y
            // leer getToken() dispara la escritura de la cookie en el response.
            token.getToken();
        }
        return ResponseEntity.noContent().build();
    }
}
