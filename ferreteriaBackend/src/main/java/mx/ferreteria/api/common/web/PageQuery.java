package mx.ferreteria.api.common.web;

import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import mx.ferreteria.api.common.error.PaginacionInvalidException;
import mx.ferreteria.api.common.i18n.ErrorCode;

/**
 * Validación centralizada de parámetros de paginación (PLAN §4.1).
 * <p>
 * ?page=0&size=20&sort=campo,asc  |  max size configurable  |  page<0 o size<=0 → 400.
 * <p>
 * BACK-SEC-009: el parámetro {@code sort} se valida contra una whitelist
 * para evitar SQL-bound DoS (columnas inexistentes) o enumeración de esquema.
 * Si el caller pide una columna no permitida, se ignora (sin error) y se usa
 * el sort por defecto del repositorio — esto preserva UX sin abrir superficie.
 */
public record PageQuery(int page, int size, String sort) {

    /**
     * Tope por defecto para listas generales. Endpoints de catálogo pequeño
     * (marcas, unidades de medida, categorías) suelen pedir todo en una sola
     * llamada; si necesitas más, expón un parámetro o un endpoint sin paginar.
     */
    public static final int DEFAULT_MAX_SIZE = 500;

    /**
     * Whitelist global de campos ordenables. Si una feature requiere otra columna,
     * declarala explicitamente via {@link #toPageable(int, Set)}.
     * Reglas de inclusion:
     * <ul>
     * <li>snake_case (mapeo directo a columna PostgreSQL)</li>
     * <li>NO incluir columnas sensibles: passwordHash, password, token</li>
     * <li>preferir columnas con índice</li>
     * </ul>
     */
    public static final Set<String> DEFAULT_SORT_FIELDS = Set.of(
            // ven (ventas, devoluciones, cotizaciones, rentas, creditos)
            "venta_id", "folio", "fecha", "fecha_local", "estado", "total",
            "subtotal", "iva", "descuento_total", "usuario_id", "turno_caja_id",
            "creado_en", "actualizado_en", "vigencia_desde", "vigencia_hasta",
            // com (compras, proveedores, pagos)
            "compra_id", "fecha_compra", "monto_total", "monto_pagado",
            "proveedor_id", "razon_social", "rfc", "email",
            // inv (inventario, productos, almacenes, movimientos)
            "producto_id", "codigo", "nombre", "costo_actual", "precio_menudeo",
            "precio_mayoreo", "categoria_id", "marca_id", "almacen_id",
            "stock", "stock_minimo", "cantidad", "fecha_movimiento",
            // cat (catalogos)
            "clave", "tipo", "activo", "orden",
            // rh (empleados, nominas)
            "empleado_id", "numero_empleado", "puesto_id", "fecha_ingreso",
            "fecha_baja", "salario", "periodo_inicio", "periodo_fin",
            // seg (usuarios, roles, sesiones, auditoria) - usuario_id ya arriba
            "username", "ultimo_login",
            "fecha_evento", "ip_address", "session_id",
            // fin (cajas, turnos, cortes, movimientos_caja) - turno_caja_id y creado_en ya arriba
            "caja_id", "corte_id", "movimiento_id",
            "monto_apertura", "monto_esperado", "monto_contado",
            "diferencia", "apertura_en", "cierre_en",
            "concepto", "monto",
            // fis (facturas)
            "factura_id", "uuid", "folio_fiscal", "tipo_comprobante",
            "fecha_timbrado", "estado_sat"
    );

    public static PageQuery of(Integer page, Integer size, String sort) {
        return new PageQuery(
                page == null ? 0 : page,
                size == null ? 20 : size,
                sort == null ? "" : sort);
    }

    public Pageable toPageable() {
        return toPageable(DEFAULT_MAX_SIZE, DEFAULT_SORT_FIELDS);
    }

    public Pageable toPageable(int maxSize) {
        return toPageable(maxSize, DEFAULT_SORT_FIELDS);
    }

    /**
     * Variante con whitelist custom para controllers que quieran exponer
     * columnas adicionales (p. ej. catalogos dinamicos).
     */
    public Pageable toPageable(int maxSize, Set<String> allowedSortFields) {
        if (page < 0) {
            throw new PaginacionInvalidException(ErrorCode.PAGINACION_INVALIDA, "page", page, 0, maxSize);
        }
        if (size <= 0 || size > maxSize) {
            throw new PaginacionInvalidException(ErrorCode.PAGINACION_INVALIDA, "size", size, 1, maxSize);
        }
        Sort s = Sort.unsorted();
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            String prop = parts[0].trim();
            if (allowedSortFields.contains(prop)) {
                Sort.Direction dir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                        ? Sort.Direction.DESC : Sort.Direction.ASC;
                s = Sort.by(dir, prop);
            }
            // Si no esta en whitelist, sort queda unsorted (UX: peticiones
            // malformadas devuelven resultados validos en orden por defecto).
        }
        return PageRequest.of(page, size, s);
    }
}