package com.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DepositRequest(

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    @Digits(integer = 15, fraction = 4, message = "amount must have at most 15 integer digits and 4 decimal places")
    BigDecimal amount,

    @Size(max = 255)
    String description
) {}
