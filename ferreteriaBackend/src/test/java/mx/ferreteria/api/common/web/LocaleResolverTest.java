package mx.ferreteria.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class LocaleResolverTest {

    private MockHttpServletRequest req() {
        return new MockHttpServletRequest("GET", "/api/v1/x");
    }

    @Test
    @DisplayName("Sin lang ni Accept-Language: default es-MX")
    void sinLangSinHeader_devuelveDefault() {
        MockHttpServletRequest r = req();
        assertThat(LocaleResolver.resolve(r)).isEqualTo(LocaleResolver.DEFAULT_LOCALE);
    }

    @Test
    @DisplayName("lang=es explicito: es-MX")
    void langEsDevuelveEsMx() {
        MockHttpServletRequest r = req();
        r.addParameter("lang", "es");
        assertThat(LocaleResolver.resolve(r)).isEqualTo(LocaleResolver.DEFAULT_LOCALE);
    }

    @Test
    @DisplayName("lang=en explicito: en")
    void langEnDevuelveEn() {
        MockHttpServletRequest r = req();
        r.addParameter("lang", "en");
        assertThat(LocaleResolver.resolve(r)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("lang case-insensitive: ES / En")
    void langCaseInsensitive() {
        MockHttpServletRequest r1 = req();
        r1.addParameter("lang", "ES");
        assertThat(LocaleResolver.resolve(r1)).isEqualTo(LocaleResolver.DEFAULT_LOCALE);

        MockHttpServletRequest r2 = req();
        r2.addParameter("lang", "En");
        assertThat(LocaleResolver.resolve(r2)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("Accept-Language en-* sin lang: en")
    void acceptLanguageEnSinLang() {
        MockHttpServletRequest r = req();
        r.addHeader("Accept-Language", "en-US,en;q=0.9");
        assertThat(LocaleResolver.resolve(r)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("Precedencia: lang=en sobre Accept-Language es")
    void langSobreAcceptLanguage() {
        MockHttpServletRequest r = req();
        r.addParameter("lang", "en");
        r.addHeader("Accept-Language", "es-MX,es;q=0.9");
        assertThat(LocaleResolver.resolve(r)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("Atributo de request cacheado se respeta (camino caliente)")
    void atributoCacheadoSeRespeta() {
        MockHttpServletRequest r = req();
        r.setAttribute(LocaleResolver.ATTR_LOCALE, Locale.ENGLISH);
        assertThat(LocaleResolver.resolve(r)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("validateLangParam sin lang: valido, locale por header")
    void validateSinLang() {
        MockHttpServletRequest r = req();
        r.addHeader("Accept-Language", "en-US");
        LocaleResolver.Validation v = LocaleResolver.validateLangParam(r);
        assertThat(v.valid()).isTrue();
        assertThat(v.invalidValue()).isNull();
        assertThat(v.resolved()).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("validateLangParam con lang invalido: invalido con valor crudo")
    void validateInvalido() {
        MockHttpServletRequest r = req();
        r.addParameter("lang", "fr");
        LocaleResolver.Validation v = LocaleResolver.validateLangParam(r);
        assertThat(v.valid()).isFalse();
        assertThat(v.invalidValue()).isEqualTo("fr");
        assertThat(v.resolved()).isEqualTo(LocaleResolver.DEFAULT_LOCALE);
    }

    @Test
    @DisplayName("validateLangParam con lang vacio: valido por header")
    void validateVacio() {
        MockHttpServletRequest r = req();
        r.addParameter("lang", "");
        LocaleResolver.Validation v = LocaleResolver.validateLangParam(r);
        assertThat(v.valid()).isTrue();
        assertThat(v.invalidValue()).isNull();
    }

    @Test
    @DisplayName("isSupported: solo es/en admitidos")
    void isSupported() {
        assertThat(LocaleResolver.isSupported("es")).isTrue();
        assertThat(LocaleResolver.isSupported("EN")).isTrue();
        assertThat(LocaleResolver.isSupported("fr")).isFalse();
        assertThat(LocaleResolver.isSupported(null)).isFalse();
        assertThat(LocaleResolver.isSupported("")).isFalse();
        assertThat(LocaleResolver.isSupported(" es ")).isTrue();
    }
}
