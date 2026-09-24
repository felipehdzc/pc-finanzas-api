package com.example.finanzas.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(TransactionNotFoundException exception,
                                                WebRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of(), request, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(BusinessValidationException.class)
    public ResponseEntity<Object> handleBusinessValidation(BusinessValidationException exception,
                                                          WebRequest request) {
        return error(HttpStatus.BAD_REQUEST, "El movimiento contiene datos inválidos.",
                exception.getFieldErrors(), request, HttpHeaders.EMPTY);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(field -> fields.putIfAbsent(
                field.getField(), field.getDefaultMessage() == null ? "Valor inválido." : field.getDefaultMessage()));
        return error(status, "El movimiento contiene datos inválidos.", fields, request, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
                                                                 HttpHeaders headers, HttpStatusCode status,
                                                                 WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof JsonMappingException mappingException && !mappingException.getPath().isEmpty()) {
                String field = mappingException.getPath().getLast().getFieldName();
                if (field != null) {
                    fields.put(field, switch (field) {
                        case "type" -> "Debe ser INCOME o EXPENSE.";
                        case "date" -> "Debe ser una fecha válida con formato yyyy-MM-dd.";
                        case "amount" -> "Debe ser un número decimal válido.";
                        default -> "El valor no tiene el formato esperado.";
                    });
                }
                break;
            }
        }
        return error(status, "El cuerpo JSON es inválido o contiene valores con formato incorrecto.",
                fields, request, headers);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception,
                                                        HttpHeaders headers, HttpStatusCode status,
                                                        WebRequest request) {
        Map<String, String> fields = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? Map.of(mismatch.getName(), "Debe ser un número entero válido.") : Map.of();
        return error(status, "Un parámetro de la petición tiene un formato inválido.", fields, request, headers);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
                                                             HttpHeaders headers, HttpStatusCode status,
                                                             WebRequest request) {
        String message = status.is5xxServerError()
                ? "Se ha producido un error interno."
                : "La petición no se ha podido procesar.";
        return error(status, message, Map.of(), request, headers);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
        log.error("Error inesperado al procesar una petición", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Se ha producido un error interno.",
                Map.of(), request, HttpHeaders.EMPTY);
    }

    private ResponseEntity<Object> error(HttpStatusCode status, String message, Map<String, String> fields,
                                         WebRequest request, HttpHeaders headers) {
        HttpStatus knownStatus = HttpStatus.resolve(status.value());
        String reason = knownStatus == null ? "Error" : knownStatus.getReasonPhrase();
        String path = request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : request.getDescription(false).replaceFirst("^uri=", "");
        ApiError body = new ApiError(clock.instant(), status.value(), reason, message, path, fields);
        return new ResponseEntity<>(body, headers, status);
    }
}
