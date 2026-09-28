package com.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record CreateWalletRequest(

    @NotNull(message = "userId is required")
    UUID userId,

    @Pattern(regexp = "[A-Z]{3}", message = "currency must be a 3-letter ISO 4217 code (e.g. BRL, USD)")
    String currency
) {
    public CreateWalletRequest {
        // default currency
        if (currency == null || currency.isBlank()) {
            currency = "BRL";
        }
    }
}
