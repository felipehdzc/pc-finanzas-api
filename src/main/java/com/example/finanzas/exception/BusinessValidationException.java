package com.example.finanzas.exception;

import java.util.Map;

public class BusinessValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public BusinessValidationException(Map<String, String> fieldErrors) {
        super("El movimiento contiene datos inválidos");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
