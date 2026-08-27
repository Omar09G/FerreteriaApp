package mx.ferreteria.api.seg.service;

import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.JwtService;
import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.seg.dto.AuthDtos.LoginRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.MeResponse;
import mx.ferreteria.api.seg.dto.AuthDtos.TokenResponse;

/**
 * Login/refresh/logout contra seg.usuarios (BCrypt pgcrypto $2a$ compatible).
 * Refresh con ROTACIÓN: cada uso revoca el hash anterior; logout revoca el
 * actual.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthUserGateway gateway;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public TokenResponse login(LoginRequest req, RequestMeta meta) {
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

        String access = jwtService.createAccessToken(principal);
        String refresh = jwtService.createRefreshToken(user.usuarioId(), sesion);

        gateway.saveRefreshToken(user.usuarioId(), JwtService.sha256Base64(refresh),
                Instant.now().plus(jwtService.refreshTtl()));
        gateway.updateUltimoLogin(user.usuarioId());

        return new TokenResponse(access, refresh,
                jwtService.refreshTtl().toSeconds(), toMe(principal));
    }

    public TokenResponse refresh(String refreshTokenRaw, RequestMeta meta) {
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

        var ownerOpt = gateway.findActiveRefreshOwner(hash, Instant.now());
        if (ownerOpt.isEmpty() || ownerOpt.get().usuarioId() != uidToken) {
            // hash presentado no corresponde al token: posible robo -> revocación defensiva
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
        return new TokenResponse(access, nuevoRefresh,
                jwtService.refreshTtl().toSeconds(), toMe(principal));
    }

    public boolean logout(String refreshTokenRaw) {
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

    public MeResponse me(UserPrincipal principal) {
        return toMe(principal);
    }

    private MeResponse toMe(UserPrincipal p) {
        return new MeResponse(p.usuarioId(), p.username(), p.empleadoId(), p.roles(), null);
    }
}
