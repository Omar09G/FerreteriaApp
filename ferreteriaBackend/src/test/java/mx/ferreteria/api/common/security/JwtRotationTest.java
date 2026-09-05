package mx.ferreteria.api.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;

/**
 * BACK-SEC-033: ventana de rotacion de JWT_SECRET.
 * Si previousSecret esta configurado, tokens firmados con el antiguo siguen
 * siendo validos durante la ventana de rotacion.
 */
class JwtRotationTest {

    private static final String OLD = "old-old-old-old-old-old-old-old-old-old-old-old";
    private static final String NEW = "new-new-new-new-new-new-new-new-new-new-new-new";
    private static final String OTHER = "other-other-other-other-other-other-other-other-other-other-other";

    @Test
    @DisplayName("previousSecret valido: token firmado con old sigue siendo valido")
    void rotation_overlap_accepts_old() {
        JwtService oldSvc = new JwtService(new JwtProperties(OLD, null, 15, 8));
        JwtService newSvc = new JwtService(new JwtProperties(NEW, OLD, 15, 8));

        String tokenFromOld = oldSvc.createAccessToken(
                new UserPrincipal(1, "u", 1, java.util.List.of("ADMIN")));
        Claims c = newSvc.parseAccess(tokenFromOld);
        assertThat(c.getSubject()).isEqualTo("u");
    }

    @Test
    @DisplayName("previousSecret ausente: token de otra clave falla")
    void rotation_offline_rejects_other() {
        JwtService otherSvc = new JwtService(new JwtProperties(OTHER, null, 15, 8));
        JwtService newSvc = new JwtService(new JwtProperties(NEW, OLD, 15, 8));

        String tokenFromOther = otherSvc.createAccessToken(
                new UserPrincipal(1, "u", 1, java.util.List.of("ADMIN")));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> newSvc.parseAccess(tokenFromOther))
                .isInstanceOf(io.jsonwebtoken.security.SecurityException.class);
    }

    @Test
    @DisplayName("previousSecret corto (<32 bytes): ignorado, no afecta validacion")
    void rotation_short_previous_is_ignored() {
        JwtService newSvc = new JwtService(new JwtProperties(NEW, "short", 15, 8));
        // No debe lanzar: previousKey queda null y parser usa solo key.
        String tokenFromNew = newSvc.createAccessToken(
                new UserPrincipal(1, "u", 1, java.util.List.of("ADMIN")));
        assertThat(newSvc.parseAccess(tokenFromNew).getSubject()).isEqualTo("u");
    }
}