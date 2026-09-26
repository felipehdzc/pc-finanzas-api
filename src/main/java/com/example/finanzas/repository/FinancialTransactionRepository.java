package com.example.finanzas.repository;

import com.example.finanzas.domain.FinancialTransaction;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {

    List<FinancialTransaction> findByDateBetween(LocalDate from, LocalDate to, Sort sort);

    List<FinancialTransaction> findByDateGreaterThanEqual(LocalDate from, Sort sort);

    List<FinancialTransaction> findByDateLessThanEqual(LocalDate to, Sort sort);
}
