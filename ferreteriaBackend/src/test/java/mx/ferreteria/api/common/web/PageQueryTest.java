package mx.ferreteria.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import mx.ferreteria.api.common.error.PaginacionInvalidException;

class PageQueryTest {

    // ── Default values ──────────────────────────────────────────────

    @Test
    @DisplayName("null page -> 0, null size -> 20, null sort -> blank")
    void of_nullDefaults() {
        PageQuery pq = PageQuery.of(null, null, null);
        assertThat(pq.page()).isZero();
        assertThat(pq.size()).isEqualTo(20);
        assertThat(pq.sort()).isEmpty();
    }

    // ── Invalid page ────────────────────────────────────────────────

    @Test
    @DisplayName("page < 0 throws PaginacionInvalidException")
    void toPageable_negativePage_throws() {
        PageQuery pq = PageQuery.of(-1, 20, null);
        assertThatThrownBy(pq::toPageable)
                .isInstanceOf(PaginacionInvalidException.class);
    }

    // ── Invalid size ────────────────────────────────────────────────

    @Test
    @DisplayName("size 0 throws PaginacionInvalidException")
    void toPageable_zeroSize_throws() {
        PageQuery pq = PageQuery.of(0, 0, null);
        assertThatThrownBy(pq::toPageable)
                .isInstanceOf(PaginacionInvalidException.class);
    }

    @Test
    @DisplayName("size -1 throws PaginacionInvalidException")
    void toPageable_negativeSize_throws() {
        PageQuery pq = PageQuery.of(0, -1, null);
        assertThatThrownBy(pq::toPageable)
                .isInstanceOf(PaginacionInvalidException.class);
    }

    @Test
    @DisplayName("size por encima del tope por defecto (501) throws PaginacionInvalidException")
    void toPageable_overMaxSize_throws() {
        PageQuery pq = PageQuery.of(0, PageQuery.DEFAULT_MAX_SIZE + 1, null);
        assertThatThrownBy(pq::toPageable)
                .isInstanceOf(PaginacionInvalidException.class);
    }

    // ── Valid size boundary ─────────────────────────────────────────

    @Test
    @DisplayName("size DEFAULT_MAX_SIZE es válido (tope)")
    void toPageable_maxSize_isValid() {
        PageQuery pq = PageQuery.of(0, PageQuery.DEFAULT_MAX_SIZE, null);
        assertThat(pq.toPageable().getPageSize()).isEqualTo(PageQuery.DEFAULT_MAX_SIZE);
    }

    @Test
    @DisplayName("size 1 is valid (min allowed)")
    void toPageable_minSize_isValid() {
        PageQuery pq = PageQuery.of(0, 1, null);
        assertThat(pq.toPageable().getPageSize()).isEqualTo(1);
    }

    // ── Sort parsing ────────────────────────────────────────────────

    @Test
    @DisplayName("'nombre,desc' -> Sort DESC on nombre")
    void toPageable_sortDesc() {
        PageQuery pq = PageQuery.of(0, 10, "nombre,desc");
        Sort sort = pq.toPageable().getSort();
        assertThat(sort.getOrderFor("nombre")).isNotNull();
        assertThat(sort.getOrderFor("nombre").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("'nombre' (no direction) -> Sort ASC on nombre")
    void toPageable_sortDefaultAsc() {
        PageQuery pq = PageQuery.of(0, 10, "nombre");
        Sort sort = pq.toPageable().getSort();
        assertThat(sort.getOrderFor("nombre")).isNotNull();
        assertThat(sort.getOrderFor("nombre").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("'nombre,asc' -> Sort ASC on nombre")
    void toPageable_sortExplicitAsc() {
        PageQuery pq = PageQuery.of(0, 10, "nombre,asc");
        Sort sort = pq.toPageable().getSort();
        assertThat(sort.getOrderFor("nombre")).isNotNull();
        assertThat(sort.getOrderFor("nombre").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("blank sort -> Sort.unsorted")
    void toPageable_blankSort_unsorted() {
        PageQuery pq = PageQuery.of(0, 10, "   ");
        assertThat(pq.toPageable().getSort()).isEqualTo(Sort.unsorted());
    }

    @Test
    @DisplayName("sort with whitespace around direction is trimmed")
    void toPageable_sortWithSpaces() {
        PageQuery pq = PageQuery.of(0, 10, "nombre , desc ");
        Sort sort = pq.toPageable().getSort();
        assertThat(sort.getOrderFor("nombre").getDirection()).isEqualTo(Sort.Direction.DESC);
    }
}
