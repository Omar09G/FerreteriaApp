package mx.ferreteria.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

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

/**
 * DoD M1: flujo completo login → me → logout → refresh rechazado contra PG real
 * con el esquema migrado por Flyway y roles semilla. Sin docker/podman socket se
 * salta automáticamente (CI sí lo ejecuta).
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

    int usuarioId;

    @BeforeEach
    void seedUser() {
        var encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode("Secreta123");
        jdbc.update("""
                INSERT INTO seg.usuarios (username, password_hash, activo)
                VALUES ('testuser', ?, true)
                """, hash);
        usuarioId = jdbc.queryForObject(
                "SELECT usuario_id FROM seg.usuarios WHERE username='testuser'", Integer.class);
        Integer rolId = jdbc.queryForObject(
                "SELECT rol_id FROM seg.roles WHERE clave='ADMINISTRADOR'", Integer.class);
        jdbc.update("INSERT INTO seg.usuario_roles (usuario_id, rol_id) VALUES (?, ?)",
                usuarioId, rolId);
    }

    private HttpHeaders json() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    static String sha256b64(String raw) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    record LoginResp(String accessToken, String refreshToken, MeInner usuario) { }
    record MeInner(String username, java.util.List<String> roles) { }

    @SuppressWarnings("unchecked")
    <T> T parse(String json, Class<T> type) {
        var om = new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            if (type == LoginResp.class) {
                var node = om.readTree(json);
                return (T) new LoginResp(
                        node.path("accessToken").asText(),
                        node.path("refreshToken").asText(),
                        new MeInner(node.path("usuario").path("username").asText(),
                                om.readValue(node.path("usuario").path("roles").toString(),
                                        java.util.List.class)));
            }
            return om.readValue(json.getBytes(StandardCharsets.UTF_8), type);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("login -> me(roles) -> logout -> refresh rechazado; password mala -> 401 codigo estable")
    void fullAuthFlow() {
        // 1. password incorrecta
        var badReq = new HttpEntity<>(
                "{\"username\":\"testuser\",\"password\":\"mala\"}", json());
        var bad = rest.postForEntity("/api/v1/auth/login", badReq, String.class);
        assertThat(bad.getStatusCode().value()).isEqualTo(401);
        assertThat(bad.getBody()).contains("CREDENCIALES_INVALIDAS");

        // 2. login feliz
        var okReq = new HttpEntity<>(
                "{\"username\":\"testuser\",\"password\":\"Secreta123\"}", json());
        var ok = rest.postForEntity("/api/v1/auth/login", okReq, String.class);
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        LoginResp tokens = parse(ok.getBody(), LoginResp.class);
        assertThat(tokens.usuario().username()).isEqualTo("testuser");
        assertThat(tokens.usuario().roles()).containsExactly("ADMINISTRADOR");

        // 3. me con Bearer
        HttpHeaders authed = json();
        authed.set(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()));
        var me = rest.exchange("/api/v1/auth/me", org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(authed), String.class);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat(me.getBody()).contains("testuser");

        // 4. me SIN token -> 401 TOKEN_EXPIRADO
        var anon = rest.exchange("/api/v1/auth/me", org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(json()), String.class);
        assertThat(anon.getStatusCode().value()).isEqualTo(401);
        assertThat(anon.getBody()).contains("TOKEN_EXPIRADO");

        // 5. logout revoca; refresh posterior falla
        var outReq = new HttpEntity<>(
                "{\"refreshToken\":\"" + tokens.refreshToken() + "\"}", json());
        assertThat(rest.postForEntity("/api/v1/auth/logout", outReq, String.class)
                .getStatusCode().value()).isEqualTo(200);
        var afterLogout = rest.postForEntity("/api/v1/auth/refresh",
                new HttpEntity<>("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}",
                        json()), String.class);
        assertThat(afterLogout.getStatusCode().value()).isEqualTo(401);

        // 6. sesión registrada con inicio y cerrada por logout
        Integer sesiones = jdbc.queryForObject(
                "SELECT count(*) FROM seg.sesiones WHERE usuario_id=" + usuarioId
                + " AND fin IS NOT NULL AND cerrada_por_logout", Integer.class);
        assertThat(sesiones).isEqualTo(1);
    }

    @Test
    @DisplayName("refresh rota: el hash viejo queda revocado en BD")
    void refreshRotation_revokesOldHashInDb() {
        var okReq = new HttpEntity<>(
                "{\"username\":\"testuser\",\"password\":\"Secreta123\"}", json());
        LoginResp t1 = parse(
                rest.postForEntity("/api/v1/auth/login", okReq, String.class).getBody(),
                LoginResp.class);

        var req = new HttpEntity<>("{\"refreshToken\":\"" + t1.refreshToken() + "\"}", json());
        LoginResp t2 = parse(
                rest.postForEntity("/api/v1/auth/refresh", req, String.class).getBody(),
                LoginResp.class);
        assertThat(t2.accessToken()).isNotBlank();

        Integer revokedOld = jdbc.queryForObject(
                "SELECT count(*) FROM seg.refresh_tokens WHERE token_hash=? AND revoked_at IS NOT NULL",
                Integer.class, sha256b64(t1.refreshToken()));
        assertThat(revokedOld).isEqualTo(1);
    }
}
