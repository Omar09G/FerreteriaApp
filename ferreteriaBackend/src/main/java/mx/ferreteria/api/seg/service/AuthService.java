package mx.ferreteria.api.seg.service;

import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.http.HttpServletRequest;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.AuthCookieProperties;
import mx.ferreteria.api.common.security.JwtService;
import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoResumen;
import mx.ferreteria.api.rh.service.EmpleadoGateway;
import mx.ferreteria.api.seg.dto.AuthDtos.ChangePasswordRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.LoginRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.MeResponse;
import mx.ferreteria.api.seg.dto.AuthDtos.PasswordOk;
import mx.ferreteria.api.seg.dto.AuthDtos.RegisterRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.RegisterResponse;
import mx.ferreteria.api.seg.dto.AuthDtos.TokenResponse;

/**
 * Login/refresh/logout/registro/change-password contra seg.usuarios (BCrypt
 * pgcrypto $2a$ compatible). Login con UNA SOLA SESIÓN ACTIVA: revoca todos los
 * refresh tokens previos del usuario (seg.refresh_tokens.revoked_at). Refresh
 * con ROTACIÓN + defensa: un token ya revocado/expirado marca "ya expiró".
 *
 * <p>El refresh token se entrega al browser como cookie HttpOnly (XSS-proof). El
 * body de /auth/refresh y /auth/logout acepta el refresh como fallback para
 * clientes no-browser (Postman, curl, tests).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Rol que /register otorga SIEMPRE (el único admitido para auto-alta). */
    public static final String ROL_REGISTRO = "ENCARGADO_CAJA";

    private final AuthUserGateway gateway;
    private final SegAdminGateway admin;
    private final EmpleadoGateway empleados;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthCookieProperties cookieProps;

    /**
     * Alta pública SOLO ENCARGADO_CAJA (sin permisos de administración):
     * crea el empleado (rh.empleados), luego el usuario ligado (empleado_id) y
     * le asigna únicamente el rol ENCARGADO_CAJA. Imposible auto-otorgar
     * ADMINISTRADOR.
     */
    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        int empleadoId = empleados.create(req.puestoId(), req.nombre(), req.apellidoPaterno(),
                req.apellidoMaterno(), null, null, req.telefono(), req.email(), null, null,
                null, null, java.time.LocalDate.now(), java.math.BigDecimal.ZERO);
        int usuarioId = admin.createUsuario(req.username(), req.email(),
                passwordEncoder.encode(req.password()), empleadoId, true);
        admin.reemplazarRoles(usuarioId, java.util.Set.of(ROL_REGISTRO));
        log.info("empleado={} auto-registrado como usuario_id={} rol={}",
                empleadoId, usuarioId, ROL_REGISTRO);
        return new RegisterResponse(usuarioId, empleadoId, req.username(), req.email());
    }

    /**
     * Cambio de password autoconsciente: valida la contraseña ACTUAL antes de
     * persistir el nuevo hash. (Los JWT ya emitidos siguen válidos hasta su
     * expiración: se validan por firma, no contra el hash.)
     */
    public PasswordOk changePassword(UserPrincipal principal, ChangePasswordRequest req) {
        if (principal == null) {
            throw new ValidacionException(ErrorCode.CREDENCIALES_INVALIDAS);
        }
        var user = gateway.findByUsername(principal.username()).orElseThrow(
                () -> new ValidacionException(ErrorCode.CREDENCIALES_INVALIDAS));
        if (!user.activo() || !passwordEncoder.matches(req.passwordActual(), user.passwordHash())) {
            throw new ValidacionException(ErrorCode.CREDENCIALES_INVALIDAS);
        }
        admin.actualizarPassword(user.usuarioId(),
                passwordEncoder.encode(req.nuevaPassword()));
        log.info("password cambiada usuario_id={}", user.usuarioId());
        return new PasswordOk(true);
    }

    /** Resultado interno de login: cuerpo (sin refresh) + refresh crudo para cookie. */
    public record LoginResult(TokenResponse body, String refreshRaw) { }

    @Transactional
    public LoginResult login(LoginRequest req, RequestMeta meta) {
        var user = gateway.findByUsername(req.username()).orElse(null);
        if (user == null || !user.activo()
                || !passwordEncoder.matches(req.password(), user.passwordHash())) {
            throw new ValidacionException(ErrorCode.CREDENCIALES_INVALIDAS);
        }

        List<String> roles = gateway.rolesOf(user.usuarioId());
        var principal = new UserPrincipal(user.usuarioId(), user.username(),
                user.empleadoId(), roles);

        int sesion = gateway.abrirSesion(user.usuarioId(),
                meta == null ? null : meta.ip(), meta == null ? null : meta.userAgent());

        // UNICA sesión activa: invalida cualquier refresh emitido antes de este login
        gateway.revokeAllRefreshTokens(user.usuarioId());

        String access = jwtService.createAccessToken(principal);
        String refresh = jwtService.createRefreshToken(user.usuarioId(), sesion);

        gateway.saveRefreshToken(user.usuarioId(), JwtService.sha256Base64(refresh),
                Instant.now().plus(jwtService.refreshTtl()));
        gateway.updateUltimoLogin(user.usuarioId());

        return new LoginResult(
                new TokenResponse(access, null, jwtService.refreshTtl().toSeconds(), toMe(principal)),
                refresh);
    }

    /**
     * Rota el refresh token. Origen válido del refresh (en orden de precedencia):
     * <ol>
     *   <li>cookie HttpOnly {@code rt} (browser con withCredentials).</li>
     *   <li>campo {@code refreshToken} del body (clientes no-browser).</li>
     * </ol>
     * Ambos orígenes son excluyentes: si llegan los dos, se prefiere la cookie.
     */
    public LoginResult refresh(String bodyRefresh, RequestMeta meta, HttpServletRequest http) {
        String refreshTokenRaw = resolveRefresh(bodyRefresh, http);
        if (refreshTokenRaw == null || refreshTokenRaw.isBlank()) {
            throw new ValidacionException(ErrorCode.CREDENCIALES_INVALIDAS);
        }
        String hash = JwtService.sha256Base64(refreshTokenRaw);

        Claims claims;
        try {
            claims = jwtService.parseRefresh(refreshTokenRaw); // firma + exp + typ=ref
        } catch (JwtException | IllegalArgumentException ex) {
            gateway.revokeByHash(hash);
            throw new ValidacionException(ErrorCode.TOKEN_EXPIRADO);
        }
        Integer uidToken = claims.get(JwtService.CLAIM_UID, Integer.class);
        if (uidToken == null) {
            gateway.revokeByHash(hash);
            throw new ValidacionException(ErrorCode.CREDENCIALES_INVALIDAS);
        }

        var estado = gateway.findRefreshRow(hash);
        if (estado.isEmpty()) {
            // JWT firma ok pero el hash no existe en la BD: token inválido/revocado antes
            gateway.revokeByHash(hash);
            throw new ValidacionException(ErrorCode.TOKEN_EXPIRADO);
        }
        var row = estado.get();
        if (row.revokedAt() != null) {
            log.warn("refresh REVOCADO (ya expiró) usuario_id={}", row.usuarioId());
            throw new ValidacionException(ErrorCode.TOKEN_EXPIRADO);
        }
        if (!row.expiresAt().isAfter(Instant.now())) {
            log.info("refresh EXPIRO por vigencia de BD usuario_id={}", row.usuarioId());
            gateway.revokeByHash(hash);
            throw new ValidacionException(ErrorCode.TOKEN_EXPIRADO);
        }
        if (row.usuarioId() != uidToken) {
            // hash presentado no corresponde al token: posible robo -> revocación defensiva
            gateway.revokeByHash(hash);
            throw new ValidacionException(ErrorCode.TOKEN_EXPIRADO);
        }

        var ownerOpt = gateway.findActiveRefreshOwner(hash, Instant.now());
        if (ownerOpt.isEmpty()) {
            // usuario desactivado/eliminado pese a tener token vigente
            gateway.revokeByHash(hash);
            throw new ValidacionException(ErrorCode.TOKEN_EXPIRADO);
        }
        var owner = ownerOpt.get();

        gateway.revokeByHash(hash); // ROTACIÓN

        List<String> roles = gateway.rolesOf(owner.usuarioId());
        var principal = new UserPrincipal(owner.usuarioId(), owner.username(),
                owner.empleadoId(), roles);

        Integer sesionViva = claims.get(JwtService.CLAIM_SES, Integer.class);
        String access = jwtService.createAccessToken(principal);
        String nuevoRefresh = jwtService.createRefreshToken(owner.usuarioId(),
                sesionViva == null ? 0 : sesionViva);
        gateway.saveRefreshToken(owner.usuarioId(), JwtService.sha256Base64(nuevoRefresh),
                Instant.now().plus(jwtService.refreshTtl()));

        log.info("refresh rotado usuario_id={} sesion={}", owner.usuarioId(), sesionViva);
        return new LoginResult(
                new TokenResponse(access, null, jwtService.refreshTtl().toSeconds(), toMe(principal)),
                nuevoRefresh);
    }

    /**
     * Cierra la sesión: revoca el hash y cierra la fila de sesión. Acepta el
     * refresh del body o de la cookie (mismo orden que {@link #refresh}).
     */
    public boolean logout(String bodyRefresh, HttpServletRequest http) {
        String refreshTokenRaw = resolveRefresh(bodyRefresh, http);
        if (refreshTokenRaw == null || refreshTokenRaw.isBlank()) {
            return true;
        }
        String hash = JwtService.sha256Base64(refreshTokenRaw);
        try {
            int sesion = jwtService.parseRefresh(refreshTokenRaw)
                    .get(JwtService.CLAIM_SES, Integer.class);
            gateway.cerrarSesion(sesion);
        } catch (JwtException | IllegalArgumentException ignored) {
            // token ya inservible: solo garantizamos revocación del hash
        }
        gateway.revokeByHash(hash);
        return true;
    }

    /**
     * Resuelve el refresh token desde la cookie HttpOnly si está presente, si
     * no, desde el body. La cookie tiene precedencia porque el frontend con
     * withCredentials la envía siempre; el body es fallback para tests / curl.
     */
    private String resolveRefresh(String bodyRefresh, HttpServletRequest http) {
        if (http != null) {
            if (http.getCookies() != null) {
                for (var c : http.getCookies()) {
                    if (cookieProps.refreshName().equals(c.getName()) && c.getValue() != null
                            && !c.getValue().isBlank()) {
                        return c.getValue();
                    }
                }
            }
        }
        return bodyRefresh;
    }

    /** Construye la cookie HttpOnly del refresh token. Llamar en /login y /refresh. */
    public ResponseCookie buildRefreshCookie(String refreshTokenRaw) {
        return ResponseCookie.from(cookieProps.refreshName(), refreshTokenRaw)
                .httpOnly(true)
                .secure(cookieProps.secure())
                .path("/api/v1/auth")
                .maxAge(cookieProps.maxAge(jwtService.refreshTtl()))
                .sameSite(cookieProps.sameSiteMode())
                .build();
    }

    /** Cookie vacía que el browser borra inmediatamente. Llamar en /logout. */
    public ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(cookieProps.refreshName(), "")
                .httpOnly(true)
                .secure(cookieProps.secure())
                .path("/api/v1/auth")
                .maxAge(java.time.Duration.ZERO)
                .sameSite(cookieProps.sameSiteMode())
                .build();
    }

    /**
     * Construye la cookie HttpOnly del access token. Se emite en /login y
     * /refresh para que el browser la adjunte a TODA llamada posterior
     * (path = scope por defecto {@code /} cubre toda la API). El frontend ya
     * no envía {@code Authorization: Bearer}, solo withCredentials.
     */
    public ResponseCookie buildAccessCookie(String accessToken) {
        return ResponseCookie.from(cookieProps.accessName(), accessToken)
                .httpOnly(true)
                .secure(cookieProps.secure())
                .path(cookieProps.path())
                .maxAge(java.time.Duration.ofMinutes(jwtService.accessTtlMinutes()))
                .sameSite(cookieProps.sameSiteMode())
                .build();
    }

    /** Cookie vacía del access. Llamar en /logout. */
    public ResponseCookie clearAccessCookie() {
        return ResponseCookie.from(cookieProps.accessName(), "")
                .httpOnly(true)
                .secure(cookieProps.secure())
                .path(cookieProps.path())
                .maxAge(java.time.Duration.ZERO)
                .sameSite(cookieProps.sameSiteMode())
                .build();
    }

    public MeResponse me(UserPrincipal principal) {
        return toMe(principal);
    }

    private MeResponse toMe(UserPrincipal p) {
        EmpleadoResumen emp = p.empleadoId() == null ? null
                : empleados.resumenById(p.empleadoId()).orElse(null);
        return new MeResponse(p.usuarioId(), p.username(), p.empleadoId(), p.roles(), null, emp);
    }
}
