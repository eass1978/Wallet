package com.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletResponse(
    UUID id,
    UUID userId,
    BigDecimal balance,
    String currency,
    Instant createdAt,
    Instant updatedAt
) {}
