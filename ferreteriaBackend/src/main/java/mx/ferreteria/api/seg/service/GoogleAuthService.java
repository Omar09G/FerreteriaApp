package mx.ferreteria.api.seg.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.GoogleAuthProperties;
import mx.ferreteria.api.common.time.ZonaHoraria;
import mx.ferreteria.api.rh.service.EmpleadoGateway;
import mx.ferreteria.api.seg.dto.AuthDtos.OtpChallengeResponse;

/**
 * Login con Google (OAuth2 Authorization Code + redirect iniciado por el
 * backend). El {@code code} lo canjea solo el backend; el secret nunca llega
 * al frontend.
 *
 * <p>Validación del {@code id_token} contra el endpoint tokeninfo de Google
 * (firma, {@code aud}, {@code exp}, {@code iss}): evita empaquetar JWKS.
 * Vinculación: {@code google_sub} existente → entra; email verificado de un
 * usuario existente → se vincula; email nuevo → se crea empleado + usuario
 * con el único rol {@code ENCARGADO_CAJA} (nunca ADMIN). En todos los casos
 * el resultado es un desafío OTP (el 2FA aplica a ambas vías).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    /** Puesto para el empleado auto-creado (se busca por nombre exacto). */
    static final String PUESTO_OAUTH_DEFECTO = "Auxiliar administrativo";

    private final GoogleAuthProperties props;
    private final AuthUserGateway usuarios;
    private final SegAdminGateway admin;
    private final EmpleadoGateway empleados;
    private final OtpGateway otp;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    private RestClient rest = RestClient.create();
    private final SecureRandom aleatorio = new SecureRandom();

    /** Seam para tests: sustituye el cliente HTTP real por uno mockeado. */
    void restClientParaTests(RestClient rest) {
        this.rest = rest;
    }

    /** URL de autorización para redirigir al usuario a Google. */
    public String urlAutorizacion(String state) {
        exigirConfigurado();
        String s = state == null || state.isBlank() ? UUID.randomUUID().toString() : state;
        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + enc(props.clientId())
                + "&redirect_uri=" + enc(props.redirectUri())
                + "&response_type=code&scope=" + enc("openid email profile")
                + "&access_type=online&prompt=select_account"
                + "&state=" + enc(s);
    }

    /**
     * Canjea el {@code code}, valida el {@code id_token} y devuelve el
     * desafío OTP del usuario vinculado o creado.
     */
    @Transactional
    public OtpChallengeResponse callback(String code) {
        exigirConfigurado();
        if (code == null || code.isBlank()) {
            throw new ValidacionException(ErrorCode.OAUTH_FALLIDO);
        }
        JsonNode tokeninfo = canjearYValidar(code);
        String sub = texto(tokeninfo, "sub");
        String email = texto(tokeninfo, "email");
        boolean verificado = "true".equalsIgnoreCase(texto(tokeninfo, "email_verified"));
        if (sub == null || sub.isBlank() || email == null || email.isBlank() || !verificado) {
            log.warn("google tokeninfo sin sub/email verificado");
            throw new ValidacionException(ErrorCode.OAUTH_FALLIDO);
        }
        int usuarioId = resolverUsuario(sub, email, texto(tokeninfo, "name"));
        String challengeId = otp.crearDesafio(usuarioId, Duration.ofMinutes(5));
        return authService.desafioPara(usuarioId, challengeId);
    }

    private int resolverUsuario(String sub, String email, String nombre) {
        var porSub = usuarios.findByGoogleSub(sub);
        if (porSub.isPresent()) {
            exigirActivo(porSub.get());
            return porSub.get().usuarioId();
        }
        var porEmail = usuarios.findByEmail(email);
        if (porEmail.isPresent()) {
            var u = porEmail.get();
            exigirActivo(u);
            // Anti-takeover: solo vincular si el email local coincide con el
            // verificado por Google (ya validado arriba).
            usuarios.linkGoogleAccount(u.usuarioId(), sub);
            log.info("google vinculado usuario_id={} email={}", u.usuarioId(), email);
            return u.usuarioId();
        }
        return crearUsuario(sub, email, nombre);
    }

    private int crearUsuario(String sub, String email, String nombre) {
        String base = email.contains("@") ? email.substring(0, email.indexOf('@')) : "google";
        String username = base.replaceAll("[^a-zA-Z0-9._-]", "").toLowerCase();
        if (username.isBlank()) {
            username = "google";
        }
        if (usuarios.findByUsername(username).isPresent()) {
            username = (username + "_" + sub.substring(0, Math.min(6, sub.length()))).toLowerCase();
        }
        int puestoId = empleados.puestoIdPorNombre(PUESTO_OAUTH_DEFECTO).orElseGet(
                () -> empleados.puestoIdPorNombre("Vendedor").orElseThrow(
                        () -> new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE)));
        String[] partes = nombre == null ? new String[0] : nombre.trim().split("\\s+");
        String nombrePila = partes.length > 0 ? partes[0] : base;
        String paterno = partes.length > 1 ? partes[1] : "Google";
        int empleadoId = empleados.create(new EmpleadoGateway.EmpleadoDatos(puestoId,
                nombrePila, paterno, null, null, null, null, null, email,
                null, null, null, null, ZonaHoraria.hoy(), BigDecimal.ZERO, null));
        // Password aleatoria inutilizable: esta cuenta solo entra por Google + OTP.
        byte[] azar = new byte[24];
        aleatorio.nextBytes(azar);
        String hash = passwordEncoder.encode(Base64.getUrlEncoder().withoutPadding()
                .encodeToString(azar));
        int usuarioId = admin.createUsuario(username, email, hash, empleadoId, true);
        usuarios.linkGoogleAccount(usuarioId, sub);
        admin.reemplazarRoles(usuarioId, java.util.Set.of(AuthService.ROL_REGISTRO));
        log.info("google creado usuario_id={} username={}", usuarioId, username);
        return usuarioId;
    }

    private static void exigirActivo(AuthUserGateway.AuthUser u) {
        if (!u.activo()) {
            throw new ValidacionException(ErrorCode.CREDENCIALES_INVALIDAS);
        }
    }

    private void exigirConfigurado() {
        if (!props.configurado()) {
            log.warn("google oauth sin configurar (faltan GOOGLE_CLIENT_ID/SECRET/REDIRECT_URI)");
            throw new ValidacionException(ErrorCode.OAUTH_FALLIDO);
        }
    }

    private JsonNode canjearYValidar(String code) {
        try {
            String body = rest.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("code=" + enc(code)
                            + "&client_id=" + enc(props.clientId())
                            + "&client_secret=" + enc(props.clientSecret())
                            + "&redirect_uri=" + enc(props.redirectUri())
                            + "&grant_type=authorization_code")
                    .retrieve()
                    .body(String.class);
            String idToken = objectMapper.readTree(body).path("id_token").asText(null);
            if (idToken == null) {
                throw new ValidacionException(ErrorCode.OAUTH_FALLIDO);
            }
            String info = rest.get()
                    .uri("https://oauth2.googleapis.com/tokeninfo?id_token=" + enc(idToken))
                    .retrieve()
                    .body(String.class);
            JsonNode n = objectMapper.readTree(info);
            if (!props.clientId().equals(texto(n, "aud"))) {
                log.warn("google aud no coincide");
                throw new ValidacionException(ErrorCode.OAUTH_FALLIDO);
            }
            String iss = texto(n, "iss");
            if (!"https://accounts.google.com".equals(iss) && !"accounts.google.com".equals(iss)) {
                log.warn("google iss no válido");
                throw new ValidacionException(ErrorCode.OAUTH_FALLIDO);
            }
            long exp = n.path("exp").asLong(0);
            if (exp * 1000L < System.currentTimeMillis()) {
                throw new ValidacionException(ErrorCode.OAUTH_FALLIDO);
            }
            return n;
        } catch (ValidacionException e) {
            throw e;
        } catch (Exception e) {
            log.warn("google callback fallo: {}", e.getMessage());
            throw new ValidacionException(ErrorCode.OAUTH_FALLIDO);
        }
    }

    private static String texto(JsonNode n, String campo) {
        return n == null ? null : n.path(campo).asText(null);
    }

    private static String enc(String v) {
        return java.net.URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }
}
