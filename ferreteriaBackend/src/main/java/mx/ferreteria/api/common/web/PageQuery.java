package mx.ferreteria.api.common.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import mx.ferreteria.api.common.error.PaginacionInvalidException;
import mx.ferreteria.api.common.i18n.ErrorCode;

/**
 * Validación centralizada de parámetros de paginación (PLAN §4.1).
 * ?page=0&size=20&sort=campo,desc  |  max size configurable  |  page<0 o size<=0 → 400.
 */
public record PageQuery(int page, int size, String sort) {

    /**
     * Tope por defecto para listas generales. Endpoints de catálogo pequeño
     * (marcas, unidades de medida, categorías) suelen pedir todo en una sola
     * llamada; si necesitas más, expón un parámetro o un endpoint sin paginar.
     */
    public static final int DEFAULT_MAX_SIZE = 500;

    public static PageQuery of(Integer page, Integer size, String sort) {
        return new PageQuery(
                page == null ? 0 : page,
                size == null ? 20 : size,
                sort == null ? "" : sort);
    }

    public Pageable toPageable() {
        return toPageable(DEFAULT_MAX_SIZE);
    }

    public Pageable toPageable(int maxSize) {
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
            Sort.Direction dir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                    ? Sort.Direction.DESC : Sort.Direction.ASC;
            s = Sort.by(dir, prop);
        }
        return PageRequest.of(page, size, s);
    }
}
