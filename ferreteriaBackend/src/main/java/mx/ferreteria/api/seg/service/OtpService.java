package mx.ferreteria.api.seg.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.JwtService;
import mx.ferreteria.api.common.security.OtpProperties;
import mx.ferreteria.api.seg.service.AuthService.LoginResult;

/**
 * Segundo factor OTP (6 dígitos, un solo uso, TTL 5 min, máx 5 intentos).
 * El código se guarda hasheado (SHA-256); la comparación es en tiempo
 * constante. Respuestas genéricas anti-enumeración: un challengeId
 * desconocido se rechaza igual que un código erróneo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpGateway otp;
    private final AuthUserGateway usuarios;
    private final AuthService authService;
    private final OtpProperties props;
    private final OtpEmailSender emailSender;
    private final OtpWhatsappPort whatsapp;

    private final SecureRandom aleatorio = new SecureRandom();

    /**
     * Metadatos del desafío (canales + destinos enmascarados) para pintar la
     * pantalla OTP. Lo usa el callback de Google, que solo recibe el id.
     */
    @Transactional(readOnly = true)
    public mx.ferreteria.api.seg.dto.AuthDtos.OtpChallengeResponse desafio(String challengeId) {
        var d = otp.findByChallengeId(challengeId).orElse(null);
        if (d == null || !d.vigente(Instant.now())) {
            throw new ValidacionException(ErrorCode.OTP_INVALIDO);
        }
        return authService.desafioPara(d.usuarioId(), challengeId);
    }

    /**
     * Envía (o reenvía) el código por el canal elegido. Siempre responde OK
     * cuando el desafío existe: si el canal no tiene destino registrado se
     * informa con CANAL_NO_DISPONIBLE para que el usuario elija otro.
     */
    @Transactional
    public void enviar(String challengeId, String canal) {
        var desafio = otp.findByChallengeId(challengeId).orElse(null);
        if (desafio == null || !desafio.vigente(Instant.now()) || desafio.consumidoEn() != null) {
            // Anti-enumeración: no revelar si el challenge existe.
            throw new ValidacionException(ErrorCode.OTP_INVALIDO);
        }
        if (!"email".equals(canal) && !"whatsapp".equals(canal)) {
            throw new ValidacionException(ErrorCode.VALOR_INVALIDO, "canal");
        }
        if (desafio.enviadoEn() != null && desafio.enviadoEn()
                .plusSeconds(props.reenvioSegundos()).isAfter(Instant.now())) {
            throw new ValidacionException(ErrorCode.LIMITE_VELOCIDAD_EXCEDIDO,
                    String.valueOf(props.reenvioSegundos()));
        }
        var contactos = usuarios.contactosDe(desafio.usuarioId()).orElse(null);
        String codigo = generarCodigo();
        String hash = JwtService.sha256Base64(codigo);

        if ("email".equals(canal)) {
            String email = contactos == null ? null : contactos.email();
            if (email == null || email.isBlank()) {
                throw new ValidacionException(ErrorCode.CANAL_NO_DISPONIBLE);
            }
            emailSender.enviarCodigo(email, codigo, props.ttlMinutos());
        } else if ("whatsapp".equals(canal)) {
            String numero = contactos == null ? null
                    : (contactos.whatsapp() != null && !contactos.whatsapp().isBlank()
                            ? contactos.whatsapp() : contactos.telefono());
            if (numero == null || numero.isBlank()) {
                throw new ValidacionException(ErrorCode.CANAL_NO_DISPONIBLE);
            }
            if (!enviarWhatsApp(numero, codigo)) {
                throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
            }
        }
        otp.fijarCodigo(challengeId, canal, hash,
                Instant.now().plus(Duration.ofMinutes(props.ttlMinutos())));
        log.info("otp enviado usuario_id={} canal={}", desafio.usuarioId(), canal);
    }

    /**
     * Verifica el código y, si es correcto, emite la sesión (par at/rt).
     * Agota intentos → revoca el desafío (OTP_AGOTADO); expirado →
     * OTP_EXPIRADO; erróneo → OTP_INVALIDO.
     */
    @Transactional
    public LoginResult verificar(String challengeId, String codigo, RequestMeta meta) {
        var desafio = otp.findByChallengeId(challengeId).orElse(null);
        if (desafio == null || desafio.consumidoEn() != null || desafio.revocadoEn() != null
                || desafio.codigoHash() == null) {
            throw new ValidacionException(ErrorCode.OTP_INVALIDO);
        }
        if (!desafio.expiraEn().isAfter(Instant.now())) {
            otp.revocar(challengeId);
            throw new ValidacionException(ErrorCode.OTP_EXPIRADO);
        }
        if (desafio.intentos() >= props.maxIntentos()) {
            otp.revocar(challengeId);
            throw new ValidacionException(ErrorCode.OTP_AGOTADO);
        }
        String hash = JwtService.sha256Base64(codigo);
        if (!constanteIgual(hash, desafio.codigoHash())) {
            otp.incrementarIntentos(challengeId);
            int restantes = props.maxIntentos() - desafio.intentos() - 1;
            if (restantes <= 0) {
                otp.revocar(challengeId);
                throw new ValidacionException(ErrorCode.OTP_AGOTADO);
            }
            throw new ValidacionException(ErrorCode.OTP_INVALIDO);
        }
        otp.consumir(challengeId);
        log.info("otp verificado usuario_id={} canal={}", desafio.usuarioId(), desafio.canal());
        return authService.emitirSesion(desafio.usuarioId(), meta);
    }

    private boolean enviarWhatsApp(String numero, String codigo) {
        String texto = "El Tornillo Feliz: tu codigo de verificacion es " + codigo
                + ". Vence en " + props.ttlMinutos()
                + " minutos. Si no lo solicitaste, ignora este mensaje.";
        return whatsapp.enviarTexto(numero, texto);
    }

    String generarCodigo() {
        int limite = (int) Math.pow(10, props.longitud());
        int n = aleatorio.nextInt(limite);
        return String.format("%0" + props.longitud() + "d", n);
    }

    private static boolean constanteIgual(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
