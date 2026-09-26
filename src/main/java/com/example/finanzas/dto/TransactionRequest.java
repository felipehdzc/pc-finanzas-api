package com.example.finanzas.dto;

import com.example.finanzas.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
    @NotBlank(message = "El concepto es obligatorio y no puede estar vacío")
        @Size(max = 100, message = "El concepto no puede superar los 100 caracteres")
        String concept,
    @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
        String description,
    @NotNull(message = "El importe es obligatorio")
        @DecimalMin(value = "0", inclusive = false, message = "El importe debe ser mayor que cero")
        @Digits(
            integer = 12,
            fraction = 2,
            message = "El importe admite como máximo 12 dígitos enteros y 2 decimales")
        BigDecimal amount,
    @NotNull(message = "El tipo es obligatorio: INCOME o EXPENSE") TransactionType type,
    @NotNull(message = "La fecha es obligatoria")
        @PastOrPresent(message = "La fecha no puede ser futura")
        LocalDate date) {}
