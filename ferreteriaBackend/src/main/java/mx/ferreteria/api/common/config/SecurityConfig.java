package mx.ferreteria.api.common.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;

import mx.ferreteria.api.common.security.JwtAuthFilter;
import mx.ferreteria.api.common.security.RestAuthEntryPoint;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthFilter jwtAuthFilter;
        private final RestAuthEntryPoint entryPoint;

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration() {
                var reg = new FilterRegistrationBean<>(this.jwtAuthFilter);
                reg.setEnabled(false);
                return reg;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                return http
                                .csrf(csrf -> csrf.disable())
                                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(HttpMethod.POST,
                                                                "/api/v1/auth/login", "/api/v1/auth/refresh",
                                                                "/api/v1/auth/logout")
                                                .permitAll()
                                                .requestMatchers("/actuator/**", "/swagger-ui/**", "/swagger-ui.html",
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
