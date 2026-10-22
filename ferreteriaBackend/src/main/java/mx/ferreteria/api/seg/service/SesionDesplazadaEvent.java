package mx.ferreteria.api.seg.service;

/**
 * Dominio seguridad: la sesión de un usuario fue desplazada por un takeover
 * single-session (nuevo login en otro equipo con confirmación explícita). El
 * módulo de notificaciones lo consume AFTER_COMMIT para empujar el cierre en
 * tiempo real a las pestañas conectadas de las sesiones anteriores.
 * Vive en seg para que seguridad no dependa del módulo notif (regla
 * arquitectónica modulosSinCiclos: la dependencia va notif → seg).
 *
 * @param usuarioId      dueño de las sesiones.
 * @param nuevaSesionId  sesión recién creada (se excluye del push: el equipo
 *                       que acaba de entrar no debe auto-expulsarse).
 * @param ubicacionNueva ciudad/región/país del nuevo inicio, o null.
 */
public record SesionDesplazadaEvent(int usuarioId, int nuevaSesionId, String ubicacionNueva) {
}
