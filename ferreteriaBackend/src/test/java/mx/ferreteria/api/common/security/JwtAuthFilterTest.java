package mx.ferreteria.api.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    JwtService jwtService;

    @Mock
    mx.ferreteria.api.common.security.AuthCookieProperties cookieProperties;

    JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtService, cookieProperties);
        SecurityContextHolder.clearContext();
    }

    private final JwtService realJwt = new JwtService(new JwtProperties("0123456789abcdef0123456789abcdef", 15, 8));

    /** Claims reales emitidos por el JwtService (impl de jjwt es runtimeOnly). */
    private Claims claims(int uid, String username, List<String> roles) {
        String token = realJwt.createAccessToken(
                new UserPrincipal(uid, username, null, roles));
        return realJwt.parseAccess(token);
    }

    @Test
    @DisplayName("Bearer valido: autentica con authorities ROLE_<clave>")
    void validToken_authenticatesWithRoles() throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/v1/productos");
        req.addHeader("Authorization", "Bearer token-ok");
        var res = new MockHttpServletResponse();

        when(jwtService.parseAccess("token-ok")).thenReturn(claims(7, "cajero1",
                List.of("VENDEDOR", "CAJERO")));

        filter.doFilter(req, res, new MockFilterChain());

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(UserPrincipal.class);
        assertThat(((UserPrincipal) auth.getPrincipal()).usuarioId()).isEqualTo(7);
        assertThat(auth.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_VENDEDOR", "ROLE_CAJERO");
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Bearer invalido: contexto vacio, request sigue (401 lo decide el entry point)")
    void invalidToken_leavesContextEmpty_andContinues() throws Exception {
        var req = new MockHttpServletRequest("GET", "/x");
        req.addHeader("Authorization", "Bearer roto");
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        when(jwtService.parseAccess("roto")).thenThrow(new JwtException("firma mala"));

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNotNull(); // cadena continuó
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("sin Authorization: passthrough puro")
    void noHeader_passthrough() throws Exception {
        var req = new MockHttpServletRequest("GET", "/x");
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
