package mx.ferreteria.api.common.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Selecciona el perfil de rate limiting (PLAN M7 §"rate limiting por controller").
 * El valor apunta a una entrada de {@code app.rate-limit.grupos} (configurable por .env).
 * Sin anotacion se aplica el perfil {@code "default"}.
 * Aplicable a clase (afecta a todos sus metodos) o a metodo especifico (sobreescribe el de la clase).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimited {
    String value() default "default";
}
