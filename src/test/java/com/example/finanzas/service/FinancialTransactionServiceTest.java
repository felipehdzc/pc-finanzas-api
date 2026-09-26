package com.example.finanzas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.finanzas.domain.FinancialTransaction;
import com.example.finanzas.domain.TransactionType;
import com.example.finanzas.dto.BalanceResponse;
import com.example.finanzas.dto.TransactionRequest;
import com.example.finanzas.dto.TransactionResponse;
import com.example.finanzas.exception.BusinessValidationException;
import com.example.finanzas.exception.TransactionNotFoundException;
import com.example.finanzas.repository.FinancialTransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class FinancialTransactionServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-21T10:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate TODAY = LocalDate.now(CLOCK);

  @Mock private FinancialTransactionRepository repository;

  private FinancialTransactionService service;

  @BeforeEach
  void setUp() {
    service = new FinancialTransactionService(repository, CLOCK);
  }

  @ParameterizedTest(name = "Crear rechaza {0} y no accede al repositorio")
  @MethodSource("invalidRequests")
  void createRejectsInvalidFieldsBeforePersistence(
      String scenario, TransactionRequest request, String invalidField) {
    BusinessValidationException exception =
        assertThrows(BusinessValidationException.class, () -> service.create(request));

    assertTrue(exception.getFieldErrors().containsKey(invalidField));
    verifyNoInteractions(repository);
  }

  @Test
  void createAcceptsTodayAndReturnsTheGeneratedIdentifier() {
    TransactionRequest request = validRequest();
    when(repository.save(any(FinancialTransaction.class)))
        .thenAnswer(
            invocation -> {
              FinancialTransaction saved = invocation.getArgument(0);
              assertEquals("Nómina", saved.getConcept());
              assertEquals("Pago mensual", saved.getDescription());
              assertEquals(new BigDecimal("45.10"), saved.getAmount());
              assertEquals(TransactionType.INCOME, saved.getType());
              assertEquals(TODAY, saved.getDate());
              saved.setId(7L);
              return saved;
            });

    TransactionResponse response = service.create(request);

    assertEquals(
        new TransactionResponse(
            7L, "Nómina", "Pago mensual", new BigDecimal("45.10"), TransactionType.INCOME, TODAY),
        response);
    verify(repository).save(any(FinancialTransaction.class));
    verifyNoMoreInteractions(repository);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0.01", "1", "999999999999.99", "1E+11"})
  void createAcceptsAmountsWithinTheAllowedPrecisionAndNormalizesToCents(String amount) {
    BigDecimal expected = new BigDecimal(amount).setScale(2);
    TransactionRequest request =
        new TransactionRequest(
            "Ingreso", null, new BigDecimal(amount), TransactionType.INCOME, TODAY.minusDays(1));
    when(repository.save(any(FinancialTransaction.class)))
        .thenAnswer(
            invocation -> {
              FinancialTransaction saved = invocation.getArgument(0);
              assertEquals(expected, saved.getAmount());
              saved.setId(8L);
              return saved;
            });

    TransactionResponse response = service.create(request);

    assertEquals(expected, response.amount());
    assertEquals(TODAY.minusDays(1), response.date());
    assertEquals(8L, response.id());
    verify(repository).save(any(FinancialTransaction.class));
  }

  @ParameterizedTest(name = "Actualizar rechaza {0} sin guardar ni modificar la entidad")
  @MethodSource("invalidRequests")
  void updateRejectsInvalidFieldsWithoutMutatingTheExistingEntity(
      String scenario, TransactionRequest request, String invalidField) {
    FinancialTransaction existing =
        transaction(
            4L,
            "Concepto original",
            "Descripción original",
            "25.00",
            TransactionType.EXPENSE,
            TODAY.minusDays(2));
    when(repository.findById(4L)).thenReturn(Optional.of(existing));

    BusinessValidationException exception =
        assertThrows(BusinessValidationException.class, () -> service.update(4L, request));

    assertTrue(exception.getFieldErrors().containsKey(invalidField));
    assertEquals(4L, existing.getId());
    assertEquals("Concepto original", existing.getConcept());
    assertEquals("Descripción original", existing.getDescription());
    assertEquals(new BigDecimal("25.00"), existing.getAmount());
    assertEquals(TransactionType.EXPENSE, existing.getType());
    assertEquals(TODAY.minusDays(2), existing.getDate());
    verify(repository).findById(4L);
    verify(repository, never()).save(any(FinancialTransaction.class));
    verifyNoMoreInteractions(repository);
  }

  @Test
  void updateReplacesEveryEditableFieldAndKeepsTheIdentifier() {
    FinancialTransaction existing =
        transaction(
            4L,
            "Concepto original",
            "Descripción original",
            "25.00",
            TransactionType.EXPENSE,
            TODAY.minusDays(2));
    TransactionRequest request =
        new TransactionRequest(
            "Nuevo ingreso", null, new BigDecimal("72.5"), TransactionType.INCOME, TODAY);
    when(repository.findById(4L)).thenReturn(Optional.of(existing));
    when(repository.save(same(existing))).thenReturn(existing);

    TransactionResponse response = service.update(4L, request);

    assertEquals(
        new TransactionResponse(
            4L, "Nuevo ingreso", null, new BigDecimal("72.50"), TransactionType.INCOME, TODAY),
        response);
    assertEquals(4L, existing.getId());
    assertEquals("Nuevo ingreso", existing.getConcept());
    assertNull(existing.getDescription());
    assertEquals(new BigDecimal("72.50"), existing.getAmount());
    assertEquals(TransactionType.INCOME, existing.getType());
    assertEquals(TODAY, existing.getDate());
    verify(repository).findById(4L);
    verify(repository).save(same(existing));
    verifyNoMoreInteractions(repository);
  }

  @Test
  void findByIdReturnsTheRequestedTransactionAsADto() {
    FinancialTransaction existing =
        transaction(5L, "Compra", "Alimentos", "38.90", TransactionType.EXPENSE, TODAY);
    when(repository.findById(5L)).thenReturn(Optional.of(existing));

    TransactionResponse response = service.findById(5L);

    assertEquals(
        new TransactionResponse(
            5L, "Compra", "Alimentos", new BigDecimal("38.90"), TransactionType.EXPENSE, TODAY),
        response);
    verify(repository).findById(5L);
    verifyNoMoreInteractions(repository);
  }

  @Test
  void findByIdRejectsAMissingTransaction() {
    when(repository.findById(99L)).thenReturn(Optional.empty());

    assertThrows(TransactionNotFoundException.class, () -> service.findById(99L));

    verify(repository).findById(99L);
    verifyNoMoreInteractions(repository);
  }

  @Test
  void updateRejectsAMissingTransactionWithoutSaving() {
    when(repository.findById(99L)).thenReturn(Optional.empty());

    assertThrows(TransactionNotFoundException.class, () -> service.update(99L, validRequest()));

    verify(repository).findById(99L);
    verifyNoMoreInteractions(repository);
  }

  @Test
  void updateChecksExistenceBeforeValidatingTheReplacement() {
    when(repository.findById(99L)).thenReturn(Optional.empty());
    TransactionRequest invalid =
        new TransactionRequest("Concepto", null, BigDecimal.ZERO, TransactionType.INCOME, TODAY);

    assertThrows(TransactionNotFoundException.class, () -> service.update(99L, invalid));

    verify(repository).findById(99L);
    verifyNoMoreInteractions(repository);
  }

  @Test
  void deleteRejectsAMissingTransactionWithoutDeleting() {
    when(repository.findById(99L)).thenReturn(Optional.empty());

    assertThrows(TransactionNotFoundException.class, () -> service.delete(99L));

    verify(repository).findById(99L);
    verifyNoMoreInteractions(repository);
  }

  @Test
  void deleteRemovesTheExistingTransaction() {
    FinancialTransaction existing =
        transaction(5L, "Compra", null, "38.90", TransactionType.EXPENSE, TODAY);
    when(repository.findById(5L)).thenReturn(Optional.of(existing));

    service.delete(5L);

    verify(repository).findById(5L);
    verify(repository).delete(same(existing));
    verifyNoMoreInteractions(repository);
  }

  @Test
  void findAllReturnsAnEmptyListWhenThereAreNoTransactions() {
    when(repository.findAll(Sort.by("id"))).thenReturn(List.of());

    assertEquals(List.of(), service.findAll());

    verify(repository).findAll(Sort.by("id"));
    verifyNoMoreInteractions(repository);
  }

  @Test
  void findAllMapsEveryTransactionIntoAnOrderedResponse() {
    FinancialTransaction first =
        transaction(1L, "Ingreso", null, "80.00", TransactionType.INCOME, TODAY.minusDays(1));
    FinancialTransaction second =
        transaction(2L, "Compra", "Alimentos", "30.00", TransactionType.EXPENSE, TODAY);
    when(repository.findAll(Sort.by("id"))).thenReturn(List.of(first, second));

    assertEquals(
        List.of(
            new TransactionResponse(
                1L,
                "Ingreso",
                null,
                new BigDecimal("80.00"),
                TransactionType.INCOME,
                TODAY.minusDays(1)),
            new TransactionResponse(
                2L,
                "Compra",
                "Alimentos",
                new BigDecimal("30.00"),
                TransactionType.EXPENSE,
                TODAY)),
        service.findAll());

    verify(repository).findAll(Sort.by("id"));
    verifyNoMoreInteractions(repository);
  }

  @Test
  void findAllWithNoDateBoundsReturnsAllRepositoryResults() {
    FinancialTransaction first =
        transaction(1L, "Ingreso", null, "80.00", TransactionType.INCOME, TODAY.minusDays(10));
    FinancialTransaction second =
        transaction(2L, "Compra", "Alimentos", "30.00", TransactionType.EXPENSE, TODAY);
    when(repository.findAll(Sort.by("id"))).thenReturn(List.of(first, second));

    List<TransactionResponse> response = service.findAll(null, null);

    assertEquals(
        List.of(
            new TransactionResponse(
                1L,
                "Ingreso",
                null,
                new BigDecimal("80.00"),
                TransactionType.INCOME,
                TODAY.minusDays(10)),
            new TransactionResponse(
                2L,
                "Compra",
                "Alimentos",
                new BigDecimal("30.00"),
                TransactionType.EXPENSE,
                TODAY)),
        response);
    verify(repository).findAll(Sort.by("id"));
    verifyNoMoreInteractions(repository);
  }

  @Test
  void findAllWithOnlyFromQueriesTheInclusiveLowerBound() {
    LocalDate from = TODAY.minusDays(5);
    FinancialTransaction atFrom =
        transaction(3L, "Ingreso inicial", null, "120.00", TransactionType.INCOME, from);
    FinancialTransaction afterFrom =
        transaction(4L, "Compra posterior", "Alimentos", "22.50", TransactionType.EXPENSE, TODAY);
    when(repository.findByDateGreaterThanEqual(from, Sort.by("id")))
        .thenReturn(List.of(atFrom, afterFrom));

    List<TransactionResponse> response = service.findAll(from, null);

    assertEquals(
        List.of(
            new TransactionResponse(
                3L,
                "Ingreso inicial",
                null,
                new BigDecimal("120.00"),
                TransactionType.INCOME,
                from),
            new TransactionResponse(
                4L,
                "Compra posterior",
                "Alimentos",
                new BigDecimal("22.50"),
                TransactionType.EXPENSE,
                TODAY)),
        response);
    verify(repository).findByDateGreaterThanEqual(from, Sort.by("id"));
    verifyNoMoreInteractions(repository);
  }

  @Test
  void findAllWithOnlyToQueriesTheInclusiveUpperBound() {
    LocalDate to = TODAY.minusDays(5);
    FinancialTransaction beforeTo =
        transaction(2L, "Ingreso anterior", null, "90.00", TransactionType.INCOME, to.minusDays(3));
    FinancialTransaction atTo =
        transaction(5L, "Compra final", "Alimentos", "15.25", TransactionType.EXPENSE, to);
    when(repository.findByDateLessThanEqual(to, Sort.by("id"))).thenReturn(List.of(beforeTo, atTo));

    List<TransactionResponse> response = service.findAll(null, to);

    assertEquals(
        List.of(
            new TransactionResponse(
                2L,
                "Ingreso anterior",
                null,
                new BigDecimal("90.00"),
                TransactionType.INCOME,
                to.minusDays(3)),
            new TransactionResponse(
                5L,
                "Compra final",
                "Alimentos",
                new BigDecimal("15.25"),
                TransactionType.EXPENSE,
                to)),
        response);
    verify(repository).findByDateLessThanEqual(to, Sort.by("id"));
    verifyNoMoreInteractions(repository);
  }

  @Test
  void findAllWithBothBoundsQueriesTheInclusiveDateRange() {
    LocalDate from = TODAY.minusDays(10);
    LocalDate to = TODAY.minusDays(2);
    FinancialTransaction atFrom =
        transaction(1L, "Ingreso inicial", null, "100.00", TransactionType.INCOME, from);
    FinancialTransaction withinRange =
        transaction(
            3L,
            "Compra intermedia",
            "Alimentos",
            "20.00",
            TransactionType.EXPENSE,
            from.plusDays(3));
    FinancialTransaction atTo =
        transaction(6L, "Ingreso final", null, "40.00", TransactionType.INCOME, to);
    when(repository.findByDateBetween(from, to, Sort.by("id")))
        .thenReturn(List.of(atFrom, withinRange, atTo));

    List<TransactionResponse> response = service.findAll(from, to);

    assertEquals(
        List.of(
            new TransactionResponse(
                1L,
                "Ingreso inicial",
                null,
                new BigDecimal("100.00"),
                TransactionType.INCOME,
                from),
            new TransactionResponse(
                3L,
                "Compra intermedia",
                "Alimentos",
                new BigDecimal("20.00"),
                TransactionType.EXPENSE,
                from.plusDays(3)),
            new TransactionResponse(
                6L, "Ingreso final", null, new BigDecimal("40.00"), TransactionType.INCOME, to)),
        response);
    verify(repository).findByDateBetween(from, to, Sort.by("id"));
    verifyNoMoreInteractions(repository);
  }

  @Test
  void findAllRejectsAnInvertedDateRangeBeforeQueryingTheRepository() {
    LocalDate from = TODAY;
    LocalDate to = TODAY.minusDays(1);
    String expectedMessage = "La fecha 'from' no puede ser posterior a 'to'.";

    BusinessValidationException exception =
        assertThrows(BusinessValidationException.class, () -> service.findAll(from, to));

    assertEquals(expectedMessage, exception.getMessage());
    assertEquals(Map.of("from", expectedMessage), exception.getFieldErrors());
    verifyNoInteractions(repository);
  }

  @Test
  void findAllWithADateRangeReturnsAnEmptyListWhenThereAreNoMatches() {
    LocalDate from = TODAY.minusDays(7);
    LocalDate to = TODAY.minusDays(3);
    when(repository.findByDateBetween(from, to, Sort.by("id"))).thenReturn(List.of());

    assertEquals(List.of(), service.findAll(from, to));

    verify(repository).findByDateBetween(from, to, Sort.by("id"));
    verifyNoMoreInteractions(repository);
  }

  @Test
  void findAllAllowsEqualDateBoundsAndReturnsTransactionsOnThatDate() {
    LocalDate date = TODAY.minusDays(2);
    FinancialTransaction income =
        transaction(7L, "Ingreso del día", null, "65.00", TransactionType.INCOME, date);
    FinancialTransaction expense =
        transaction(8L, "Compra del día", "Alimentos", "12.75", TransactionType.EXPENSE, date);
    when(repository.findByDateBetween(date, date, Sort.by("id")))
        .thenReturn(List.of(income, expense));

    List<TransactionResponse> response = service.findAll(date, date);

    assertEquals(
        List.of(
            new TransactionResponse(
                7L, "Ingreso del día", null, new BigDecimal("65.00"), TransactionType.INCOME, date),
            new TransactionResponse(
                8L,
                "Compra del día",
                "Alimentos",
                new BigDecimal("12.75"),
                TransactionType.EXPENSE,
                date)),
        response);
    verify(repository).findByDateBetween(date, date, Sort.by("id"));
    verifyNoMoreInteractions(repository);
  }

  @Test
  void getBalanceAddsIncomeAndSubtractsExpensesUsingExactDecimals() {
    when(repository.findAll())
        .thenReturn(
            List.of(
                transaction(1L, "Nómina", null, "1000.10", TransactionType.INCOME, TODAY),
                transaction(2L, "Devolución", null, "200.20", TransactionType.INCOME, TODAY),
                transaction(3L, "Alquiler", null, "350.75", TransactionType.EXPENSE, TODAY),
                transaction(4L, "Compra", null, "49.25", TransactionType.EXPENSE, TODAY)));

    BalanceResponse balance = service.getBalance();

    assertEquals(
        new BalanceResponse(
            new BigDecimal("1200.30"), new BigDecimal("400.00"), new BigDecimal("800.30")),
        balance);
    verify(repository).findAll();
    verifyNoMoreInteractions(repository);
  }

  @Test
  void getBalanceReturnsZeroTotalsWhenThereAreNoTransactions() {
    when(repository.findAll()).thenReturn(List.of());

    BalanceResponse balance = service.getBalance();

    assertEquals(
        new BalanceResponse(new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00")),
        balance);
    verify(repository).findAll();
    verifyNoMoreInteractions(repository);
  }

  @Test
  void getBalanceAllowsExpensesToExceedIncome() {
    when(repository.findAll())
        .thenReturn(
            List.of(
                transaction(1L, "Ingreso", null, "10.00", TransactionType.INCOME, TODAY),
                transaction(2L, "Gasto", null, "25.01", TransactionType.EXPENSE, TODAY)));

    BalanceResponse balance = service.getBalance();

    assertEquals(
        new BalanceResponse(
            new BigDecimal("10.00"), new BigDecimal("25.01"), new BigDecimal("-15.01")),
        balance);
    verify(repository).findAll();
    verifyNoMoreInteractions(repository);
  }

  private static TransactionRequest validRequest() {
    return new TransactionRequest(
        "Nómina", "Pago mensual", new BigDecimal("45.10"), TransactionType.INCOME, TODAY);
  }

  private static Stream<Arguments> invalidRequests() {
    return Stream.of(
        invalid("concepto nulo", null, null, "1.00", TransactionType.INCOME, TODAY, "concept"),
        invalid("concepto vacío", "", null, "1.00", TransactionType.INCOME, TODAY, "concept"),
        invalid(
            "concepto en blanco", " \t ", null, "1.00", TransactionType.INCOME, TODAY, "concept"),
        invalid(
            "concepto demasiado largo",
            "a".repeat(101),
            null,
            "1.00",
            TransactionType.INCOME,
            TODAY,
            "concept"),
        invalid(
            "descripción demasiado larga",
            "Concepto",
            "a".repeat(501),
            "1.00",
            TransactionType.INCOME,
            TODAY,
            "description"),
        invalid("importe nulo", "Concepto", null, null, TransactionType.INCOME, TODAY, "amount"),
        invalid("importe cero", "Concepto", null, "0", TransactionType.INCOME, TODAY, "amount"),
        invalid(
            "importe negativo", "Concepto", null, "-1.00", TransactionType.INCOME, TODAY, "amount"),
        invalid(
            "más de dos decimales",
            "Concepto",
            null,
            "1.001",
            TransactionType.INCOME,
            TODAY,
            "amount"),
        invalid(
            "más de doce dígitos enteros",
            "Concepto",
            null,
            "1000000000000.00",
            TransactionType.INCOME,
            TODAY,
            "amount"),
        invalid(
            "exponente que excede doce dígitos",
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

  private static FinancialTransaction transaction(
      Long id,
      String concept,
      String description,
      String amount,
      TransactionType type,
      LocalDate date) {
    FinancialTransaction transaction =
        new FinancialTransaction(concept, description, new BigDecimal(amount), type, date);
    transaction.setId(id);
    return transaction;
  }
}
