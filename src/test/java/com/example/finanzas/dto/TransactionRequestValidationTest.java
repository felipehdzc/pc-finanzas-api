package com.example.finanzas.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.finanzas.domain.TransactionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TransactionRequestValidationTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-21T10:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate TODAY = LocalDate.now(CLOCK);
  private static ValidatorFactory validatorFactory;
  private static Validator validator;

  @BeforeAll
  static void setUpValidatorWithoutSpring() {
    validatorFactory =
        Validation.byDefaultProvider()
            .configure()
            .clockProvider(() -> CLOCK)
            .buildValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    validatorFactory.close();
  }

  @ParameterizedTest(name = "Bean Validation rechaza {0}")
  @MethodSource("invalidRequests")
  void reportsTheInvalidField(String scenario, TransactionRequest request, String expectedField) {
    Set<String> invalidFields =
        validator.validate(request).stream()
            .map(violation -> violation.getPropertyPath().toString())
            .collect(Collectors.toSet());

    assertEquals(Set.of(expectedField), invalidFields);
  }

  @Test
  void acceptsTheMaximumLengthsAndAmountAndTodaysDate() {
    TransactionRequest request =
        new TransactionRequest(
            "a".repeat(100),
            "b".repeat(500),
            new BigDecimal("999999999999.99"),
            TransactionType.INCOME,
            TODAY);

    Set<ConstraintViolation<TransactionRequest>> violations = validator.validate(request);

    assertTrue(violations.isEmpty(), () -> "Infracciones inesperadas: " + violations);
  }

  @Test
  void acceptsANullDescriptionAndAPastExpense() {
    TransactionRequest request =
        new TransactionRequest(
            "Compra", null, new BigDecimal("0.01"), TransactionType.EXPENSE, TODAY.minusDays(1));

    assertEquals(Set.of(), validator.validate(request));
  }

  @Test
  void acceptsAnEmptyDescriptionAndAnIntegerAmount() {
    TransactionRequest request =
        new TransactionRequest("Ingreso", "", new BigDecimal("12"), TransactionType.INCOME, TODAY);

    assertEquals(Set.of(), validator.validate(request));
  }

  private static Stream<Arguments> invalidRequests() {
    return Stream.of(
        invalid("concepto nulo", null, null, "1.00", TransactionType.INCOME, TODAY, "concept"),
        invalid("concepto vacío", "", null, "1.00", TransactionType.INCOME, TODAY, "concept"),
        invalid(
            "concepto en blanco", " \t ", null, "1.00", TransactionType.INCOME, TODAY, "concept"),
        invalid(
            "concepto de 101 caracteres",
            "a".repeat(101),
            null,
            "1.00",
            TransactionType.INCOME,
            TODAY,
            "concept"),
        invalid(
            "descripción de 501 caracteres",
            "Concepto",
            "a".repeat(501),
            "1.00",
            TransactionType.INCOME,
            TODAY,
            "description"),
        invalid("importe nulo", "Concepto", null, null, TransactionType.INCOME, TODAY, "amount"),
        invalid("importe cero", "Concepto", null, "0.00", TransactionType.INCOME, TODAY, "amount"),
        invalid(
            "importe negativo", "Concepto", null, "-0.01", TransactionType.INCOME, TODAY, "amount"),
        invalid(
            "importe con tres decimales",
            "Concepto",
            null,
            "1.001",
            TransactionType.INCOME,
            TODAY,
            "amount"),
        invalid(
            "importe con trece dígitos enteros",
            "Concepto",
            null,
            "1000000000000.00",
            TransactionType.INCOME,
            TODAY,
            "amount"),
        invalid(
            "importe con exponente fuera del límite",
            "Concepto",
            null,
            "1E+12",
            TransactionType.INCOME,
            TODAY,
            "amount"),
        invalid("tipo nulo", "Concepto", null, "1.00", null, TODAY, "type"),
        invalid("fecha nula", "Concepto", null, "1.00", TransactionType.INCOME, null, "date"),
        invalid(
            "fecha futura",
            "Concepto",
            null,
            "1.00",
            TransactionType.INCOME,
            TODAY.plusDays(1),
            "date"));
  }

  private static Arguments invalid(
      String scenario,
      String concept,
      String description,
      String amount,
      TransactionType type,
      LocalDate date,
      String field) {
    return Arguments.of(
        scenario,
        new TransactionRequest(
            concept, description, amount == null ? null : new BigDecimal(amount), type, date),
        field);
  }
}
