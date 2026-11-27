package com.buscard.backend.repo;

import com.buscard.backend.domain.TransactionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    boolean existsByCardIdAndTxCounter(String cardId, long txCounter);

    List<TransactionEntity> findByCardIdOrderByTxCounterDesc(String cardId);

    List<TransactionEntity> findTop50ByOrderByRecordedAtDesc();
}
