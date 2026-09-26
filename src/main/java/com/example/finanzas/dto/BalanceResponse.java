package com.example.finanzas.dto;

import java.math.BigDecimal;

public record BalanceResponse(
    BigDecimal totalIncome, BigDecimal totalExpense, BigDecimal balance) {}
