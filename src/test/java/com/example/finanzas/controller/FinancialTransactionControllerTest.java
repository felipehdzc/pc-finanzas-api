package com.example.finanzas.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.finanzas.domain.TransactionType;
import com.example.finanzas.dto.BalanceResponse;
import com.example.finanzas.dto.TransactionRequest;
import com.example.finanzas.dto.TransactionResponse;
import com.example.finanzas.service.FinancialTransactionService;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class FinancialTransactionControllerTest {

  @Mock private FinancialTransactionService service;

  private FinancialTransactionController controller;

  private final TransactionRequest request =
      new TransactionRequest(
          "Compra",
          "Supermercado",
          new BigDecimal("35.50"),
          TransactionType.EXPENSE,
          LocalDate.of(2026, 9, 21));

  private final TransactionResponse response =
      new TransactionResponse(
          7L,
          "Compra",
          "Supermercado",
          new BigDecimal("35.50"),
          TransactionType.EXPENSE,
          LocalDate.of(2026, 9, 21));

  @BeforeEach
  void setUp() {
    controller = new FinancialTransactionController(service);
  }

  @Test
  void createReturns201WithLocationAndCreatedTransaction() {
    when(service.create(request)).thenReturn(response);

    ResponseEntity<TransactionResponse> result = controller.create(request);

    assertEquals(HttpStatus.CREATED, result.getStatusCode());
    assertEquals(URI.create("/api/transactions/7"), result.getHeaders().getLocation());
    assertEquals(response, result.getBody());
    verify(service).create(request);
  }

  @Test
  void findAllWithoutFiltersReturnsTransactionsFromService() {
    when(service.findAll(null, null)).thenReturn(List.of(response));

    assertEquals(List.of(response), controller.findAll(null, null));
    verify(service).findAll(null, null);
  }

  @Test
  void findAllWithBothDatesPassesTheRangeToService() {
    LocalDate from = LocalDate.of(2026, 9, 1);
    LocalDate to = LocalDate.of(2026, 9, 30);
    when(service.findAll(from, to)).thenReturn(List.of(response));

    assertEquals(List.of(response), controller.findAll(from, to));
    verify(service).findAll(from, to);
  }

  @Test
  void findAllWithOnlyFromPassesAnOpenEndedRangeToService() {
    LocalDate from = LocalDate.of(2026, 9, 1);
    when(service.findAll(from, null)).thenReturn(List.of(response));

    assertEquals(List.of(response), controller.findAll(from, null));
    verify(service).findAll(from, null);
  }

  @Test
  void findAllWithOnlyToPassesAnOpenStartedRangeToService() {
    LocalDate to = LocalDate.of(2026, 9, 30);
    when(service.findAll(null, to)).thenReturn(List.of(response));

    assertEquals(List.of(response), controller.findAll(null, to));
    verify(service).findAll(null, to);
  }

  @Test
  void findAllReturnsEmptyListWhenThereAreNoTransactions() {
    when(service.findAll(null, null)).thenReturn(List.of());

    assertEquals(List.of(), controller.findAll(null, null));
  }

  @Test
  void findByIdReturnsRequestedTransaction() {
    when(service.findById(7L)).thenReturn(response);

    assertEquals(response, controller.findById(7L));
    verify(service).findById(7L);
  }

  @Test
  void updatePassesTheIdentifierAndReturnsUpdatedTransaction() {
    when(service.update(7L, request)).thenReturn(response);

    assertEquals(response, controller.update(7L, request));
    verify(service).update(7L, request);
  }

  @Test
  void deleteReturns204WithoutBody() {
    ResponseEntity<Void> result = controller.delete(7L);

    assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
    assertNull(result.getBody());
    verify(service).delete(7L);
  }

  @Test
  void getBalanceReturnsTotalsCalculatedByService() {
    BalanceResponse balance =
        new BalanceResponse(
            new BigDecimal("100.00"), new BigDecimal("35.50"), new BigDecimal("64.50"));
    when(service.getBalance()).thenReturn(balance);

    assertEquals(balance, controller.getBalance());
  }
}
