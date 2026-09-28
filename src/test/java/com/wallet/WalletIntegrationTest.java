package com.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.dto.*;
import com.wallet.model.Wallet;
import com.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser
class WalletIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    WalletRepository walletRepository;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    // -----------------------------------------------------------------------
    // Create wallet
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /wallets - creates wallet and returns 201")
    void createWallet() throws Exception {
        mvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateWalletRequest(userId, "BRL"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.currency").value("BRL"));
    }

    @Test
    @DisplayName("POST /wallets - returns 409 on duplicate userId")
    void createWallet_duplicate() throws Exception {
        createWalletForUser(userId);

        mvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateWalletRequest(userId, "BRL"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /wallets - returns 400 on missing userId")
    void createWallet_missingUserId() throws Exception {
        mvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"BRL\"}"))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // Deposit
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /wallets/{id}/deposit - increases balance")
    void deposit_increasesBalance() throws Exception {
        WalletResponse wallet = createWalletForUser(userId);

        mvc.perform(post("/api/v1/wallets/" + wallet.id() + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DepositRequest(new BigDecimal("250.00"), "test deposit", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.balanceAfter").value(250.00));
    }

    @Test
    @DisplayName("POST /wallets/{id}/deposit - returns 400 for zero amount")
    void deposit_zeroAmount() throws Exception {
        WalletResponse wallet = createWalletForUser(userId);

        mvc.perform(post("/api/v1/wallets/" + wallet.id() + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DepositRequest(BigDecimal.ZERO, null, null))))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // Withdraw
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /wallets/{id}/withdraw - decreases balance")
    void withdraw_decreasesBalance() throws Exception {
        WalletResponse wallet = createWalletForUser(userId);
        depositTo(wallet.id(), new BigDecimal("100.00"));

        mvc.perform(post("/api/v1/wallets/" + wallet.id() + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WithdrawRequest(new BigDecimal("40.00"), null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.balanceAfter").value(60.00));
    }

    @Test
    @DisplayName("POST /wallets/{id}/withdraw - returns 422 on insufficient funds")
    void withdraw_insufficientFunds() throws Exception {
        WalletResponse wallet = createWalletForUser(userId);

        mvc.perform(post("/api/v1/wallets/" + wallet.id() + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WithdrawRequest(new BigDecimal("1.00"), null, null))))
                .andExpect(status().isUnprocessableEntity());
    }

    // -----------------------------------------------------------------------
    // Transfer
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /wallets/{id}/transfer - moves funds between wallets")
    void transfer_success() throws Exception {
        WalletResponse source = createWalletForUser(userId);
        WalletResponse target = createWalletForUser(UUID.randomUUID());
        depositTo(source.id(), new BigDecimal("200.00"));

        mvc.perform(post("/api/v1/wallets/" + source.id() + "/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransferRequest(target.id(), new BigDecimal("70.00"), null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TRANSFER_DEBIT"))
                .andExpect(jsonPath("$.balanceAfter").value(130.00));

        mvc.perform(get("/api/v1/wallets/" + target.id() + "/balance"))
                .andExpect(jsonPath("$.balance").value(70.00));
    }

    @Test
    @DisplayName("POST /wallets/{id}/transfer - returns 400 for self-transfer")
    void transfer_selfTransfer() throws Exception {
        WalletResponse wallet = createWalletForUser(userId);
        depositTo(wallet.id(), new BigDecimal("100.00"));

        mvc.perform(post("/api/v1/wallets/" + wallet.id() + "/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransferRequest(wallet.id(), new BigDecimal("10.00"), null, null))))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // Balance / history
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /wallets/{id}/balance - returns current balance")
    void getBalance() throws Exception {
        WalletResponse wallet = createWalletForUser(userId);
        depositTo(wallet.id(), new BigDecimal("500.00"));

        mvc.perform(get("/api/v1/wallets/" + wallet.id() + "/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(500.00));
    }

    @Test
    @DisplayName("GET /wallets/{id}/balance/history - returns historical balance")
    void getHistoricalBalance() throws Exception {
        WalletResponse wallet = createWalletForUser(userId);
        depositTo(wallet.id(), new BigDecimal("100.00"));
        String now = java.time.Instant.now().plusSeconds(1).toString();

        mvc.perform(get("/api/v1/wallets/" + wallet.id() + "/balance/history")
                        .param("at", now))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    @DisplayName("GET /wallets/{id}/transactions - returns paged transaction list")
    void getTransactions() throws Exception {
        WalletResponse wallet = createWalletForUser(userId);
        depositTo(wallet.id(), new BigDecimal("100.00"));

        mvc.perform(get("/api/v1/wallets/" + wallet.id() + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].type").value("DEPOSIT"));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private WalletResponse createWalletForUser(UUID uid) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateWalletRequest(uid, "BRL"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), WalletResponse.class);
    }

    private void depositTo(UUID walletId, BigDecimal amount) throws Exception {
        mvc.perform(post("/api/v1/wallets/" + walletId + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DepositRequest(amount, null, null))))
                .andExpect(status().isOk());
    }
}
