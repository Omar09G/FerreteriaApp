package mx.ferreteria.api.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import mx.ferreteria.api.common.i18n.ErrorCode;

class ExceptionsAndHandlerTest {

    private GlobalExceptionHandler handler;
    private DbErrorTranslator translator;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        translator = new DbErrorTranslator();
        handler = new GlobalExceptionHandler(messages, translator);
        request = new MockHttpServletRequest("GET", "/api/v1/ventas");
    }

    @Test
    @DisplayName("Jerarquia: cada excepcion conserva ErrorCode y args (clon defensivo)")
    void hierarchy_keepsErrorCodeAndClonesArgs() {
        var ex = new ReglaNegocioException(ErrorCode.STOCK_INSUFICIENTE, "LLA-002", 3);
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.STOCK_INSUFICIENTE);
        assertThat(ex.args()).containsExactly("LLA-002", 3);
        ex.args()[0] = "mutado";
        assertThat(ex.args()[0]).isEqualTo("LLA-002");

        assertThat(new ValidacionException(ErrorCode.VALOR_INVALIDO, 1).errorCode())
                .isEqualTo(ErrorCode.VALOR_INVALIDO);
        assertThat(new PaginacionInvalidException(ErrorCode.PAGINACION_INVALIDA, "size", 1, 100)
                .errorCode()).isEqualTo(ErrorCode.PAGINACION_INVALIDA);
        var paginacion = new PaginacionInvalidException(ErrorCode.PAGINACION_INVALIDA, "size", 1, 100);
        assertThat(paginacion.args()).containsExactly("size", 1, 100);
        assertThat(new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO, 999)
                .errorCode()).isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO);
        assertThat(new ConflictoException(ErrorCode.REGISTRO_DUPLICADO, "RFC")
                .errorCode().http()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(new ErrorInternoException(ErrorCode.ERROR_INTERNO, "abc")
                .errorCode().http()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThatThrownBy(() -> { throw ex; }).hasMessage("STOCK_INSUFICIENTE");
    }

    @Test
    @DisplayName("errorBody: envelope con detail localizado es/en y codigo estable")
    void errorBody_buildsLocalizedEnvelope() {
        var ex = new ReglaNegocioException(ErrorCode.STOCK_INSUFICIENTE, "LLA-002", 3);

        Map<String, Object> bodyEs = handler.errorBody(ErrorCode.STOCK_INSUFICIENTE, ex.args(),
                Locale.of("es", "MX"), request);
        assertThat(bodyEs.get("errorCode")).isEqualTo(409);
        assertThat(bodyEs.get("errorMessage")).isEqualTo(
                "Stock insuficiente para el producto LLA-002. Disponible: 3.");
        assertThat(bodyEs.get("codigo")).isEqualTo("STOCK_INSUFICIENTE");
        assertThat(bodyEs.get("instance")).isEqualTo("/api/v1/ventas");
        assertThat(bodyEs.get("success")).isEqualTo(false);
        assertThat(bodyEs.get("data")).isNull();

        Map<String, Object> bodyEn = handler.errorBody(ErrorCode.STOCK_INSUFICIENTE, ex.args(),
                Locale.ENGLISH, request);
        assertThat(bodyEn.get("errorMessage")).isEqualTo(
                "Insufficient stock for product LLA-002. Available: 3.");
    }

    @Test
    @DisplayName("Traductor: SQLSTATE P0 -> ErrorCode; estado desconocido -> vacio")
    void translator_mapsSqlstateContract() {
        var business = new DataAccessResourceFailureException(
                "call failed", new SQLException("Stock negativo no permitido", "P0100", 1));
        assertThat(translator.translate(business)).contains(ErrorCode.STOCK_INSUFICIENTE);

        var unknown = new DataAccessResourceFailureException(
                "boom", new SQLException("otra cosa", "42P01", 1));
        assertThat(translator.translate(unknown)).isEmpty();

        assertThat(translator.translate(new DataAccessResourceFailureException("sin causa"))).isEmpty();
    }

    @Test
    @DisplayName("ERROR_INTERNO interpola el requestId de correlacion cuando existe")
    void internalError_includesCorrelationId() {
        String rid = UUID.randomUUID().toString();
        org.slf4j.MDC.put("requestId", rid);
        try {
            Map<String, Object> body = handler.errorBody(ErrorCode.ERROR_INTERNO,
                    new Object[] {rid}, Locale.of("es"), request);
            assertThat(body.get("errorMessage").toString()).contains(rid);
        } finally {
            org.slf4j.MDC.remove("requestId");
        }
    }

    @Test
    @DisplayName("handleApi: ReglaNegocio -> 409 con codigo estable y instance del URI")
    void handleApi_mapsToEnvelopeResponse() {
        var response = handler.handleApi(
                new ReglaNegocioException(ErrorCode.TURNO_YA_CERRADO, 7, "10:00"), request);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().get("errorCode")).isEqualTo(409);
        assertThat(response.getBody().get("codigo")).isEqualTo("TURNO_YA_CERRADO");
        assertThat(response.getBody().get("instance")).isEqualTo("/api/v1/ventas");
    }

    @Test
    @DisplayName("handleDataAccess: ERRCODE P0100 -> 409 negocio; sin contrato -> 500 interno")
    void handleDataAccess_translatesContractOrFallsBack() {
        var business = new DataAccessResourceFailureException(
                "call", new SQLException("stock", "P0100", 1));
        var ok = handler.handleDataAccess(business, request);
        assertThat(ok.getStatusCode().value()).isEqualTo(409);
        assertThat(ok.getBody().get("codigo")).isEqualTo("STOCK_INSUFICIENTE");

        var desconocido = new DataAccessResourceFailureException(
                "boom", new SQLException("otra cosa", "42P01", 1));
        var fallback = handler.handleDataAccess(desconocido, request);
        assertThat(fallback.getStatusCode().value()).isEqualTo(500);
        assertThat(fallback.getBody().get("codigo")).isEqualTo("ERROR_INTERNO");
    }

    @Test
    @DisplayName("handleUnexpected: cualquier excepcion -> 500 ERROR_INTERNO sin filtrar stack")
    void handleUnexpected_returnsSanitizedInternalError() {
        var response = handler.handleUnexpected(new IllegalStateException("secreto-interno"), request);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().get("codigo")).isEqualTo("ERROR_INTERNO");
        assertThat(response.getBody().get("errorMessage").toString()).doesNotContain("secreto-interno");
    }

    @Test
    @DisplayName("handleValidation: Bean Validation -> 400 CAMPO_REQUERIDO con details[]")
    void handleValidation_collectsFieldErrors() throws Exception {
        class Holder {
            @SuppressWarnings("unused")
            void crear(String nombre) { }
        }
        var metodo = Holder.class.getDeclaredMethod("crear", String.class);
        var parameter = new org.springframework.core.MethodParameter(metodo, 0);
        var binding = new org.springframework.validation.BeanPropertyBindingResult(new Dto(), "dto");
        binding.rejectValue("nombre", "blank", "requerido");
        var ex = new MethodArgumentNotValidException(parameter, binding);

        var response = handler.handleValidation(ex, request);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().get("errorCode")).isEqualTo(400);
        assertThat(response.getBody().get("codigo")).isEqualTo("CAMPO_REQUERIDO");
        @SuppressWarnings("unchecked")
        var details = (java.util.List<Map<String, String>>) response.getBody().get("details");
        assertThat(details).isNotEmpty();
        assertThat(details.get(0).get("field")).isEqualTo("nombre");
        assertThat(details.get(0).get("error")).isEqualTo("requerido");
    }

    static final class Dto {
        private String nombre;

        public String getNombre() {
            return nombre;
        }

        @SuppressWarnings("unused")
        public void setNombre(String nombre) {
            this.nombre = nombre;
        }
    }
}
