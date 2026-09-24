package com.example.finanzas.exception;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(Long id) {
        super("No existe un movimiento con id " + id);
    }
}
