package mx.ferreteria.api.common.api;

import java.io.IOException;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.storage.FotoStoragePort;
import mx.ferreteria.api.common.web.RateLimited;

/**
 * Subida de fotos de entidades (clientes, proveedores, empleados, productos).
 * El archivo se almacena en MinIO renombrado a UUID + extensión y se devuelve
 * la URL pública; el frontend la incluye como {@code fotoUrl}/{@code imagenUrl}
 * en el create/update de la entidad. Escribe cualquier rol operativo con
 * formularios que acepten foto.
 */
@RestController
@RequestMapping("/api/v1/archivos")
@RequiredArgsConstructor
@RateLimited("default")
public class ArchivoController {

    private final FotoStoragePort storage;

    public record ImagenResponse(String url) {
    }

    @PostMapping(value = "/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','VENDEDOR','ENCARGADO_CAJA','ALMACENISTA','AUDITOR')")
    public ImagenResponse subirImagen(@RequestParam("archivo") MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ValidacionException(ErrorCode.CAMPO_REQUERIDO, "archivo");
        }
        try {
            String url = storage.subir(archivo.getContentType(), archivo.getOriginalFilename(),
                    archivo.getInputStream(), archivo.getSize());
            return new ImagenResponse(url);
        } catch (IOException e) {
            throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
        }
    }
}
