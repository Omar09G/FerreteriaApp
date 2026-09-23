package mx.ferreteria.api.cat.service;

import java.math.BigDecimal;

/**
 * Puerto de sincronización de línea de crédito (lado consumidor). Vive en
 * {@code cat} a propósito: si {@code cat} importara el repositorio de
 * {@code ven} directamente se forma el ciclo cat↔ven que prohíbe
 * {@code modulosSinCiclos}. El adapter en {@code ven} implementa este
 * contrato (la arista ven→cat ya existe y es unidireccional).
 */
public interface CreditoPort {

    /**
     * Crea o actualiza la línea ACTIVA del cliente. No-op si limite es
     * nulo o ≤ 0.
     *
     * @param clienteId     cliente dueño de la línea
     * @param limiteCredito monto autorizado (≤0 = no-op)
     * @param diasCredito   días (null/≤0 = 15; se acota a 1..365)
     */
    void sincronizarLinea(Long clienteId, BigDecimal limiteCredito, Integer diasCredito);
}
