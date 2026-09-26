package com.example.finanzas.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.finanzas.controller.FinancialTransactionController;
import com.example.finanzas.dto.TransactionRequest;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class GlobalExceptionHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(Clock.fixed(NOW, ZoneOffset.UTC));
    private final WebRequest request = new ServletWebRequest(new MockHttpServletRequest("POST", "/api/transactions"));

    @Test
    void missingTransactionReturns404WithUniformErrorAndFixedTimestamp() {
        TransactionNotFoundException exception = new TransactionNotFoundException(99L);

        ResponseEntity<Object> result = handler.handleNotFound(exception, request);

        ApiError error = assertInstanceOf(ApiError.class, result.getBody());
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
        assertEquals(NOW, error.timestamp());
        assertEquals(404, error.status());
        assertEquals("Not Found", error.error());
        assertEquals(exception.getMessage(), error.message());
        assertEquals("/api/transactions", error.path());
        assertEquals(Map.of(), error.fieldErrors());
    }

    @Test
    void businessValidationReturns400AndDetailsOfInvalidFields() {
        Map<String, String> fields = Map.of("amount", "Debe ser estrictamente positivo.");

        ResponseEntity<Object> result = handler.handleBusinessValidation(new BusinessValidationException(fields), request);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        ApiError error = assertInstanceOf(ApiError.class, result.getBody());
        assertEquals(400, error.status());
        assertEquals("El movimiento contiene datos inválidos.", error.message());
        assertEquals(fields, error.fieldErrors());
    }

    @Test
    void invertedDateRangeReturns400WithSpecificMessageAndFromDetail() {
        String message = "La fecha 'from' no puede ser posterior a 'to'.";
        Map<String, String> fields = Map.of("from", message);
        BusinessValidationException exception = new BusinessValidationException(message, fields);

        ResponseEntity<Object> result = handler.handleBusinessValidation(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        ApiError error = assertInstanceOf(ApiError.class, result.getBody());
        assertEquals(400, error.status());
        assertEquals(message, error.message());
        assertEquals(fields, error.fieldErrors());
    }

    @Test
    void beanValidationReturns400AndFieldErrors() throws ReflectiveOperationException {
        TransactionRequest invalid = new TransactionRequest("", null, null, null, null);
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(invalid, "transactionRequest");
        binding.addError(new FieldError("transactionRequest", "concept", "El concepto es obligatorio."));
        binding.addError(new FieldError("transactionRequest", "amount", "El importe es obligatorio."));
        MethodParameter parameter = new MethodParameter(
                FinancialTransactionController.class.getMethod("create", TransactionRequest.class), 0);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, binding);

        ResponseEntity<Object> result = handler.handleMethodArgumentNotValid(
                exception, HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, request);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        ApiError error = assertInstanceOf(ApiError.class, result.getBody());
        assertEquals(Map.of("concept", "El concepto es obligatorio.", "amount", "El importe es obligatorio."),
                error.fieldErrors());
    }

    @ParameterizedTest
    @MethodSource("invalidJsonFields")
    void invalidJsonValueReturns400WithTheAffectedField(String field, String json, String expectedMessage) {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        JsonMappingException cause = assertThrows(JsonMappingException.class,
                () -> mapper.readValue(json, TransactionRequest.class));
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                "Detalles internos del deserializador", cause, new MockHttpInputMessage(new byte[0]));

        ResponseEntity<Object> result = handler.handleHttpMessageNotReadable(
                exception, HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, request);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        ApiError error = assertInstanceOf(ApiError.class, result.getBody());
        assertEquals(Map.of(field, expectedMessage), error.fieldErrors());
        assertFalse(error.message().contains("Detalles internos"));
    }

    private static Stream<Arguments> invalidJsonFields() {
        return Stream.of(
                Arguments.of("type", "{\"type\":\"TRANSFER\"}", "Debe ser INCOME o EXPENSE."),
                Arguments.of("date", "{\"date\":\"2026-02-30\"}", "Debe ser una fecha válida con formato yyyy-MM-dd."),
                Arguments.of("amount", "{\"amount\":\"dinero\"}", "Debe ser un número decimal válido."));
    }

    @Test
    void malformedOrMissingJsonReturns400WithoutExposingParserDetails() {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                "Detalles internos del parser", new MockHttpInputMessage(new byte[0]));

        ResponseEntity<Object> result = handler.handleHttpMessageNotReadable(
                exception, HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, request);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        ApiError error = assertInstanceOf(ApiError.class, result.getBody());
        assertEquals(Map.of(), error.fieldErrors());
        assertFalse(error.message().contains("Detalles internos"));
    }

    @Test
    void nonNumericIdentifierReturns400WithIdDetail() throws ReflectiveOperationException {
        MethodParameter parameter = new MethodParameter(
                FinancialTransactionController.class.getMethod("findById", Long.class), 0);
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "id", parameter, new NumberFormatException("abc"));

        ResponseEntity<Object> result = handler.handleTypeMismatch(
                exception, HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, request);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        ApiError error = assertInstanceOf(ApiError.class, result.getBody());
        assertEquals(Map.of("id", "Debe ser un número entero válido."), error.fieldErrors());
    }

    @ParameterizedTest
    @CsvSource({"from, 0, 24-09-2026", "from, 0, 2026-02-30", "to, 1, 24-09-2026", "to, 1, 2026-02-30"})
    void invalidDateParameterReturns400WithDateFormatDetail(String name, int parameterIndex, String invalidDate)
            throws ReflectiveOperationException {
        MethodParameter parameter = new MethodParameter(
                FinancialTransactionController.class.getMethod("findAll", LocalDate.class, LocalDate.class),
                parameterIndex);
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                invalidDate, LocalDate.class, name, parameter, new IllegalArgumentException("Fecha inválida"));

        ResponseEntity<Object> result = handler.handleTypeMismatch(
                exception, HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, request);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        ApiError error = assertInstanceOf(ApiError.class, result.getBody());
        assertEquals(400, error.status());
        assertEquals(Map.of(name, "Debe ser una fecha válida con formato yyyy-MM-dd."), error.fieldErrors());
        assertFalse(error.toString().contains("número entero"));
    }

    @Test
    void frameworkErrorsKeepUniformFormatAndProtocolHeaders() throws Exception {
        HttpRequestMethodNotSupportedException exception =
                new HttpRequestMethodNotSupportedException("PATCH", List.of("GET", "POST"));

        ResponseEntity<Object> result = handler.handleException(exception, request);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, result.getStatusCode());
        assertEquals(Set.of(HttpMethod.GET, HttpMethod.POST), result.getHeaders().getAllow());
        ApiError error = assertInstanceOf(ApiError.class, result.getBody());
        assertEquals(405, error.status());
        assertEquals("/api/transactions", error.path());
    }

    @Test
    void unexpectedErrorReturns500WithoutLeakingTechnicalDetails() {
        ResponseEntity<Object> result = handler.handleUnexpected(
                new IllegalStateException("SELECT * FROM financial_transactions: credenciales secretas"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
        ApiError error = assertInstanceOf(ApiError.class, result.getBody());
        assertEquals("Se ha producido un error interno.", error.message());
        assertEquals(Map.of(), error.fieldErrors());
        assertFalse(error.toString().contains("SELECT"));
        assertFalse(error.toString().contains("credenciales"));
    }
}
