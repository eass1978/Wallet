package com.wallet.dto;

import com.wallet.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
    UUID id,
    UUID walletId,
    TransactionType type,
    BigDecimal amount,
    BigDecimal balanceAfter,
    String currency,
    UUID correlationId,
    UUID counterpartWalletId,
    String description,
    Instant createdAt
) {}
