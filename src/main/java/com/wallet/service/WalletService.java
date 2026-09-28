package com.wallet.service;

import com.wallet.dto.*;
import com.wallet.exception.DuplicateWalletException;
import com.wallet.exception.InsufficientFundsException;
import com.wallet.exception.InvalidOperationException;
import com.wallet.exception.WalletNotFoundException;
import com.wallet.mapper.TransactionMapper;
import com.wallet.mapper.WalletMapper;
import com.wallet.model.Transaction;
import com.wallet.model.TransactionType;
import com.wallet.model.Wallet;
import com.wallet.repository.TransactionRepository;
import com.wallet.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WalletMapper walletMapper;
    private final TransactionMapper transactionMapper;

    public WalletService(WalletRepository walletRepository,
                         TransactionRepository transactionRepository,
                         WalletMapper walletMapper,
                         TransactionMapper transactionMapper) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.walletMapper = walletMapper;
        this.transactionMapper = transactionMapper;
    }

    // -----------------------------------------------------------------------
    // Create
    // -----------------------------------------------------------------------

    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        if (walletRepository.existsByUserId(request.userId())) {
            throw new DuplicateWalletException(
                    "A wallet already exists for userId: " + request.userId());
        }

        Wallet wallet = Wallet.builder()
                .userId(request.userId())
                .balance(BigDecimal.ZERO)
                .currency(request.currency())
                .build();

        wallet = walletRepository.save(wallet);
        log.info("Wallet created: id={} userId={} currency={}", wallet.getId(), wallet.getUserId(), wallet.getCurrency());
        return walletMapper.toResponse(wallet);
    }

    // -----------------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public WalletResponse getWalletById(UUID walletId) {
        return walletMapper.toResponse(findWalletOrThrow(walletId));
    }

    @Transactional(readOnly = true)
    public WalletResponse getWalletByUserId(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException("No wallet found for userId: " + userId));
        return walletMapper.toResponse(wallet);
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(UUID walletId) {
        Wallet wallet = findWalletOrThrow(walletId);
        return new BalanceResponse(wallet.getId(), wallet.getUserId(),
                wallet.getBalance(), wallet.getCurrency(), Instant.now());
    }

    /**
     * Returns the wallet balance at a specific point in time.
     *
     * <p>Strategy: find the most recent Transaction record whose createdAt <= at.
     * That record's balanceAfter is the balance at that instant.
     * If no transactions exist before that point but the wallet existed, balance is 0.
     * If the wallet was created after the requested instant, a 404 is returned.
     */
    @Transactional(readOnly = true)
    public BalanceResponse getHistoricalBalance(UUID walletId, Instant at) {
        Wallet wallet = findWalletOrThrow(walletId);

        if (at.isBefore(wallet.getCreatedAt())) {
            throw new InvalidOperationException(
                    "Requested timestamp is before the wallet was created at " + wallet.getCreatedAt());
        }

        BigDecimal historicalBalance = transactionRepository
                .findLatestBeforeOrAt(walletId, at)
                .map(Transaction::getBalanceAfter)
                .orElse(BigDecimal.ZERO);

        return new BalanceResponse(wallet.getId(), wallet.getUserId(), historicalBalance, wallet.getCurrency(), at);
    }

    // -----------------------------------------------------------------------
    // Operations
    // -----------------------------------------------------------------------

    /**
     * Deposit funds. If an idempotencyKey is provided and a matching transaction
     * already exists, returns it immediately without re-processing.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public TransactionResponse deposit(UUID walletId, DepositRequest request) {
        if (request.idempotencyKey() != null) {
            return transactionRepository.findByIdempotencyKey(request.idempotencyKey())
                    .map(existing -> resolveIdempotent(existing, walletId))
                    .orElseGet(() -> executeDeposit(walletId, request));
        }
        return executeDeposit(walletId, request);
    }

    private TransactionResponse executeDeposit(UUID walletId, DepositRequest request) {
        Wallet wallet = findWalletWithLockOrThrow(walletId);

        BigDecimal newBalance = wallet.getBalance().add(request.amount());
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        Transaction tx = transactionRepository.save(Transaction.builder()
                .walletId(wallet.getId())
                .type(TransactionType.DEPOSIT)
                .amount(request.amount())
                .balanceAfter(newBalance)
                .currency(wallet.getCurrency())
                .description(request.description())
                .idempotencyKey(request.idempotencyKey())
                .build());

        log.info("DEPOSIT walletId={} amount={} newBalance={}", walletId, request.amount(), newBalance);
        return transactionMapper.toResponse(tx);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public TransactionResponse withdraw(UUID walletId, WithdrawRequest request) {
        if (request.idempotencyKey() != null) {
            return transactionRepository.findByIdempotencyKey(request.idempotencyKey())
                    .map(existing -> resolveIdempotent(existing, walletId))
                    .orElseGet(() -> executeWithdraw(walletId, request));
        }
        return executeWithdraw(walletId, request);
    }

    private TransactionResponse executeWithdraw(UUID walletId, WithdrawRequest request) {
        Wallet wallet = findWalletWithLockOrThrow(walletId);

        if (wallet.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds: available=" + wallet.getBalance() + ", requested=" + request.amount());
        }

        BigDecimal newBalance = wallet.getBalance().subtract(request.amount());
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        Transaction tx = transactionRepository.save(Transaction.builder()
                .walletId(wallet.getId())
                .type(TransactionType.WITHDRAWAL)
                .amount(request.amount().negate())
                .balanceAfter(newBalance)
                .currency(wallet.getCurrency())
                .description(request.description())
                .idempotencyKey(request.idempotencyKey())
                .build());

        log.info("WITHDRAWAL walletId={} amount={} newBalance={}", walletId, request.amount(), newBalance);
        return transactionMapper.toResponse(tx);
    }

    /**
     * Transfer funds between two wallets.
     *
     * <p>Deadlock prevention: wallets are always locked in ascending UUID order
     * so that concurrent transfers between the same pair never deadlock.
     * The idempotencyKey is stored on the DEBIT transaction (the one returned).
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public TransactionResponse transfer(UUID sourceWalletId, TransferRequest request) {
        if (sourceWalletId.equals(request.targetWalletId())) {
            throw new InvalidOperationException("Source and target wallets must be different");
        }

        if (request.idempotencyKey() != null) {
            return transactionRepository.findByIdempotencyKey(request.idempotencyKey())
                    .map(existing -> resolveIdempotent(existing, sourceWalletId))
                    .orElseGet(() -> executeTransfer(sourceWalletId, request));
        }
        return executeTransfer(sourceWalletId, request);
    }

    private TransactionResponse executeTransfer(UUID sourceWalletId, TransferRequest request) {
        UUID targetWalletId = request.targetWalletId();

        UUID lowerUuid  = sourceWalletId.compareTo(targetWalletId) < 0 ? sourceWalletId : targetWalletId;
        UUID higherUuid = lowerUuid.equals(sourceWalletId) ? targetWalletId : sourceWalletId;

        Wallet lowerWallet  = findWalletWithLockOrThrow(lowerUuid);
        Wallet higherWallet = findWalletWithLockOrThrow(higherUuid);

        Wallet source = lowerWallet.getId().equals(sourceWalletId) ? lowerWallet : higherWallet;
        Wallet target = lowerWallet.getId().equals(targetWalletId) ? lowerWallet : higherWallet;

        if (!source.getCurrency().equals(target.getCurrency())) {
            throw new InvalidOperationException(
                    "Cross-currency transfers are not supported: source=" + source.getCurrency()
                    + ", target=" + target.getCurrency());
        }

        if (source.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds: available=" + source.getBalance() + ", requested=" + request.amount());
        }

        UUID correlationId = UUID.randomUUID();

        BigDecimal sourceNewBalance = source.getBalance().subtract(request.amount());
        source.setBalance(sourceNewBalance);
        walletRepository.save(source);

        BigDecimal targetNewBalance = target.getBalance().add(request.amount());
        target.setBalance(targetNewBalance);
        walletRepository.save(target);

        Transaction debitTx = transactionRepository.save(Transaction.builder()
                .walletId(source.getId())
                .type(TransactionType.TRANSFER_DEBIT)
                .amount(request.amount().negate())
                .balanceAfter(sourceNewBalance)
                .currency(source.getCurrency())
                .correlationId(correlationId)
                .counterpartWalletId(target.getId())
                .description(request.description())
                .idempotencyKey(request.idempotencyKey())
                .build());

        transactionRepository.save(Transaction.builder()
                .walletId(target.getId())
                .type(TransactionType.TRANSFER_CREDIT)
                .amount(request.amount())
                .balanceAfter(targetNewBalance)
                .currency(target.getCurrency())
                .correlationId(correlationId)
                .counterpartWalletId(source.getId())
                .description(request.description())
                .build());

        log.info("TRANSFER correlationId={} source={} target={} amount={}",
                correlationId, sourceWalletId, targetWalletId, request.amount());

        return transactionMapper.toResponse(debitTx);
    }

    // -----------------------------------------------------------------------
    // Transaction history
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(UUID walletId, Pageable pageable) {
        findWalletOrThrow(walletId);
        return transactionRepository.findByWalletIdOrderByCreatedAtDesc(walletId, pageable)
                .map(transactionMapper::toResponse);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Wallet findWalletOrThrow(UUID walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));
    }

    private Wallet findWalletWithLockOrThrow(UUID walletId) {
        return walletRepository.findByIdWithLock(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));
    }

    private TransactionResponse resolveIdempotent(Transaction existing, UUID expectedWalletId) {
        if (!existing.getWalletId().equals(expectedWalletId)) {
            throw new InvalidOperationException("Idempotency key already used for a different wallet");
        }
        return transactionMapper.toResponse(existing);
    }
}
