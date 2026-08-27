package mx.ferreteria.api.common.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import mx.ferreteria.api.common.error.PaginacionInvalidException;
import mx.ferreteria.api.common.i18n.ErrorCode;

/**
 * Validación centralizada de parámetros de paginación (PLAN §4.1).
 * ?page=0&size=20&sort=campo,desc  |  max size=100  |  page<0 o size<=0 → 400.
 */
public record PageQuery(int page, int size, String sort) {

    private static final int MAX_SIZE = 100;

    public static PageQuery of(Integer page, Integer size, String sort) {
        return new PageQuery(
                page == null ? 0 : page,
                size == null ? 20 : size,
                sort == null ? "" : sort);
    }

    public Pageable toPageable() {
        if (page < 0) {
            throw new PaginacionInvalidException(ErrorCode.PAGINACION_INVALIDA, "page", page, ">=0");
        }
        if (size <= 0 || size > MAX_SIZE) {
            throw new PaginacionInvalidException(ErrorCode.PAGINACION_INVALIDA, "size", size, "1-" + MAX_SIZE);
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
