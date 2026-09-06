package mx.ferreteria.api.common.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfigurationSource;

import lombok.RequiredArgsConstructor;

import mx.ferreteria.api.common.security.JwtAuthFilter;
import mx.ferreteria.api.common.security.RestAuthEntryPoint;
import mx.ferreteria.api.common.web.CorsConfigurationFactory;
import mx.ferreteria.api.common.web.CorsProperties;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthFilter jwtAuthFilter;
        private final RestAuthEntryPoint entryPoint;
        private final CorsProperties corsProperties;

        @Bean
        public PasswordEncoder passwordEncoder() {
                // BACK-SEC-040: cost 12 (defecto BCrypt es 10). 2^12 = 4096 iteraciones,
                // ~250ms por hash en hardware moderno. Aceptable para login/register
                // (pocos req/s) y bloquea ataques offline contra el hash.
                return new BCryptPasswordEncoder(12);
        }

        /**
         * PASO 30 BACK-SEC-009 + CORS hardening fino — verificacion sin cambio de comportamiento:
         * <ul>
         *   <li>BACK-SEC-009 sort whitelist ya en {@link mx.ferreteria.api.common.web.PageQuery}
         *       (PASO 20, DEFAULT_SORT_FIELDS). Sin pendiente aqui.</li>
         *   <li>Headers HSTS/CSP/frameOptions/referrerPolicy presentes via {@code http.headers(...)}.</li>
         *   <li>CORS via {@link CorsConfigurationFactory#source(CorsProperties)} registrado como
         *       {@code CorsConfigurationSource} @Bean y consumido por {@code http.cors(Customizer.withDefaults())}.</li>
         * </ul>
         */
        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                return CorsConfigurationFactory.source(corsProperties);
        }

        @Bean
        public FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration() {
                var reg = new FilterRegistrationBean<>(this.jwtAuthFilter);
                reg.setEnabled(false);
                return reg;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                // CSRF con cookie legible por JS: el browser envía la cookie
                // XSRF-TOKEN automáticamente y el front la duplica en el header
                // X-XSRF-TOKEN en cada mutación (POST/PUT/PATCH/DELETE).
                var csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
                csrfRepo.setCookieName("XSRF-TOKEN");
                csrfRepo.setHeaderName("X-XSRF-TOKEN");
                // Spring 6: deferred handler para que la cookie se emita en el
                // primer response (no antes, evitando tokens muertos).
                var csrfHandler = new CsrfTokenRequestAttributeHandler();
                csrfHandler.setCsrfRequestAttributeName(null);

                return http
                                // Cabeceras de seguridad (defensa en profundidad).
                                .headers(h -> h
                                                .contentTypeOptions(c -> {})
                                                .frameOptions(f -> f.deny())
                                                .httpStrictTransportSecurity(hsts -> hsts
                                                                .includeSubDomains(true)
                                                                .maxAgeInSeconds(31536000))
                                                .referrerPolicy(r -> r
                                                                .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                                                .contentSecurityPolicy(csp -> csp.policyDirectives(
                                                                "default-src 'self'; frame-ancestors 'none'; base-uri 'self'")))
                                .csrf(csrf -> csrf
                                                .csrfTokenRepository(csrfRepo)
                                                .csrfTokenRequestHandler(csrfHandler)
                                                // /login y /register son la puerta de
                                                // entrada: sin CSRF cookie previa, no
                                                // podemos exigir el header. La defense
                                                // in-depth aquí viene de SameSite=Lax
                                                // en la cookie de auth.
                                                .ignoringRequestMatchers(
                                                                "/api/v1/auth/login",
                                                                "/api/v1/auth/register"))
                                .cors(Customizer.withDefaults())
                                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(HttpMethod.GET,
                                                                "/api/v1/auth/csrf-init")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.POST,
                                                                "/api/v1/auth/login",
                                                                "/api/v1/auth/register",
                                                                "/api/v1/auth/refresh",
                                                                "/api/v1/auth/logout")
                                                .permitAll()
                                                .requestMatchers("/actuator/health", "/actuator/health/**")
                                                .permitAll()
                                                // info/metrics/prometheus y docs requieren auth
                                                .anyRequest().authenticated())
                                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                                .formLogin(f -> f.disable())
                                .httpBasic(b -> b.disable())
                                .anonymous(a -> a.disable())
                                .logout(l -> l.disable())
                                .build();
        }
}
