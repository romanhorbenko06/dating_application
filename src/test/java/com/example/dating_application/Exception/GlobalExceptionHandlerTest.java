package com.example.dating_application.Exception;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Контракт помилок API з фронтом і те, що внутрішні подробиці назовні не йдуть. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final WebRequest request = webRequest();

    @Test
    @DisplayName("відмова в доступі не пояснює, ЧОМУ саме відмовлено")
    void accessDeniedSaysNothingExtra() {
        var response = handler.handleAccessDenied(
                new AccessDeniedException("user 42 lacks ROLE_ADMIN on /api/admin/users"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(message(response)).isEqualTo("Access denied");
        assertThat(body(response).toString())
                .as("подробиці правила доступу — підказка для того, хто його обходить")
                .doesNotContain("ROLE_ADMIN", "42");
    }

    @Test
    @DisplayName("помилка цілісності БД не показує SQL")
    void dataIntegrityHidesSql() {
        var response = handler.handleDataIntegrity(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"users_email_key\""), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(message(response)).isEqualTo("Operation violates data integrity constraints");
        assertThat(body(response).toString())
                .as("назви таблиць і обмежень — карта бази для стороннього")
                .doesNotContain("users_email_key", "unique constraint");
    }

    @Test
    @DisplayName("несподіваний виняток віддає 500 без подробиць")
    void unexpectedErrorStaysOpaque() {
        var runtime = handler.handleRuntimeException(
                new IllegalStateException("connection pool exhausted at HikariPool-1"), request);
        var checked = handler.handleGenericException(
                new Exception("NullPointerException at UserService.java:451"), request);

        for (var response : List.of(runtime, checked)) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(message(response)).isEqualTo("Internal server error");
            assertThat(body(response).toString())
                    .doesNotContain("HikariPool", "UserService.java", "NullPointer");
        }
    }

    @Test
    @DisplayName("порушення бізнес-правила — єдиний випадок, коли текст іде клієнту")
    void businessExceptionCarriesItsMessage() {
        var response = handler.handleBusinessException(
                new BusinessException("You are already matched with this user"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(message(response))
                .as("саме ці тексти фронт перекладає користувачеві")
                .isEqualTo("You are already matched with this user");
    }

    @Test
    @DisplayName("помилки валідації приходять розкладеними по полях")
    void validationErrorsArriveAsFieldMap() throws Exception {
        var binding = new BeanPropertyBindingResult(new Object(), "registerRequest");
        binding.addError(new FieldError("registerRequest", "email", "Email format is invalid"));
        binding.addError(new FieldError("registerRequest", "name", "Name is required"));

        var response = handler.handleValidationErrors(
                new MethodArgumentNotValidException(null, binding), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(message(response)).isEqualTo("Validation failed");
        assertThat(fieldErrors(response))
                .as("без цієї мапи форма не знає, яке поле підсвітити")
                .containsEntry("email", "Email format is invalid")
                .containsEntry("name", "Name is required");
    }

    @Test
    @DisplayName("порушення обмежень на параметрах теж дає мапу полів")
    void constraintViolationsArriveAsFieldMap() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("getUpcoming.limit");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("limit must not exceed 20");

        var response = handler.handleConstraintViolation(
                new ConstraintViolationException(Set.of(violation)), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fieldErrors(response))
                .as("ім'я методу відкидається, лишається сам параметр")
                .containsEntry("limit", "limit must not exceed 20");
    }

    @Test
    @DisplayName("зіпсований JSON дає 400, а не 500")
    void malformedInputIsBadRequest() {
        var response = handler.handleInvalidInput(
                new HttpMessageNotReadableException("Unexpected end of input",
                        (org.springframework.http.HttpInputMessage) null), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(message(response)).isEqualTo("Invalid or malformed request value");
    }

    @Test
    @DisplayName("завеликий файл — 413, а не загальна помилка")
    void oversizedUploadIsPayloadTooLarge() {
        var response = handler.handleUploadTooLarge(
                new MaxUploadSizeExceededException(5_000_000L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(message(response)).isEqualTo("Uploaded file is too large");
    }

    @Test
    @DisplayName("відсутня частина multipart називає, якої саме бракує")
    void missingPartNamesIt() {
        var response = handler.handleMissingPart(
                new MissingServletRequestPartException("file"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(message(response))
                .as("клієнту треба знати, яке поле він забув")
                .contains("file");
    }

    @Test
    @DisplayName("невірний метод — 405, невірний тип тіла — 415, неприйнятний формат — 406 без тіла")
    void protocolErrorsKeepTheirStatuses() {
        assertThat(handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PATCH"), request).getStatusCode())
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);

        assertThat(handler.handleUnsupportedMediaType(
                new HttpMediaTypeNotSupportedException("text/plain"), request).getStatusCode())
                .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        ResponseEntity<Void> notAcceptable = handler.handleNotAcceptable(
                new HttpMediaTypeNotAcceptableException("no acceptable representation"), request);
        assertThat(notAcceptable.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(notAcceptable.getBody())
                .as("на 406 тіло слати нічим — клієнт саме й сказав, що не прийме жодного формату")
                .isNull();
    }

    @Test
    @DisplayName("невідома адреса — 404, а не 500")
    void unknownPathIsNotFound() {
        var response = handler.handleNotFound(
                new NoResourceFoundException(HttpMethod.GET, "/api/вигадка", "/api/вигадка"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("відсутній обов'язковий параметр — 400")
    void missingParameterIsBadRequest() {
        var response = handler.handleBindingException(
                new ServletRequestBindingException("Missing request parameter 'token'"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("помилки JPA перетворюються на 400 зі своїм текстом")
    void jpaExceptionsBecomeBadRequest() {
        var notFound = handler.handleEntityNotFoundException(
                new EntityNotFoundException("Chat not found"), request);
        var exists = handler.handleEntityExistsException(
                new EntityExistsException("Cannot create chat with yourself"), request);
        var illegal = handler.handleIllegalArgumentException(
                new IllegalArgumentException("Unknown value"), request);

        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(message(notFound)).isEqualTo("Chat not found");
        assertThat(exists.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(message(exists)).isEqualTo("Cannot create chat with yourself");
        assertThat(illegal.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("кожна відповідь має однаковий каркас: час, статус, error, message")
    void everyResponseSharesTheSameShape() {
        var response = handler.handleBusinessException(new BusinessException("будь-що"), request);

        assertThat(body(response))
                .as("фронт розбирає саме ці ключі")
                .containsKeys("timestamp", "status", "message", "error");
        assertThat(body(response).get("status")).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    private static WebRequest webRequest() {
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(anyBoolean())).thenReturn("uri=/api/test");
        return request;
    }

    private static Map<String, Object> body(ResponseEntity<Map<String, Object>> response) {
        return response.getBody();
    }

    private static String message(ResponseEntity<Map<String, Object>> response) {
        return String.valueOf(body(response).get("message"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> fieldErrors(ResponseEntity<Map<String, Object>> response) {
        return (Map<String, String>) body(response).get("fieldErrors");
    }
}
