/**
 * Capacidad de cámara del navegador.
 *
 * `false` cuando el navegador bloquea la cámara, típico en `http://` de LAN
 * (`navigator.mediaDevices` solo existe en contexto seguro: HTTPS o
 * localhost). Ver PLAN_APP_MOVIL (HTTPS con mkcert).
 */
export function camaraDisponible(): boolean {
	return (
		typeof navigator !== "undefined" &&
		!!navigator.mediaDevices?.getUserMedia
	);
}
