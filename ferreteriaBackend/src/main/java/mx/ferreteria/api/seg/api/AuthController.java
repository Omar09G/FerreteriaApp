package mx.ferreteria.api.seg.api;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.common.web.RateLimited;
import mx.ferreteria.api.seg.dto.AuthDtos.ChangePasswordRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.LoginRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.LogoutOk;
import mx.ferreteria.api.seg.dto.AuthDtos.MeResponse;
import mx.ferreteria.api.seg.dto.AuthDtos.PasswordOk;
import mx.ferreteria.api.seg.dto.AuthDtos.RefreshRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.RegisterRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.RegisterResponse;
import mx.ferreteria.api.seg.dto.AuthDtos.TokenResponse;
import mx.ferreteria.api.seg.service.AuthService;
import mx.ferreteria.api.seg.service.RequestMeta;

/**
 * Autenticación M1: login, rotación de refresh, logout, registro público
 * (ENCARGADO_CAJA), cambio de password y perfil (PLAN §5/§6).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @RateLimited("auth")
    public TokenResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return authService.login(req, meta(http));
    }

    /**
     * Alta pública sin roles (usuario debe recibir rol por ADMINISTRADOR antes
     * de operar). Nunca asigna ADMIN: ver AuthService.register.
     */
    @PostMapping("/register")
    @RateLimited("auth")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    /**
     * Cambio de password del usuario autenticado (requiere password actual).
     */
    @PostMapping("/change-password")
    public ResponseEntity<PasswordOk> changePassword(
            @Valid @RequestBody ChangePasswordRequest req, java.security.Principal principal) {
        Object source = principal instanceof org.springframework.security.core.Authentication auth
                ? auth.getPrincipal()
                : principal;
        if (source instanceof UserPrincipal up) {
            return ResponseEntity.ok(authService.changePassword(up, req));
        }
        return ResponseEntity.status(401).build();
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        return authService.refresh(req.refreshToken(), meta(http));
    }

    @PostMapping("/logout")
    public LogoutOk logout(@Valid @RequestBody RefreshRequest req) {
        return new LogoutOk(authService.logout(req.refreshToken()));
    }

    private mx.ferreteria.api.seg.service.RequestMeta meta(HttpServletRequest h) {
        String xff = h.getHeader("X-Forwarded-For");
        String ip = xff != null && !xff.isBlank() ? xff.split(",")[0].trim() : h.getRemoteAddr();
        return new RequestMeta(ip, h.getHeader("User-Agent"));
    }

    /**
     * Perfil del token actual: requiere Bearer válido (401 vía entry point si no).
     */
    /**
     * Acepta el Authentication del filtro (producción) o un Principal directo
     * (tests).
     */
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(java.security.Principal principal) {
        Object source = principal instanceof org.springframework.security.core.Authentication auth
                ? auth.getPrincipal()
                : principal;
        if (source instanceof UserPrincipal up) {
            return ResponseEntity.ok(authService.me(up));
        }
        return ResponseEntity.status(401).build();
    }
}
