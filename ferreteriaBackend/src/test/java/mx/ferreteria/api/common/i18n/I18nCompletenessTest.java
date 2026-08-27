package mx.ferreteria.api.common.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.PropertyResourceBundle;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Toda clave debe existir en AMBOS bundles, sin blancos (PLAN §4.4). */
class I18nCompletenessTest {

    private static Set<String> keys(String bundle) throws Exception {
        try (InputStream in = I18nCompletenessTest.class
                .getResourceAsStream("/i18n/messages_" + bundle + ".properties")) {
            assertThat(in).as("bundle %s existe", bundle).isNotNull();
            var rb = new PropertyResourceBundle(in);
            var keys = new HashSet<String>();
            for (var k = rb.getKeys(); k.hasMoreElements();) {
                keys.add(k.nextElement());
            }
            return keys;
        }
    }

    @Test
    @DisplayName("keysets de es y en son IDENTICOS (en ambas direcciones)")
    void bundles_haveIdenticalKeysets() throws Exception {
        Set<String> es = keys("es");
        Set<String> en = keys("en");
        assertThat(es).containsExactlyInAnyOrderElementsOf(en);
    }

    @Test
    @DisplayName("cada ErrorCode.key() existe en ambos bundles — y ningun key huerfano")
    void enumKeys_matchBundlesExactly() throws Exception {
        Set<String> expected = new HashSet<>();
        for (ErrorCode c : ErrorCode.values()) {
            expected.add(c.key());
        }
        assertThat(keys("es")).as("bundle es == ErrorCode.key()").isEqualTo(expected);
        assertThat(keys("en")).as("bundle en == ErrorCode.key()").isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    @DisplayName("ningun mensaje va vacio en es ni en en")
    void noBlankMessages(ErrorCode code) throws Exception {
        try (InputStream inEs = getClass().getResourceAsStream("/i18n/messages_es.properties");
             InputStream inEn = getClass().getResourceAsStream("/i18n/messages_en.properties")) {
            var es = new PropertyResourceBundle(inEs);
            var en = new PropertyResourceBundle(inEn);
            assertThat(es.getString(code.key())).isNotBlank();
            assertThat(en.getString(code.key())).isNotBlank();
        }
    }

    @Test
    @DisplayName("los args {0},{1} usados por el codigo existen en el patron del mensaje")
    void placeholders_areConsistent() throws Exception {
        // stock-insuficiente usa {0} producto y {1} disponible
        try (InputStream in = getClass().getResourceAsStream("/i18n/messages_es.properties")) {
            var es = new PropertyResourceBundle(in);
            String pattern = es.getString(ErrorCode.STOCK_INSUFICIENTE.key());
            assertThat(pattern).contains("{0}").contains("{1}");
            String paginacion = es.getString(ErrorCode.PAGINACION_INVALIDA.key());
            long placeholders = Arrays.stream(paginacion.split("\\{"))
                    .skip(1).count();
            assertThat(placeholders).isGreaterThanOrEqualTo(3);
        }
    }
}
