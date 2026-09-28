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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private final WalletMapper walletMapper = Mappers.getMapper(WalletMapper.class);
    private final TransactionMapper transactionMapper = Mappers.getMapper(TransactionMapper.class);

    private WalletService walletService;

    private UUID walletId;
    private UUID userId;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(walletRepository, transactionRepository, walletMapper, transactionMapper);
        walletId = UUID.randomUUID();
        userId = UUID.randomUUID();
        wallet = Wallet.builder()
                .id(walletId)
                .userId(userId)
                .balance(new BigDecimal("100.00"))
                .currency("BRL")
                .version(0L)
                .createdAt(Instant.now().minusSeconds(3600))
                .updatedAt(Instant.now())
                .build();
    }

    // -----------------------------------------------------------------------
    // createWallet
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("createWallet - success")
    void createWallet_success() {
        when(walletRepository.existsByUserId(userId)).thenReturn(false);
        when(walletRepository.save(any())).thenReturn(wallet);

        WalletResponse response = walletService.createWallet(new CreateWalletRequest(userId, "BRL"));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.balance()).isEqualByComparingTo(wallet.getBalance());
        verify(walletRepository).save(any(Wallet.class));
    }

    @Test
    @DisplayName("createWallet - throws DuplicateWalletException when user already has a wallet")
    void createWallet_duplicate() {
        when(walletRepository.existsByUserId(userId)).thenReturn(true);

        assertThatThrownBy(() -> walletService.createWallet(new CreateWalletRequest(userId, "BRL")))
                .isInstanceOf(DuplicateWalletException.class);
    }

    // -----------------------------------------------------------------------
    // deposit
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("deposit - adds amount to balance and creates DEPOSIT transaction")
    void deposit_success() {
        when(walletRepository.findByIdWithLock(walletId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenReturn(wallet);

        Transaction savedTx = buildTransaction(TransactionType.DEPOSIT, new BigDecimal("50.00"), new BigDecimal("150.00"));
        when(transactionRepository.save(any())).thenReturn(savedTx);

        TransactionResponse response = walletService.deposit(walletId, new DepositRequest(new BigDecimal("50.00"), null, null));

        assertThat(response.type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(response.balanceAfter()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    @DisplayName("deposit - returns existing transaction for duplicate idempotency key")
    void deposit_idempotentDuplicate() {
        UUID idempotencyKey = UUID.randomUUID();
        Transaction existingTx = buildTransaction(TransactionType.DEPOSIT, new BigDecimal("50.00"), new BigDecimal("150.00"));
        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingTx));

        TransactionResponse response = walletService.deposit(walletId,
                new DepositRequest(new BigDecimal("50.00"), null, idempotencyKey));

        assertThat(response.type()).isEqualTo(TransactionType.DEPOSIT);
        verify(walletRepository, never()).findByIdWithLock(any());
    }

    @Test
    @DisplayName("deposit - throws WalletNotFoundException for unknown wallet")
    void deposit_walletNotFound() {
        when(walletRepository.findByIdWithLock(walletId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.deposit(walletId, new DepositRequest(BigDecimal.TEN, null, null)))
                .isInstanceOf(WalletNotFoundException.class);
    }

    // -----------------------------------------------------------------------
    // withdraw
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("withdraw - deducts amount and creates WITHDRAWAL transaction")
    void withdraw_success() {
        when(walletRepository.findByIdWithLock(walletId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenReturn(wallet);

        Transaction savedTx = buildTransaction(TransactionType.WITHDRAWAL, new BigDecimal("-50.00"), new BigDecimal("50.00"));
        when(transactionRepository.save(any())).thenReturn(savedTx);

        TransactionResponse response = walletService.withdraw(walletId, new WithdrawRequest(new BigDecimal("50.00"), null, null));

        assertThat(response.type()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(response.balanceAfter()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("withdraw - returns existing transaction for duplicate idempotency key")
    void withdraw_idempotentDuplicate() {
        UUID idempotencyKey = UUID.randomUUID();
        Transaction existingTx = buildTransaction(TransactionType.WITHDRAWAL, new BigDecimal("-50.00"), new BigDecimal("50.00"));
        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingTx));

        TransactionResponse response = walletService.withdraw(walletId,
                new WithdrawRequest(new BigDecimal("50.00"), null, idempotencyKey));

        assertThat(response.type()).isEqualTo(TransactionType.WITHDRAWAL);
        verify(walletRepository, never()).findByIdWithLock(any());
    }

    @Test
    @DisplayName("withdraw - throws InsufficientFundsException when balance is too low")
    void withdraw_insufficientFunds() {
        when(walletRepository.findByIdWithLock(walletId)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> walletService.withdraw(walletId, new WithdrawRequest(new BigDecimal("200.00"), null, null)))
                .isInstanceOf(InsufficientFundsException.class);
    }

    // -----------------------------------------------------------------------
    // transfer
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("transfer - moves funds and creates DEBIT + CREDIT transactions")
    void transfer_success() {
        UUID targetId = UUID.randomUUID();
        Wallet target = Wallet.builder()
                .id(targetId)
                .userId(UUID.randomUUID())
                .balance(BigDecimal.ZERO)
                .currency("BRL")
                .version(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        UUID lowerUuid  = walletId.compareTo(targetId) < 0 ? walletId : targetId;
        UUID higherUuid = lowerUuid.equals(walletId) ? targetId : walletId;

        when(walletRepository.findByIdWithLock(lowerUuid))
                .thenReturn(Optional.of(lowerUuid.equals(walletId) ? wallet : target));
        when(walletRepository.findByIdWithLock(higherUuid))
                .thenReturn(Optional.of(higherUuid.equals(walletId) ? wallet : target));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = walletService.transfer(walletId,
                new TransferRequest(targetId, new BigDecimal("30.00"), null, null));

        assertThat(response.type()).isEqualTo(TransactionType.TRANSFER_DEBIT);
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("-30.00"));
        verify(transactionRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("transfer - returns existing transaction for duplicate idempotency key")
    void transfer_idempotentDuplicate() {
        UUID idempotencyKey = UUID.randomUUID();
        Transaction existingTx = buildTransaction(TransactionType.TRANSFER_DEBIT, new BigDecimal("-30.00"), new BigDecimal("70.00"));
        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingTx));

        TransactionResponse response = walletService.transfer(walletId,
                new TransferRequest(UUID.randomUUID(), new BigDecimal("30.00"), null, idempotencyKey));

        assertThat(response.type()).isEqualTo(TransactionType.TRANSFER_DEBIT);
        verify(walletRepository, never()).findByIdWithLock(any());
    }

    @Test
    @DisplayName("transfer - throws InvalidOperationException for self-transfer")
    void transfer_selfTransfer() {
        assertThatThrownBy(() -> walletService.transfer(walletId,
                new TransferRequest(walletId, BigDecimal.TEN, null, null)))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("transfer - throws InsufficientFundsException when balance too low")
    void transfer_insufficientFunds() {
        UUID targetId = UUID.randomUUID();
        Wallet target = Wallet.builder().id(targetId).userId(UUID.randomUUID())
                .balance(BigDecimal.ZERO).currency("BRL").version(0L)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();

        UUID lowerUuid  = walletId.compareTo(targetId) < 0 ? walletId : targetId;
        UUID higherUuid = lowerUuid.equals(walletId) ? targetId : walletId;

        when(walletRepository.findByIdWithLock(lowerUuid))
                .thenReturn(Optional.of(lowerUuid.equals(walletId) ? wallet : target));
        when(walletRepository.findByIdWithLock(higherUuid))
                .thenReturn(Optional.of(higherUuid.equals(walletId) ? wallet : target));

        assertThatThrownBy(() -> walletService.transfer(walletId,
                new TransferRequest(targetId, new BigDecimal("999.00"), null, null)))
                .isInstanceOf(InsufficientFundsException.class);
    }

    // -----------------------------------------------------------------------
    // getHistoricalBalance
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getHistoricalBalance - returns balanceAfter of last tx before requested time")
    void historicalBalance_found() {
        Instant queryTime = Instant.now();
        Transaction tx = buildTransaction(TransactionType.DEPOSIT, BigDecimal.TEN, new BigDecimal("60.00"));
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findLatestBeforeOrAt(walletId, queryTime)).thenReturn(Optional.of(tx));

        BalanceResponse response = walletService.getHistoricalBalance(walletId, queryTime);

        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("60.00"));
    }

    @Test
    @DisplayName("getHistoricalBalance - returns zero when no transactions existed yet")
    void historicalBalance_noTransactions() {
        Instant queryTime = Instant.now();
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findLatestBeforeOrAt(walletId, queryTime)).thenReturn(Optional.empty());

        BalanceResponse response = walletService.getHistoricalBalance(walletId, queryTime);

        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getHistoricalBalance - throws InvalidOperationException when querying before wallet creation")
    void historicalBalance_beforeCreation() {
        Instant before = wallet.getCreatedAt().minusSeconds(1);
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> walletService.getHistoricalBalance(walletId, before))
                .isInstanceOf(InvalidOperationException.class);
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private Transaction buildTransaction(TransactionType type, BigDecimal amount, BigDecimal balanceAfter) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .walletId(walletId)
                .type(type)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .currency("BRL")
                .createdAt(Instant.now())
                .build();
    }
}
