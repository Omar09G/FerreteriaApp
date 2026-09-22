package mx.ferreteria.api;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;
import org.springframework.scheduling.annotation.EnableScheduling;

import mx.ferreteria.api.common.time.ZonaHoraria;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class FerreteriaApplication {

    public static void main(String[] args) {
        // Pin de zona JVM: LocalDate.now() residual, entidades y librerías
        // (que no reciben ZoneId explícito) deben operar en la zona del
        // negocio aunque el contenedor arranque en UTC. Ver ZonaHoraria.
        TimeZone.setDefault(TimeZone.getTimeZone(ZonaHoraria.ZONA));
        SpringApplication.run(FerreteriaApplication.class, args);
    }
}
