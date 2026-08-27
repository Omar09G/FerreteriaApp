package mx.ferreteria.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class EnvelopeAdviceTest {

    private final EnvelopeAdvice advice = new EnvelopeAdvice();

    @SuppressWarnings("unchecked")
    private Map<String, Object> wrap(Object body) {
        return (Map<String, Object>) advice.beforeBodyWrite(body, null,
                org.springframework.http.MediaType.APPLICATION_JSON,
                org.springframework.http.converter.json.MappingJackson2HttpMessageConverter.class,
                null, null);
    }

    @Test
    @DisplayName("Objeto normal: se envuelve en {success:true, data:body}")
    void wrapsPlainObject() {
        Map<String, String> body = Map.of("username", "admin");
        Map<String, Object> result = wrap(body);
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("data")).isEqualTo(body);
        assertThat(result.containsKey("meta")).isFalse();
    }

    @Test
    @DisplayName("Map con success:false (error handler): no se re-envuelve")
    void errorMapPassesThrough() {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("errorCode", 400);
        error.put("codigo", "CAMPO_REQUERIDO");
        Object result = advice.beforeBodyWrite(error, null,
                org.springframework.http.MediaType.APPLICATION_JSON,
                org.springframework.http.converter.json.MappingJackson2HttpMessageConverter.class,
                null, null);
        assertThat(result).isSameAs(error);
    }

    @Test
    @DisplayName("Page de Spring Data: data=content, meta con paginacion")
    void pageExtractsContentAndMeta() {
        var page = new PageImpl<>(List.of("a", "b"),
                PageRequest.of(1, 10, Sort.unsorted()), 25);
        Map<String, Object> result = wrap(page);
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("data")).isEqualTo(List.of("a", "b"));

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) result.get("meta");
        assertThat(meta).isNotNull();
        assertThat(meta.get("page")).isEqualTo(1);
        assertThat(meta.get("size")).isEqualTo(10);
        assertThat(meta.get("totalElements")).isEqualTo(25L);
        assertThat(meta.get("totalPages")).isEqualTo(3);
    }

    @Test
    @DisplayName("null body: se envuelve con data=null")
    void nullBodyWrapped() {
        Map<String, Object> result = wrap(null);
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("data")).isNull();
    }
}
