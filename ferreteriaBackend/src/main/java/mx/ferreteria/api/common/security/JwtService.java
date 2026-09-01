package mx.ferreteria.api.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

/**
 * Emisión y validación de tokens (access typ=acc con roles; refresh typ=ref sin
 * datos).
 * El refresh que viaja al cliente es un JWT opaco cuyo SHA-256 se persiste en
 * seg.refresh_tokens (rotación en cada refresh, revocación en logout).
 */
@Component
public class JwtService {

    public static final String CLAIM_UID = "uid";
    public static final String CLAIM_EMP = "emp";
    public static final String CLAIM_ROLES = "rol";
    public static final String CLAIM_TYP = "typ";
    public static final String CLAIM_SES = "ses";
    public static final String TYP_ACCESS = "acc";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(JwtProperties props) {
        byte[] secret = props.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "JWT secret debe tener al menos 32 bytes; configure JWT_SECRET");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.accessTtl = Duration.ofMinutes(props.accessMinutes());
        this.refreshTtl = Duration.ofHours(props.refreshHours());
    }

    public String createAccessToken(UserPrincipal user) {
        return Jwts.builder()
                .subject(user.username())
                .claim(CLAIM_UID, user.usuarioId())
                .claim(CLAIM_EMP, user.empleadoId())
                .claim(CLAIM_ROLES, user.roles())
                .claim(CLAIM_TYP, TYP_ACCESS)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /** JWT opaco de refresh: solo identifica; la validez real vive en BD (hash). */
    public String createRefreshToken(int usuarioId, int sesionId) {
        return Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .claim(CLAIM_UID, usuarioId)
                .claim(CLAIM_SES, sesionId)
                .claim(CLAIM_TYP, "ref")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(refreshTtl)))
                .signWith(key)
                .compact();
    }

    /**
     * Valida firma+expiración+typ=acc y devuelve claims. Lanza JwtException si no.
     */
    public Claims parseAccess(String token) {
        Claims claims = parser(token).getPayload();
        if (!TYP_ACCESS.equals(claims.get(CLAIM_TYP, String.class))) {
            throw new SignatureException("token no es de acceso");
        }
        return claims;
    }

    /** Valida firma/expiración del refresh y exige typ=ref. */
    public Claims parseRefresh(String token) {
        Claims c = parser(token).getPayload();
        if (!"ref".equals(c.get(CLAIM_TYP, String.class))) {
            throw new SignatureException("token no es de refresh");
        }
        return c;
    }

    private io.jsonwebtoken.Jws<Claims> parser(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }

    public static String sha256Base64(String raw) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    public long accessTtlMinutes() {
        return accessTtl.toMinutes();
    }
}
