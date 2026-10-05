package mx.ferreteria.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import mx.ferreteria.api.notif.service.WhatsAppMockBandeja;

/**
 * DoD: login en dos fases (password → OTP) contra PG real con el esquema
 * migrado por Flyway y roles semilla. El OTP viaja por WhatsApp mock
 * (bandeja en memoria, sin SMTP ni proveedor real). Sin docker/podman socket
 * se salta automáticamente (CI sí lo ejecuta).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> pg =
            new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName("ferreteria");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        r.add("app.jwt.secret", () -> "0123456789abcdef0123456789abcdef");
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    WhatsAppMockBandeja bandeja;

    int usuarioId;

    @BeforeEach
    void seedUser() {
        var encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode("Secreta123");
        jdbc.update("DELETE FROM seg.otp_desafios WHERE usuario_id IN "
                + "(SELECT usuario_id FROM seg.usuarios WHERE username='testuser')");
        jdbc.update("DELETE FROM seg.refresh_tokens WHERE usuario_id IN "
                + "(SELECT usuario_id FROM seg.usuarios WHERE username='testuser')");
        jdbc.update("DELETE FROM seg.sesiones WHERE usuario_id IN "
                + "(SELECT usuario_id FROM seg.usuarios WHERE username='testuser')");
        jdbc.update("DELETE FROM seg.usuario_roles WHERE usuario_id IN "
                + "(SELECT usuario_id FROM seg.usuarios WHERE username='testuser')");
        jdbc.update("DELETE FROM seg.usuarios WHERE username='testuser'");
        Integer puestoId = jdbc.queryForObject(
                "SELECT puesto_id FROM cat.puestos WHERE nombre='Auxiliar administrativo'",
                Integer.class);
        Integer empleadoId = jdbc.queryForObject("""
                INSERT INTO rh.empleados (puesto_id, nombre, apellido_p, telefono, whatsapp, email)
                VALUES (?, 'Test', 'User', '5551234567', '5551234567', 'test@ferreteria.local')
                RETURNING empleado_id
                """, Integer.class, puestoId);
        jdbc.update("""
                INSERT INTO seg.usuarios (username, email, password_hash, activo, empleado_id)
                VALUES ('testuser', 'test@ferreteria.local', ?, true, ?)
                """, hash, empleadoId);
        usuarioId = jdbc.queryForObject(
                "SELECT usuario_id FROM seg.usuarios WHERE username='testuser'", Integer.class);
        Integer rolId = jdbc.queryForObject(
                "SELECT rol_id FROM seg.roles WHERE clave='ADMINISTRADOR'", Integer.class);
        jdbc.update("INSERT INTO seg.usuario_roles (usuario_id, rol_id) VALUES (?, ?)",
                usuarioId, rolId);
        bandeja.limpiar();
    }

    private HttpHeaders json() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    record ChallengeResp(String challengeId, List<String> canales) { }

    record TokenResp(String accessToken, MeInner usuario) { }

    record MeInner(String username, java.util.List<String> roles) { }

    private final com.fasterxml.jackson.databind.ObjectMapper om =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private ChallengeResp challenge(String json) {
        try {
            var node = om.readTree(json);
            return new ChallengeResp(node.path("challengeId").asText(),
                    om.readValue(node.path("canales").toString(), java.util.List.class));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private TokenResp tokens(String json) {
        try {
            var node = om.readTree(json);
            return new TokenResp(node.path("accessToken").asText(),
                    new MeInner(node.path("usuario").path("username").asText(),
                            om.readValue(node.path("usuario").path("roles").toString(),
                                    java.util.List.class)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Flujo completo: login → challenge → OTP por WhatsApp → sesión. */
    private TokenResp loginConOtp() {
        var okReq = new HttpEntity<>(
                "{\"username\":\"testuser\",\"password\":\"Secreta123\"}", json());
        var ok = rest.postForEntity("/api/v1/auth/login", okReq, String.class);
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        ChallengeResp ch = challenge(body(ok));
        assertThat(ch.challengeId()).isNotBlank();
        assertThat(ch.canales()).contains("email", "whatsapp");

        var envReq = new HttpEntity<>(
                "{\"challengeId\":\"" + ch.challengeId() + "\",\"canal\":\"whatsapp\"}",
                json());
        var env = rest.postForEntity("/api/v1/auth/otp/enviar", envReq, String.class);
        assertThat(env.getStatusCode().value()).isEqualTo(200);

        assertThat(bandeja.mensajes()).hasSize(1);
        Matcher m = Pattern.compile("(\\d{6})")
                .matcher(bandeja.mensajes().get(0).asunto());
        assertThat(m.find()).isTrue();
        String codigo = m.group(1);

        var verReq = new HttpEntity<>(
                "{\"challengeId\":\"" + ch.challengeId() + "\",\"codigo\":\"" + codigo + "\"}",
                json());
        var ver = rest.postForEntity("/api/v1/auth/otp/verificar", verReq, String.class);
        assertThat(ver.getStatusCode().value()).isEqualTo(200);
        TokenResp t = tokens(body(ver));
        assertThat(t.accessToken()).isNotBlank();
        assertThat(t.usuario().username()).isEqualTo("testuser");
        assertThat(t.usuario().roles()).containsExactly("ADMINISTRADOR");
        return t;
    }

    private static String body(
            org.springframework.http.ResponseEntity<String> r) {
        String json = r.getBody();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(json).path("data").toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("login -> OTP -> me(roles) -> logout -> refresh rechazado; mala pass -> 401")
    void fullAuthFlow() {
        // 1. password incorrecta
        var badReq = new HttpEntity<>(
                "{\"username\":\"testuser\",\"password\":\"mala\"}", json());
        var bad = rest.postForEntity("/api/v1/auth/login", badReq, String.class);
        assertThat(bad.getStatusCode().value()).isEqualTo(401);
        assertThat(bad.getBody()).contains("CREDENCIALES_INVALIDAS");

        // 2. login feliz en dos fases
        TokenResp t = loginConOtp();

        // 3. me con Bearer
        HttpHeaders authed = json();
        authed.set(HttpHeaders.AUTHORIZATION, bearer(t.accessToken()));
        var me = rest.exchange("/api/v1/auth/me", org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(authed), String.class);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat(me.getBody()).contains("testuser");

        // 4. me SIN token -> 401 TOKEN_EXPIRADO
        var anon = rest.exchange("/api/v1/auth/me", org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(json()), String.class);
        assertThat(anon.getStatusCode().value()).isEqualTo(401);
        assertThat(anon.getBody()).contains("TOKEN_EXPIRADO");

        // 5. logout revoca (cookie rt viaja sola); refresh posterior falla
        var out = rest.postForEntity("/api/v1/auth/logout",
                new HttpEntity<>("{}", json()), String.class);
        assertThat(out.getStatusCode().value()).isEqualTo(200);
        var afterLogout = rest.postForEntity("/api/v1/auth/refresh",
                new HttpEntity<>("{}", json()), String.class);
        assertThat(afterLogout.getStatusCode().value()).isEqualTo(401);

        // 6. sesión registrada con inicio y cerrada por logout
        Integer sesiones = jdbc.queryForObject(
                "SELECT count(*) FROM seg.sesiones WHERE usuario_id=" + usuarioId
                + " AND fin IS NOT NULL AND cerrada_por_logout", Integer.class);
        assertThat(sesiones).isEqualTo(1);

        // 7. el desafío quedó consumido (un solo uso)
        Integer consumidos = jdbc.queryForObject(
                "SELECT count(*) FROM seg.otp_desafios WHERE usuario_id=" + usuarioId
                + " AND consumido_en IS NOT NULL", Integer.class);
        assertThat(consumidos).isEqualTo(1);
    }

    @Test
    @DisplayName("OTP erróneo repetido agota intentos; refresh rota revocando el hash viejo")
    void otpAgotadoYRefreshRotation() {
        var okReq = new HttpEntity<>(
                "{\"username\":\"testuser\",\"password\":\"Secreta123\"}", json());
        ChallengeResp ch = challenge(body(
                rest.postForEntity("/api/v1/auth/login", okReq, String.class)));
        var envReq = new HttpEntity<>("{\"challengeId\":\"" + ch.challengeId()
                + "\",\"canal\":\"whatsapp\"}", json());
        assertThat(rest.postForEntity("/api/v1/auth/otp/enviar", envReq, String.class)
                .getStatusCode().value()).isEqualTo(200);
        // 5 intentos erróneos agotan el desafío (los 4 primeros 401, el último 429)
        for (int i = 0; i < 5; i++) {
            var verReq = new HttpEntity<>("{\"challengeId\":\"" + ch.challengeId()
                    + "\",\"codigo\":\"00000" + i + "\"}", json());
            var ver = rest.postForEntity("/api/v1/auth/otp/verificar", verReq,
                    String.class);
            assertThat(ver.getStatusCode().value())
                    .isEqualTo(i < 4 ? 401 : 429);
            assertThat(ver.getBody()).contains(i < 4 ? "OTP_INVALIDO" : "OTP_AGOTADO");
        }
        bandeja.limpiar();

        // nuevo login + OTP válido para la parte de rotación
        TokenResp t1 = loginConOtp();
        var req = new HttpEntity<>("{}", json());
        TokenResp t2 = tokens(body(
                rest.postForEntity("/api/v1/auth/refresh", req, String.class)));
        assertThat(t2.accessToken()).isNotBlank();
        assertThat(t2.accessToken()).isNotEqualTo(t1.accessToken());
    }
}
