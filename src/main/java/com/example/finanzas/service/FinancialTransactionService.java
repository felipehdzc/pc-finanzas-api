package com.example.finanzas.service;

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
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FinancialTransactionService {

  private final FinancialTransactionRepository repository;
  private final Clock clock;

  public FinancialTransactionService(FinancialTransactionRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional
  public TransactionResponse create(TransactionRequest request) {
    validate(request);
    FinancialTransaction transaction =
        new FinancialTransaction(
            request.concept(),
            request.description(),
            request.amount().setScale(2),
            request.type(),
            request.date());
    return TransactionResponse.from(repository.save(transaction));
  }

  public List<TransactionResponse> findAll() {
    return findAll(null, null);
  }

  public List<TransactionResponse> findAll(LocalDate from, LocalDate to) {
    if (from != null && to != null && from.isAfter(to)) {
      String message = "La fecha 'from' no puede ser posterior a 'to'.";
      throw new BusinessValidationException(message, Map.of("from", message));
    }

    Sort sort = Sort.by("id");
    List<FinancialTransaction> transactions;
    if (from != null && to != null) {
      transactions = repository.findByDateBetween(from, to, sort);
    } else if (from != null) {
      transactions = repository.findByDateGreaterThanEqual(from, sort);
    } else if (to != null) {
      transactions = repository.findByDateLessThanEqual(to, sort);
    } else {
      transactions = repository.findAll(sort);
    }

    return transactions.stream().map(TransactionResponse::from).toList();
  }

  public TransactionResponse findById(Long id) {
    return TransactionResponse.from(findTransaction(id));
  }

  @Transactional
  public TransactionResponse update(Long id, TransactionRequest request) {
    FinancialTransaction transaction = findTransaction(id);
    // Validar antes de modificar la entidad gestionada: Hibernate detecta sus cambios.
    validate(request);
    transaction.setConcept(request.concept());
    transaction.setDescription(request.description());
    transaction.setAmount(request.amount().setScale(2));
    transaction.setType(request.type());
    transaction.setDate(request.date());
    return TransactionResponse.from(repository.save(transaction));
  }

  @Transactional
  public void delete(Long id) {
    repository.delete(findTransaction(id));
  }

  public BalanceResponse getBalance() {
    BigDecimal totalIncome = new BigDecimal("0.00");
    BigDecimal totalExpense = new BigDecimal("0.00");
    for (FinancialTransaction transaction : repository.findAll()) {
      if (transaction.getType() == TransactionType.INCOME) {
        totalIncome = totalIncome.add(transaction.getAmount());
      } else {
        totalExpense = totalExpense.add(transaction.getAmount());
      }
    }
    return new BalanceResponse(totalIncome, totalExpense, totalIncome.subtract(totalExpense));
  }

  private FinancialTransaction findTransaction(Long id) {
    return repository.findById(id).orElseThrow(() -> new TransactionNotFoundException(id));
  }

  private void validate(TransactionRequest request) {
    if (request == null) {
      throw new BusinessValidationException(Map.of("body", "El movimiento es obligatorio"));
    }

    Map<String, String> errors = new LinkedHashMap<>();
    if (request.concept() == null || request.concept().isBlank()) {
      errors.put("concept", "El concepto es obligatorio y no puede estar vacío");
    } else if (request.concept().length() > 100) {
      errors.put("concept", "El concepto no puede superar los 100 caracteres");
    }
    if (request.description() != null && request.description().length() > 500) {
      errors.put("description", "La descripción no puede superar los 500 caracteres");
    }

    BigDecimal amount = request.amount();
    if (amount == null) {
      errors.put("amount", "El importe es obligatorio");
    } else if (amount.signum() <= 0) {
      errors.put("amount", "El importe debe ser mayor que cero");
    } else if (amount.precision() - (long) amount.scale() > 12 || amount.scale() > 2) {
      errors.put("amount", "El importe admite como máximo 12 dígitos enteros y 2 decimales");
    }

    if (request.type() == null) {
      errors.put("type", "El tipo es obligatorio: INCOME o EXPENSE");
    }
    if (request.date() == null) {
      errors.put("date", "La fecha es obligatoria");
    } else if (request.date().isAfter(LocalDate.now(clock))) {
      errors.put("date", "La fecha no puede ser futura");
    }

    if (!errors.isEmpty()) {
      throw new BusinessValidationException(errors);
    }
  }
}
