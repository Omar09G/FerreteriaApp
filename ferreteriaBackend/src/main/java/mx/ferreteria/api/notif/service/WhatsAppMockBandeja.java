package mx.ferreteria.api.notif.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Bandeja en memoria del mock de WhatsApp (par de Mailpit para email):
 * captura lo que el sender mock "enviaría" para verificarlo en pruebas
 * y depuración local. Sin persistencia; se vacía al reiniciar.
 */
@Component
public class WhatsAppMockBandeja {

    public record MensajeMock(String numero, String asunto, String nombreArchivo, byte[] pdf) {
    }

    private final List<MensajeMock> mensajes = new ArrayList<>();

    public synchronized void registrar(String numero, String asunto, String nombreArchivo, byte[] pdf) {
        mensajes.add(new MensajeMock(numero, asunto, nombreArchivo, pdf));
    }

    public synchronized List<MensajeMock> mensajes() {
        return List.copyOf(mensajes);
    }

    public synchronized void limpiar() {
        mensajes.clear();
    }
}
