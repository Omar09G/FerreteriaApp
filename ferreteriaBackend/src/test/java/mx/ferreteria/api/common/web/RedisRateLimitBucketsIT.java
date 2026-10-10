package mx.ferreteria.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * BACK-ESC-001: dos instancias (simulan dos réplicas del backend) contra el
 * mismo Redis comparten el presupuesto: lo que consume una lo ve la otra.
 * Se salta sin socket Docker/Podman (igual que los demás *IT).
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisRateLimitBucketsIT {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static String uri() {
        return "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379);
    }

    @Test
    @DisplayName("Dos proveedores contra el mismo Redis comparten el bucket")
    void dosProveedoresCompartenPresupuesto() {
        var grupo = new RateLimitProperties.Grupo(2, 1);
        var replicaA = new RedisRateLimitBuckets(uri());
        var replicaB = new RedisRateLimitBuckets(uri());
        try {
            String clave = "it:compartido:" + UUID.randomUUID();

            assertThat(replicaA.resolve(clave, grupo).tryConsume(1)).isTrue();
            assertThat(replicaA.resolve(clave, grupo).tryConsume(1)).isTrue();

            // Presupuesto agotado: la OTRA réplica también lo ve agotado.
            assertThat(replicaB.resolve(clave, grupo).tryConsume(1)).isFalse();

            // Otra clave no afectada.
            assertThat(replicaB.resolve(clave + ":otra", grupo).tryConsume(1)).isTrue();
        } finally {
            replicaA.close();
            replicaB.close();
        }
    }

    @Test
    @DisplayName("La factoría enruta a Redis con distributed=true y URI válida")
    void factoriaEnrutaARedis() {
        var props = new RateLimitProperties(
                true, true, 100L, 5L, true, false, uri(), null);
        RateLimitBuckets buckets = RateLimitBucketsFactory.create(props);
        try {
            assertThat(buckets).isInstanceOf(RedisRateLimitBuckets.class);
        } finally {
            buckets.close();
        }
    }

    @Test
    @DisplayName("configPara traslada capacidad y recarga del grupo")
    void configParaRespetaGrupo() {
        var config = RedisRateLimitBuckets.configPara(new RateLimitProperties.Grupo(7, 3));

        assertThat(config.getBandwidths()).hasSize(1);
        assertThat(config.getBandwidths()[0].getCapacity()).isEqualTo(7);
    }
}
