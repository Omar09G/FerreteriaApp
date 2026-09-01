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
                return new BCryptPasswordEncoder();
        }

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
                                                                "/api/v1/auth/refresh",
                                                                "/api/v1/auth/logout",
                                                                "/api/v1/auth/register")
                                                .permitAll()
                                                .requestMatchers("/actuator/**", "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/v3/api-docs/**")
                                                .permitAll()
                                                .anyRequest().authenticated())
                                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                                .formLogin(f -> f.disable())
                                .httpBasic(b -> b.disable())
                                .anonymous(a -> a.disable())
                                .logout(l -> l.disable())
                                .build();
        }
}
