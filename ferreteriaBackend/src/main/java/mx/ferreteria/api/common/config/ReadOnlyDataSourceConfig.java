package mx.ferreteria.api.common.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * PASO 23: Read/replica datasource para reportes (DB-ESC-004).
 * ReporteService usa JdbcTemplate contra la replica para no competir
 * con el POS por el pool de PgBouncer primario.
 *
 * Si app.datasource.read-only.url no esta configurada (default ""), los beans
 * no se crean y ReporteService hace fallback al datasource primario.
 */
@Configuration
public class ReadOnlyDataSourceConfig {

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("'${app.datasource.read-only.url:}' != ''")
    public DataSource readOnlyDataSource(
            @Value("${app.datasource.read-only.url}") String url,
            @Value("${app.datasource.read-only.username}") String username,
            @Value("${app.datasource.read-only.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(1);
        ds.setConnectionTimeout(3000);
        ds.setMaxLifetime(1700000);
        return ds;
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("'${app.datasource.read-only.url:}' != ''")
    public JdbcTemplate readOnlyJdbcTemplate(@Qualifier("readOnlyDataSource") DataSource readOnlyDataSource) {
        return new JdbcTemplate(readOnlyDataSource);
    }
}