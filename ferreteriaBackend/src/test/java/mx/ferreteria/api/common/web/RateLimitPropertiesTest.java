package mx.ferreteria.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RateLimitPropertiesTest {

    @Test
    @DisplayName("Sin grupos configurados: cae al fallback interno")
    void sinGruposCaeAFallbackInterno() {
        RateLimitProperties props = new RateLimitProperties(true, true, 100L, 5L, null);
        RateLimitProperties.Grupo g = props.grupo("cualquiera");

        assertThat(g.capacity()).isEqualTo(120);
        assertThat(g.refillPerSecond()).isEqualTo(5);
    }

    @Test
    @DisplayName("Mapa vacio: cae al fallback interno")
    void mapaVacioCaeAFallbackInterno() {
        RateLimitProperties props = new RateLimitProperties(true, true, 100L, 5L, Map.of());
        assertThat(props.grupo("auth").capacity()).isEqualTo(120);
        assertThat(props.grupo("auth").refillPerSecond()).isEqualTo(5);
    }

    @Test
    @DisplayName("Perfil exacto: usa el grupo configurado")
    void perfilExactoResuelto() {
        Map<String, RateLimitProperties.Grupo> grupos = new HashMap<>();
        grupos.put("auth", new RateLimitProperties.Grupo(10, 1));
        RateLimitProperties props = new RateLimitProperties(true, true, 100L, 5L, grupos);

        RateLimitProperties.Grupo g = props.grupo("auth");
        assertThat(g.capacity()).isEqualTo(10);
        assertThat(g.refillPerSecond()).isEqualTo(1);
    }

    @Test
    @DisplayName("Perfil desconocido pero default presente: usa default")
    void perfilDesconocidoCaeADefault() {
        Map<String, RateLimitProperties.Grupo> grupos = new HashMap<>();
        grupos.put("default", new RateLimitProperties.Grupo(300, 10));
        RateLimitProperties props = new RateLimitProperties(true, true, 100L, 5L, grupos);

        RateLimitProperties.Grupo g = props.grupo("catalogo");
        assertThat(g.capacity()).isEqualTo(300);
        assertThat(g.refillPerSecond()).isEqualTo(10);
    }

    @Test
    @DisplayName("Sin default ni perfil exacto: fallback interno")
    void sinDefaultUsaFallbackInterno() {
        Map<String, RateLimitProperties.Grupo> grupos = new HashMap<>();
        grupos.put("catalogo", new RateLimitProperties.Grupo(1200, 40));
        RateLimitProperties props = new RateLimitProperties(true, true, 100L, 5L, grupos);

        RateLimitProperties.Grupo g = props.grupo("otro");
        assertThat(g.capacity()).isEqualTo(120);
        assertThat(g.refillPerSecond()).isEqualTo(5);
    }

    @Test
    @DisplayName("Preferir perfil exacto sobre default")
    void perfilExactoGanaSobreDefault() {
        Map<String, RateLimitProperties.Grupo> grupos = new HashMap<>();
        grupos.put("auth", new RateLimitProperties.Grupo(10, 1));
        grupos.put("default", new RateLimitProperties.Grupo(300, 10));
        RateLimitProperties props = new RateLimitProperties(true, true, 100L, 5L, grupos);

        assertThat(props.grupo("auth").capacity()).isEqualTo(10);
        assertThat(props.grupo("otro").capacity()).isEqualTo(300);
    }
}
