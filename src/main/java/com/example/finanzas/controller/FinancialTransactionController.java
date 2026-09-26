package com.example.finanzas.controller;

import com.example.finanzas.dto.BalanceResponse;
import com.example.finanzas.dto.TransactionRequest;
import com.example.finanzas.dto.TransactionResponse;
import com.example.finanzas.service.FinancialTransactionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class FinancialTransactionController {

  private final FinancialTransactionService service;

  public FinancialTransactionController(FinancialTransactionService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<TransactionResponse> create(
      @Valid @RequestBody TransactionRequest request) {
    TransactionResponse created = service.create(request);
    return ResponseEntity.created(URI.create("/api/transactions/" + created.id())).body(created);
  }

  @GetMapping
  public List<TransactionResponse> findAll(
      @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return service.findAll(from, to);
  }

  @GetMapping("/{id}")
  public TransactionResponse findById(@PathVariable("id") Long id) {
    return service.findById(id);
  }

  @PutMapping("/{id}")
  public TransactionResponse update(
      @PathVariable("id") Long id, @Valid @RequestBody TransactionRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/balance")
  public BalanceResponse getBalance() {
    return service.getBalance();
  }
}
