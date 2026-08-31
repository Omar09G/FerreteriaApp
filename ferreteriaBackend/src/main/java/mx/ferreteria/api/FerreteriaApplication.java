package mx.ferreteria.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

@SpringBootApplication
@ConfigurationPropertiesScan
// Serializar paginas como PagedModel (estable) en lugar de PageImpl "as-is"
// (evita el warning de SpringDataJacksonConfiguration$PageModule).
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class FerreteriaApplication {

    public static void main(String[] args) {
        SpringApplication.run(FerreteriaApplication.class, args);
    }
}
