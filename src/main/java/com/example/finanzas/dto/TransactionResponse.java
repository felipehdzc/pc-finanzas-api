package com.example.finanzas.dto;

import com.example.finanzas.domain.FinancialTransaction;
import com.example.finanzas.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionResponse(
    Long id,
    String concept,
    String description,
    BigDecimal amount,
    TransactionType type,
    LocalDate date) {

  public static TransactionResponse from(FinancialTransaction transaction) {
    return new TransactionResponse(
        transaction.getId(),
        transaction.getConcept(),
        transaction.getDescription(),
        transaction.getAmount(),
        transaction.getType(),
        transaction.getDate());
  }
}
