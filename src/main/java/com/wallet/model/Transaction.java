package com.wallet.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable ledger entry. Each financial operation appends one (or two) records here.
 * This table is the single source of truth for auditability and historical balance queries.
 * Records are NEVER updated or deleted.
 */
@Entity
@Table(
    name = "transactions",
    indexes = {
        @Index(name = "idx_transactions_wallet_id", columnList = "wallet_id"),
        @Index(name = "idx_transactions_created_at", columnList = "created_at"),
        @Index(name = "idx_transactions_correlation_id", columnList = "correlation_id")
    }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 20)
    private TransactionType type;

    /**
     * Positive for credits (deposits, incoming transfers),
     * negative for debits (withdrawals, outgoing transfers).
     */
    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /**
     * Snapshot of the wallet balance AFTER this transaction was applied.
     * Enables O(1) historical balance lookup at any transaction boundary.
     */
    @Column(name = "balance_after", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    /**
     * Links the two sides of a transfer (debit + credit share the same correlationId).
     */
    @Column(name = "correlation_id", updatable = false)
    private UUID correlationId;

    /**
     * For transfers: the counterpart wallet.
     */
    @Column(name = "counterpart_wallet_id", updatable = false)
    private UUID counterpartWalletId;

    @Column(name = "description", updatable = false, length = 255)
    private String description;

    @Column(name = "idempotency_key", unique = true, updatable = false)
    private UUID idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
