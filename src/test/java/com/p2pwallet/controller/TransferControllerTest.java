package com.p2pwallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2pwallet.dto.TransferResponse;
import com.p2pwallet.exception.GlobalExceptionHandler;
import com.p2pwallet.exception.IdempotencyConflictException;
import com.p2pwallet.exception.InsufficientFundsException;
import com.p2pwallet.service.TransferService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TransferControllerTest {

    @Mock
    private TransferService transferService;

    @InjectMocks
    private TransferController transferController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID fromWalletId = UUID.randomUUID();
    private final UUID toWalletId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(transferController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    private TransferResponse createTransferResponse(String txnId, String status) {
        return new TransferResponse(
                UUID.randomUUID(), txnId, fromWalletId, toWalletId,
                5000L, status, null, Instant.now()
        );
    }

    @Nested
    @DisplayName("POST /transfers")
    class CreateTransferTests {

        @Test
        @DisplayName("should create transfer and return 201")
        void shouldCreateTransfer() throws Exception {
            TransferResponse response = createTransferResponse("txn_001", "COMPLETED");

            when(transferService.executeTransfer(fromWalletId, toWalletId, 5000L, "txn_001"))
                    .thenReturn(response);

            String requestBody = String.format(
                    "{\"from\": \"%s\", \"to\": \"%s\", \"amount_paise\": 5000, \"transaction_id\": \"txn_001\"}",
                    fromWalletId, toWalletId);

            mockMvc.perform(post("/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.transaction_id").value("txn_001"))
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("should return 422 when insufficient funds")
        void shouldReturn422WhenInsufficientFunds() throws Exception {
            when(transferService.executeTransfer(fromWalletId, toWalletId, 5000L, "txn_002"))
                    .thenThrow(new InsufficientFundsException("Insufficient funds"));

            String requestBody = String.format(
                    "{\"from\": \"%s\", \"to\": \"%s\", \"amount_paise\": 5000, \"transaction_id\": \"txn_002\"}",
                    fromWalletId, toWalletId);

            mockMvc.perform(post("/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"));
        }

        @Test
        @DisplayName("should return 409 on idempotency conflict")
        void shouldReturn409OnConflict() throws Exception {
            when(transferService.executeTransfer(fromWalletId, toWalletId, 5000L, "txn_003"))
                    .thenThrow(new IdempotencyConflictException("Already used with different params"));

            String requestBody = String.format(
                    "{\"from\": \"%s\", \"to\": \"%s\", \"amount_paise\": 5000, \"transaction_id\": \"txn_003\"}",
                    fromWalletId, toWalletId);

            mockMvc.perform(post("/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("IDEMPOTENCY_CONFLICT"));
        }

        @Test
        @DisplayName("should return 400 when from wallet ID is missing")
        void shouldReturn400WhenFromMissing() throws Exception {
            String requestBody = String.format(
                    "{\"to\": \"%s\", \"amount_paise\": 5000, \"transaction_id\": \"txn_004\"}",
                    toWalletId);

            mockMvc.perform(post("/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when transaction_id is blank")
        void shouldReturn400WhenTransactionIdBlank() throws Exception {
            String requestBody = String.format(
                    "{\"from\": \"%s\", \"to\": \"%s\", \"amount_paise\": 5000, \"transaction_id\": \"\"}",
                    fromWalletId, toWalletId);

            mockMvc.perform(post("/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when amount_paise is zero")
        void shouldReturn400WhenAmountZero() throws Exception {
            String requestBody = String.format(
                    "{\"from\": \"%s\", \"to\": \"%s\", \"amount_paise\": 0, \"transaction_id\": \"txn_005\"}",
                    fromWalletId, toWalletId);

            mockMvc.perform(post("/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /transfers/{id}")
    class GetTransferTests {

        @Test
        @DisplayName("should return transfer by ID")
        void shouldReturnTransfer() throws Exception {
            UUID transferId = UUID.randomUUID();
            TransferResponse response = createTransferResponse("txn_get", "COMPLETED");

            when(transferService.getById(transferId)).thenReturn(response);

            mockMvc.perform(get("/transfers/{id}", transferId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transaction_id").value("txn_get"));
        }

        @Test
        @DisplayName("should return 400 when transfer not found")
        void shouldReturn400WhenNotFound() throws Exception {
            UUID transferId = UUID.randomUUID();
            when(transferService.getById(transferId))
                    .thenThrow(new IllegalArgumentException("Transfer not found: " + transferId));

            mockMvc.perform(get("/transfers/{id}", transferId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        }
    }

    @Nested
    @DisplayName("GET /transfers/transaction/{transactionId}")
    class GetByTransactionIdTests {

        @Test
        @DisplayName("should return transfer by transaction ID")
        void shouldReturnTransfer() throws Exception {
            TransferResponse response = createTransferResponse("txn_find", "COMPLETED");

            when(transferService.getByTransactionId("txn_find")).thenReturn(response);

            mockMvc.perform(get("/transfers/transaction/{transactionId}", "txn_find"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transaction_id").value("txn_find"));
        }

        @Test
        @DisplayName("should return 400 when transaction ID not found")
        void shouldReturn400WhenNotFound() throws Exception {
            when(transferService.getByTransactionId("txn_missing"))
                    .thenThrow(new IllegalArgumentException("Transfer not found for transaction_id: txn_missing"));

            mockMvc.perform(get("/transfers/transaction/{transactionId}", "txn_missing"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        }
    }
}
