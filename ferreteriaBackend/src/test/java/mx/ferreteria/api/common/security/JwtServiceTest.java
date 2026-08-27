package mx.ferreteria.api.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;

class JwtServiceTest {

    private static final String SECRET_32 = "0123456789abcdef0123456789abcdef"; // 32 bytes
    private final UserPrincipal user = new UserPrincipal(7, "cajero1", 42, List.of("VENDEDOR", "CAJERO"));

    private JwtService svc(int accessMinutes) {
        return new JwtService(new JwtProperties(SECRET_32, accessMinutes, 8));
    }

    @Test
    @DisplayName("access: roundtrip de claims (uid, emp, roles) y typ=acc")
    void accessToken_roundtrip() {
        Claims c = svc(15).parseAccess(svc(15).createAccessToken(user));

        assertThat(c.getSubject()).isEqualTo("cajero1");
        assertThat(c.get(JwtService.CLAIM_UID, Integer.class)).isEqualTo(7);
        assertThat(c.get(JwtService.CLAIM_EMP, Integer.class)).isEqualTo(42);
        // Obtenemos el claim sin pasarle List.class, y AssertJ se encarga del tipo
        // seguro
        assertThat(c.get(JwtService.CLAIM_ROLES))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(String.class)) // Convierte de forma
                                                                                                 // segura a una lista
                                                                                                 // de Strings
                .containsExactly("VENDEDOR", "CAJERO");
        assertThat(c.get(JwtService.CLAIM_TYP, String.class)).isEqualTo("acc");
    }

    @Test
    @DisplayName("access expirado: parse lanza ExpiredJwtException")
    void accessToken_expired_isRejected() {
        String token = svc(-1).createAccessToken(user); // TTL negativo => ya expiró
        assertThatThrownBy(() -> svc(15).parseAccess(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("firma alterada: SignatureException (integridad)")
    void tamperedToken_isRejected() {
        String token = svc(15).createAccessToken(user);
        String tampered = token.substring(0, token.length() - 3) + "AAA";
        assertThatThrownBy(() -> svc(15).parseAccess(tampered))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    @DisplayName("secret corto (<32 bytes) rechaza el arranque con mensaje claro")
    void shortSecret_failsFast() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("corto", 15, 8)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("refresh: typ=ref; parseAccess lo rechaza y sha256Base64 es determinista")
    void refresh_typ_and_hash() {
        var s = svc(15);
        String refresh = s.createRefreshToken(7, 1);

        Claims c = s.parseRefresh(refresh);
        assertThat(c.get(JwtService.CLAIM_TYP, String.class)).isEqualTo("ref");
        assertThat(c.get(JwtService.CLAIM_SES, Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> s.parseAccess(refresh))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);

        assertThat(JwtService.sha256Base64(refresh))
                .isEqualTo(JwtService.sha256Base64(refresh))
                .hasSize(43); // base64url sin padding de 256 bits
    }
}
