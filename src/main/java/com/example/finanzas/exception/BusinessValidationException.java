package com.example.finanzas.exception;

import java.util.Map;

public class BusinessValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public BusinessValidationException(Map<String, String> fieldErrors) {
        this("El movimiento contiene datos inválidos.", fieldErrors);
    }

    public BusinessValidationException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
