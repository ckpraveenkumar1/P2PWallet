package com.p2pwallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2pwallet.dto.CreateWalletRequest;
import com.p2pwallet.dto.WalletResponse;
import com.p2pwallet.exception.GlobalExceptionHandler;
import com.p2pwallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    @Mock
    private WalletService walletService;

    @InjectMocks
    private WalletController walletController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(walletController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    private WalletResponse createWalletResponse(UUID id, String userId, long balance) {
        return new WalletResponse(id, userId, balance, Instant.now(), Instant.now());
    }

    @Nested
    @DisplayName("POST /wallets")
    class CreateWalletTests {

        @Test
        @DisplayName("should create wallet and return 200")
        void shouldCreateWallet() throws Exception {
            UUID walletId = UUID.randomUUID();
            WalletResponse response = createWalletResponse(walletId, "alice", 0L);

            when(walletService.getOrCreate("alice")).thenReturn(response);

            mockMvc.perform(post("/wallets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"user_id\": \"alice\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user_id").value("alice"))
                    .andExpect(jsonPath("$.balance").value(0));
        }

        @Test
        @DisplayName("should return 400 when user_id is blank")
        void shouldReturn400WhenUserIdBlank() throws Exception {
            mockMvc.perform(post("/wallets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"user_id\": \"\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when user_id is missing")
        void shouldReturn400WhenUserIdMissing() throws Exception {
            mockMvc.perform(post("/wallets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /wallets/{id}")
    class GetWalletTests {

        @Test
        @DisplayName("should return wallet by ID")
        void shouldReturnWallet() throws Exception {
            UUID walletId = UUID.randomUUID();
            WalletResponse response = createWalletResponse(walletId, "alice", 10000L);

            when(walletService.getById(walletId)).thenReturn(response);

            mockMvc.perform(get("/wallets/{id}", walletId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(walletId.toString()))
                    .andExpect(jsonPath("$.balance").value(10000));
        }

        @Test
        @DisplayName("should return 400 when wallet not found")
        void shouldReturn400WhenNotFound() throws Exception {
            UUID walletId = UUID.randomUUID();
            when(walletService.getById(walletId))
                    .thenThrow(new IllegalArgumentException("Wallet not found: " + walletId));

            mockMvc.perform(get("/wallets/{id}", walletId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        }
    }

    @Nested
    @DisplayName("GET /wallets/user/{userId}")
    class GetWalletByUserIdTests {

        @Test
        @DisplayName("should return wallet by user ID")
        void shouldReturnWalletByUserId() throws Exception {
            UUID walletId = UUID.randomUUID();
            WalletResponse response = createWalletResponse(walletId, "bob", 5000L);

            when(walletService.getByUserId("bob")).thenReturn(response);

            mockMvc.perform(get("/wallets/user/{userId}", "bob"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user_id").value("bob"))
                    .andExpect(jsonPath("$.balance").value(5000));
        }

        @Test
        @DisplayName("should return 400 when user not found")
        void shouldReturn400WhenUserNotFound() throws Exception {
            when(walletService.getByUserId("unknown"))
                    .thenThrow(new IllegalArgumentException("Wallet not found for user: unknown"));

            mockMvc.perform(get("/wallets/user/{userId}", "unknown"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        }
    }

    @Nested
    @DisplayName("POST /wallets/{id}/deposit")
    class DepositTests {

        @Test
        @DisplayName("should deposit funds and return updated wallet")
        void shouldDeposit() throws Exception {
            UUID walletId = UUID.randomUUID();
            WalletResponse response = createWalletResponse(walletId, "alice", 15000L);

            when(walletService.deposit(walletId, 15000L)).thenReturn(response);

            mockMvc.perform(post("/wallets/{id}/deposit", walletId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount_paise\": 15000}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(15000));
        }

        @Test
        @DisplayName("should return 400 when amount_paise is missing")
        void shouldReturn400WhenAmountMissing() throws Exception {
            UUID walletId = UUID.randomUUID();

            mockMvc.perform(post("/wallets/{id}/deposit", walletId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        }
    }
}
