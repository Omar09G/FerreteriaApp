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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Valida el Bearer access token (typ=acc) y puebla el SecurityContext con
 * authorities ROLE_&lt;clave&gt;. Sin token o inválido: contexto vacío — la
 * decisión
 * 401/403 la tocan entry point / authorization rules.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String BEARER = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
            FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String raw = header.substring(BEARER.length());
                log.debug("Auth header crudo len={} head={} tail={}", header.length(),
                        header.substring(0, Math.min(12, header.length())),
                        header.substring(Math.max(0, header.length() - 8)));
                Claims claims = jwtService.parseAccess(raw);
                int uid = claims.get(JwtService.CLAIM_UID, Integer.class);
                Integer emp = claims.get(JwtService.CLAIM_EMP, Integer.class);
                List<?> rawRoles = claims.get(JwtService.CLAIM_ROLES, List.class);
                List<String> roles = (rawRoles != null)
                        ? rawRoles.stream().map(Object::toString).toList()
                        // ? rawRoles.stream().map(Object::toString).collect(Collectors.toList()) // Si
                        // usas Java 11 o inferior
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
                log.warn("Bearer rechazado [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }

    /**
     * Credentials null = ya autenticado por token; evita exponer material sensible.
     */
    private static Object tokenNoCredencial() {
        return null;
    }
}
