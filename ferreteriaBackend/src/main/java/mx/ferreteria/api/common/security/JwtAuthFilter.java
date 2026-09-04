package mx.ferreteria.api.common.security;

import java.io.IOException;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Valida el access token (typ=acc) y puebla el SecurityContext con
 * authorities ROLE_&lt;clave&gt;. Sin token o inválido: contexto vacío — la
 * decisión 401/403 la tocan entry point / authorization rules.
 *
 * <p>
 * <b>Origen del token (en orden de precedencia):</b>
 * </p>
 * <ol>
 * <li>Cookie HttpOnly {@code at} (browser con withCredentials).</li>
 * <li>Header {@code Authorization: Bearer ...} (clientes no-browser,
 * tests).</li>
 * </ol>
 * <p>
 * Con cookies HttpOnly el browser NUNCA expone el token a JS: aunque un
 * XSS inyecte código, no puede leer el access ni el refresh (también en
 * cookie), así que no puede escalar a otras llamadas suplantando al usuario.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final AuthCookieProperties cookieProps;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
            FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(req, res);
            return;
        }
        String raw = resolveAccessToken(req);
        if (raw != null) {
            try {
                Claims claims = jwtService.parseAccess(raw);
                int uid = claims.get(JwtService.CLAIM_UID, Integer.class);
                Integer emp = claims.get(JwtService.CLAIM_EMP, Integer.class);
                List<?> rawRoles = claims.get(JwtService.CLAIM_ROLES, List.class);
                List<String> roles = (rawRoles != null)
                        ? rawRoles.stream().map(Object::toString).toList()
                        : List.of();
                var principal = new UserPrincipal(uid, claims.getSubject(), emp,
                        roles == null ? List.of() : roles);
                var authorities = principal.roles().stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toList();
                var auth = new UsernamePasswordAuthenticationToken(principal, tokenNoCredencial(),
                        authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException ex) {
                log.warn("Access rechazado [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }

    /**
     * Lee el access token de la cookie HttpOnly primero; si no está, del
     * header Authorization. La cookie tiene precedencia porque el frontend
     * con withCredentials la envía automáticamente.
     */
    private String resolveAccessToken(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (var c : cookies) {
                if (cookieProps.accessName().equals(c.getName())
                        && c.getValue() != null && !c.getValue().isBlank()) {
                    return c.getValue();
                }
            }
        }
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            String raw = header.substring(BEARER.length());
            if (!raw.isBlank())
                return raw;
        }
        return null;
    }

    /**
     * Credentials null = ya autenticado por token; evita exponer material sensible.
     */
    private static Object tokenNoCredencial() {
        return null;
    }
}
