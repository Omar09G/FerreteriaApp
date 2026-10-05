package mx.ferreteria.api.seg.api;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
import mx.ferreteria.api.common.security.GoogleAuthProperties;
import mx.ferreteria.api.seg.dto.AuthDtos.ChangePasswordRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.GoogleInitResponse;
import mx.ferreteria.api.seg.dto.AuthDtos.LoginRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.LogoutOk;
import mx.ferreteria.api.seg.dto.AuthDtos.MeResponse;
import mx.ferreteria.api.seg.dto.AuthDtos.OtpChallengeResponse;
import mx.ferreteria.api.seg.dto.AuthDtos.OtpEnviarRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.OtpVerificarRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.PasswordOk;
import mx.ferreteria.api.seg.dto.AuthDtos.RefreshRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.RegisterRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.RegisterResponse;
import mx.ferreteria.api.seg.dto.AuthDtos.TokenResponse;
import mx.ferreteria.api.seg.service.AuthService;
import mx.ferreteria.api.seg.service.AuthService.LoginResult;
import mx.ferreteria.api.seg.service.GoogleAuthService;
import mx.ferreteria.api.seg.service.OtpService;
import mx.ferreteria.api.seg.service.RequestMeta;

/**
 * Autenticación: login en dos fases (password → OTP por email/WhatsApp),
 * login con Google (redirect), rotación de refresh, logout, registro público
 * (ENCARGADO_CAJA), cambio de password y perfil.
 *
 * <p>Los tokens solo se emiten tras el OTP verificado, vía cookies HttpOnly
 * ({@code at}/{@code rt}); en logout se eliminan con Max-Age=0.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String SET_COOKIE = "Set-Cookie";

    private final AuthService authService;
    private final OtpService otpService;
    private final GoogleAuthService googleAuthService;
    private final GoogleAuthProperties googleProps;

    /**
     * Primera fase (password): valida credenciales y devuelve un desafío OTP.
     * Los tokens se emiten solo en {@code POST /otp/verificar}.
     */
    @PostMapping("/login")
    @RateLimited("auth")
    @ApiResponse(responseCode = "200", description = "Password OK; desafío OTP con canales disponibles")
    @ApiResponse(responseCode = "401", description = "Credenciales invalidas (CREDENCIALES_INVALIDAS)")
    @ApiResponse(responseCode = "423", description = "Cuenta bloqueada por intentos fallidos (CUENTA_BLOQUEADA)")
    @ApiResponse(responseCode = "429", description = "Rate limit excedido (capacidad/minuto)")
    public ResponseEntity<OtpChallengeResponse> login(@Valid @RequestBody LoginRequest req,
            HttpServletRequest http) {
        return ResponseEntity.ok(authService.login(req, meta(http)));
    }

    /**
     * Metadatos del desafío para pintar la pantalla OTP (la usa el callback
     * de Google, que solo recibe el id en la URL).
     */
    @GetMapping("/otp/desafio")
    @RateLimited("auth")
    @ApiResponse(responseCode = "200", description = "Desafío vigente con canales")
    @ApiResponse(responseCode = "401", description = "Desafío inválido o vencido (OTP_INVALIDO)")
    public ResponseEntity<OtpChallengeResponse> otpDesafio(
            @org.springframework.web.bind.annotation.RequestParam String challengeId) {
        return ResponseEntity.ok(otpService.desafio(challengeId));
    }

    /**
     * Envía (o reenvía) el código de 6 dígitos por el canal elegido.
     * Anti-enumeración: un challengeId desconocido se rechaza igual que un
     * código erróneo (OTP_INVALIDO).
     */
    @PostMapping("/otp/enviar")
    @RateLimited("auth")
    @ApiResponse(responseCode = "200", description = "Código enviado (o reenviado) al canal")
    @ApiResponse(responseCode = "401", description = "Desafío inválido (OTP_INVALIDO)")
    @ApiResponse(responseCode = "422", description = "Canal sin destino registrado (CANAL_NO_DISPONIBLE)")
    @ApiResponse(responseCode = "429", description = "Reenvío muy pronto o intentos agotados")
    public ResponseEntity<Void> otpEnviar(@Valid @RequestBody OtpEnviarRequest req) {
        otpService.enviar(req.challengeId(), req.canal());
        return ResponseEntity.ok().build();
    }

    /**
     * Segunda fase (común a password y Google): verifica el código y emite
     * las cookies `at` y `rt`.
     */
    @PostMapping("/otp/verificar")
    @RateLimited("auth")
    @ApiResponse(responseCode = "200", description = "OTP OK; cookies `at` y `rt` emitidas")
    @ApiResponse(responseCode = "401", description = "Código erróneo/expirado (OTP_INVALIDO/OTP_EXPIRADO)")
    @ApiResponse(responseCode = "429", description = "Intentos agotados (OTP_AGOTADO)")
    public ResponseEntity<TokenResponse> otpVerificar(@Valid @RequestBody OtpVerificarRequest req,
            HttpServletRequest http) {
        LoginResult result = otpService.verificar(req.challengeId(), req.codigo(), meta(http));
        return sesion(result);
    }

    /**
     * Inicia el login con Google: el frontend redirige el browser a esta URL.
     */
    @GetMapping("/oauth2/google")
    @RateLimited("auth")
    @ApiResponse(responseCode = "200", description = "URL de autorización de Google")
    public ResponseEntity<GoogleInitResponse> googleInit(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String state) {
        return ResponseEntity.ok(new GoogleInitResponse(googleAuthService.urlAutorizacion(state)));
    }

    /**
     * Callback de Google: canjea el code, vincula o crea al usuario y
     * redirige al frontend con el desafío OTP (o el error).
     */
    @GetMapping("/oauth2/google/callback")
    @RateLimited("auth")
    @ApiResponse(responseCode = "302", description = "Redirect al frontend con ?challengeId= o ?error=")
    public ResponseEntity<Void> googleCallback(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String code,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String error) {
        String base = googleProps.frontendCallbackUrl() == null
                || googleProps.frontendCallbackUrl().isBlank()
                        ? "http://localhost:5173/auth/callback"
                        : googleProps.frontendCallbackUrl();
        String destino;
        try {
            if (error != null && !error.isBlank()) {
                throw new mx.ferreteria.api.common.error.ValidacionException(
                        mx.ferreteria.api.common.i18n.ErrorCode.OAUTH_FALLIDO);
            }
            OtpChallengeResponse ch = googleAuthService.callback(code);
            destino = base + "?challengeId=" + enc(ch.challengeId());
        } catch (mx.ferreteria.api.common.error.ApiException e) {
            destino = base + "?error=" + enc(e.errorCode().name());
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", destino)
                .build();
    }

    private static String enc(String v) {
        return java.net.URLEncoder.encode(v == null ? "" : v,
                java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Alta pública sin roles (usuario debe recibir rol por ADMINISTRADOR antes
     * de operar). Nunca asigna ADMIN: ver AuthService.register.
     * BACK-UI-001: responde 201 Created porque crea un recurso (ven.Usuarios /
     * seg.Usuarios). 200 OK se reservaría para el login.
     */
    @PostMapping("/register")
    @RateLimited("auth")
    @ApiResponse(responseCode = "201", description = "Usuario y empleado creados")
    @ApiResponse(responseCode = "400", description = "Datos invalidos (password corta, email duplicado)")
    @ApiResponse(responseCode = "409", description = "Username/email duplicado (REGISTRO_DUPLICADO)")
    @ApiResponse(responseCode = "429", description = "Rate limit excedido")
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
    @ApiResponse(responseCode = "200", description = "Nuevo par access/refresh emitido")
    @ApiResponse(responseCode = "401", description = "Token invalido/expirado (TOKEN_EXPIRADO)")
    @ApiResponse(responseCode = "429", description = "Rate limit excedido")
    public ResponseEntity<TokenResponse> refresh(
            @Valid @RequestBody(required = false) RefreshRequest req,
            HttpServletRequest http) {
        LoginResult result = authService.refresh(
                req == null ? null : req.refreshToken(), meta(http), http);
        return sesion(result);
    }

    @PostMapping("/logout")
    @RateLimited("auth")
    public ResponseEntity<LogoutOk> logout(
            @Valid @RequestBody(required = false) RefreshRequest req,
            HttpServletRequest http) {
        boolean ok = authService.logout(req == null ? null : req.refreshToken(), http);
        return ResponseEntity.ok()
                .header(SET_COOKIE, authService.clearRefreshCookie().toString())
                .header(SET_COOKIE, authService.clearAccessCookie().toString())
                .body(new LogoutOk(ok));
    }

    /** Respuesta de sesión: par at/rt en body + cookies HttpOnly. */
    private ResponseEntity<TokenResponse> sesion(LoginResult result) {
        return ResponseEntity.ok()
                .header(SET_COOKIE,
                        authService.buildRefreshCookie(result.refreshRaw()).toString())
                .header(SET_COOKIE,
                        authService.buildAccessCookie(result.body().accessToken()).toString())
                .body(result.body());
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
