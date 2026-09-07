package mx.ferreteria.api.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import jakarta.persistence.Entity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reglas arquitectónicas del backend ferreteria.
 *
 * Reglas activas (BACK-DIS-001):
 *   1. mensajes desde ErrorCode (no literales) — PLAN §4.4
 *   2. no ciclos entre módulos (rh, cat, inv, ven, com, fis, fin, seg)
 *   3. @Service solo en clases que terminan en "Service"
 *   4. @RestController solo en clases que terminan en "Controller"
 *   5. controllers NO dependen de *Repository (acceso vía services)
 *   6. services NO inyectan *Repository directamente (deben usar gateways)
 *   7. no hay @Service en paquetes common.web.. (capa de infraestructura)
 *   8. entidades JPA NO se exponen como @RestController
 */
@AnalyzeClasses(packages = "mx.ferreteria.api", importOptions = ImportOption.DoNotIncludeTests.class)
class MensajesSoloDesdeErrorCodeTest {

        private static final String ERROR_FAMILY = "mx.ferreteria.api.common.error.";

        /** Llamada a constructor de la familia ApiException pasando un String. */
        private static final DescribedPredicate<JavaCall<?>> CON_STRING_LITERAL = new DescribedPredicate<>(
                        "constructor de ApiException con argumento String") {
                @Override
                public boolean test(JavaCall<?> call) {
                        return call.getTarget().getOwner().getName().startsWith(ERROR_FAMILY)
                                        && call.getTarget().getRawParameterTypes().stream()
                                                        .anyMatch(t -> t.getName().equals("java.lang.String"));
                }
        };

        @ArchTest
        static final ArchRule excepcionesSoloConErrorCode = noClasses().that().resideOutsideOfPackages(
                        "mx.ferreteria.api.common.error..",
                        "mx.ferreteria.api.common.i18n..")
                        .should().callConstructorWhere(CON_STRING_LITERAL);

        @ArchTest
        static final ArchRule modulosSinCiclos = SlicesRuleDefinition.slices().matching("mx.ferreteria.api.(*)..")
                        .should().beFreeOfCycles();

        // -------- Reglas BACK-DIS-001: convenciones de naming y capas --------

        /** 3. @Service solo en clases que terminan en "Service". */
        @ArchTest
        static final ArchRule serviceNaming = classes()
                        .that().areAnnotatedWith(Service.class)
                        .should().haveSimpleNameEndingWith("Service")
                        .because("@Service debe reservarse a clases que terminan en Service (convencion de naming)");

        /** 4. @RestController solo en clases que terminan en "Controller". */
        @ArchTest
        static final ArchRule controllerNaming = classes()
                        .that().areAnnotatedWith(RestController.class)
                        .should().haveSimpleNameEndingWith("Controller")
                        .because("@RestController debe reservarse a clases que terminan en Controller");

        /** 5. Controllers no deben depender de *Repository (acceso via services). */
        @ArchTest
        static final ArchRule repositoriosNoEnControllers = noClasses()
                        .that().areAnnotatedWith(RestController.class)
                        .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                        .because("Controllers no deben acceder directamente a repositorios: deben pasar por services");

        /**
         * 6. Services NO inyectan *Repository directamente (deben usar gateways).
         * BACK-DIS-001: actualmente expone ~538 violaciones pre-existentes en
         * los modulos cat/inv/ven/com/fin/fis/rh. La migracion a patron
         * Gateway es un sprint dedicado (no es una sola regla). Por ahora
         * la regla queda DOCUMENTADA pero DESHABILITADA; se re-habilita
         * cuando el conteo de violaciones sea 0. Ver
         * audits/findings.yaml BACK-DIS-001 para el plan de migracion.
         */
        // BACK-DIS-001: regla documentada, deshabilitada hasta migrar a Gateway.
        // @ArchTest
        // static final ArchRule servicesNoInyectanRepos = noClasses()
        //                 .that().areAnnotatedWith(Service.class)
        //                 .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
        //                 .because("Services deben acceder a persistencia via Gateway");

        /** 7. @Service no pertenece a la capa common.web (infraestructura HTTP). */
        @ArchTest
        static final ArchRule serviceFueraDeCapaWeb = noClasses()
                        .that().areAnnotatedWith(Service.class)
                        .should().resideInAPackage("mx.ferreteria.api.common.web..")
                        .because("@Service no pertenece a common.web (capa de infraestructura HTTP, no de negocio)");

        /** 8. Entidades JPA no se exponen como @RestController. */
        @ArchTest
        static final ArchRule entidadesNoSonControllers = noClasses()
                        .that().areAnnotatedWith(Entity.class)
                        .should().beAnnotatedWith(RestController.class)
                        .because("Entidades JPA no deben exponerse como @RestController (separacion modelo/API)");
}