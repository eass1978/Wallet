package com.wallet.controller;

import com.wallet.dto.*;
import com.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@Tag(name = "Wallets", description = "Wallet management and operations")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    // -----------------------------------------------------------------------
    // Wallet CRUD
    // -----------------------------------------------------------------------

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a wallet for a user")
    public WalletResponse createWallet(@Valid @RequestBody CreateWalletRequest request) {
        return walletService.createWallet(request);
    }

    @GetMapping("/{walletId}")
    @Operation(summary = "Get wallet by ID")
    public WalletResponse getWallet(@PathVariable UUID walletId) {
        return walletService.getWalletById(walletId);
    }

    @GetMapping("/by-user/{userId}")
    @Operation(summary = "Get wallet by user ID")
    public WalletResponse getWalletByUser(@PathVariable UUID userId) {
        return walletService.getWalletByUserId(userId);
    }

    // -----------------------------------------------------------------------
    // Balance
    // -----------------------------------------------------------------------

    @GetMapping("/{walletId}/balance")
    @Operation(summary = "Retrieve current balance")
    public BalanceResponse getBalance(@PathVariable UUID walletId) {
        return walletService.getBalance(walletId);
    }

    @GetMapping("/{walletId}/balance/history")
    @Operation(summary = "Retrieve historical balance at a specific point in time")
    public BalanceResponse getHistoricalBalance(
            @PathVariable UUID walletId,
            @RequestParam
            @Parameter(description = "ISO-8601 timestamp, e.g. 2024-06-01T12:00:00Z")
            Instant at) {
        return walletService.getHistoricalBalance(walletId, at);
    }

    // -----------------------------------------------------------------------
    // Operations
    // -----------------------------------------------------------------------

    @PostMapping("/{walletId}/deposit")
    @Operation(summary = "Deposit funds into a wallet")
    public TransactionResponse deposit(
            @PathVariable UUID walletId,
            @Valid @RequestBody DepositRequest request) {
        return walletService.deposit(walletId, request);
    }

    @PostMapping("/{walletId}/withdraw")
    @Operation(summary = "Withdraw funds from a wallet")
    public TransactionResponse withdraw(
            @PathVariable UUID walletId,
            @Valid @RequestBody WithdrawRequest request) {
        return walletService.withdraw(walletId, request);
    }

    @PostMapping("/{walletId}/transfer")
    @Operation(summary = "Transfer funds to another wallet")
    public TransactionResponse transfer(
            @PathVariable UUID walletId,
            @Valid @RequestBody TransferRequest request) {
        return walletService.transfer(walletId, request);
    }

    // -----------------------------------------------------------------------
    // Transaction history
    // -----------------------------------------------------------------------

    @GetMapping("/{walletId}/transactions")
    @Operation(summary = "Retrieve paginated transaction history for a wallet")
    public Page<TransactionResponse> getTransactions(
            @PathVariable UUID walletId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return walletService.getTransactions(walletId, pageable);
    }
}
