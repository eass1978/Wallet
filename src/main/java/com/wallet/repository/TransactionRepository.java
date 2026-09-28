package com.wallet.repository;

import com.wallet.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    /**
     * Finds the most recent transaction for a wallet at or before a given point in time.
     * The balanceAfter of that row IS the historical balance at that instant.
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.walletId = :walletId
              AND t.createdAt <= :at
            ORDER BY t.createdAt DESC, t.id DESC
            LIMIT 1
            """)
    Optional<Transaction> findLatestBeforeOrAt(@Param("walletId") UUID walletId,
                                               @Param("at") Instant at);

}
