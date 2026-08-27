package mx.ferreteria.api.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * Regla arquitectónica: NINGÚN mensaje de error vive en el código (PLAN §4.4).
 * Fuera de common.error/common.i18n está prohibido invocar constructores de la
 * familia ApiException pasando un String literal/variable como argumento.
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
}
