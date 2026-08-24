package com.p2pwallet.service;

import com.p2pwallet.dto.TransferResponse;
import com.p2pwallet.entity.Transfer;
import com.p2pwallet.exception.IdempotencyConflictException;
import com.p2pwallet.exception.InsufficientFundsException;
import com.p2pwallet.repository.TransferRepository;
import com.p2pwallet.repository.WalletRepository;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private PlatformTransactionManager txManager;

    @Mock
    private Counter transfersCompletedCounter;

    @Mock
    private Counter transfersDeclinedCounter;

    @Mock
    private Counter insufficientFundsCounter;

    @Mock
    private Counter idempotentReplaysCounter;

    private TransferService transferService;

    private final UUID fromWalletId = UUID.randomUUID();
    private final UUID toWalletId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        transferService = new TransferService(
                transferRepository,
                walletRepository,
                txManager,
                transfersCompletedCounter,
                transfersDeclinedCounter,
                insufficientFundsCounter,
                idempotentReplaysCounter
        );
    }

    private Transfer createTestTransfer(String transactionId, UUID from, UUID to, Long amount) {
        Transfer transfer = new Transfer(transactionId, from, to, amount);
        transfer.setId(UUID.randomUUID());
        transfer.setCreatedAt(Instant.now());
        return transfer;
    }

    @Nested
    @DisplayName("executeTransfer")
    class ExecuteTransferTests {

        @Test
        @DisplayName("should complete a valid transfer")
        void shouldCompleteTransfer() {
            String txnId = "txn_001";
            Long amount = 5000L;
            Transfer transfer = createTestTransfer(txnId, fromWalletId, toWalletId, amount);

            when(transferRepository.saveAndFlush(any(Transfer.class))).thenReturn(transfer);
            when(walletRepository.findByIdsForUpdate(any())).thenReturn(List.of());
            when(walletRepository.conditionalDebit(fromWalletId, amount)).thenReturn(1);
            when(walletRepository.credit(toWalletId, amount)).thenReturn(1);
            when(transferRepository.save(any(Transfer.class))).thenReturn(transfer);

            TransferResponse response = transferService.executeTransfer(fromWalletId, toWalletId, amount, txnId);

            assertNotNull(response);
            assertEquals(txnId, response.getTransactionId());
            assertEquals("COMPLETED", response.getStatus());
            verify(transfersCompletedCounter).increment();
            verify(walletRepository).conditionalDebit(fromWalletId, amount);
            verify(walletRepository).credit(toWalletId, amount);
        }

        @Test
        @DisplayName("should throw InsufficientFundsException when balance is too low")
        void shouldThrowInsufficientFunds() {
            String txnId = "txn_002";
            Long amount = 100000L;
            Transfer transfer = createTestTransfer(txnId, fromWalletId, toWalletId, amount);

            when(transferRepository.saveAndFlush(any(Transfer.class))).thenReturn(transfer);
            when(walletRepository.findByIdsForUpdate(any())).thenReturn(List.of());
            when(walletRepository.conditionalDebit(fromWalletId, amount)).thenReturn(0);
            when(transferRepository.save(any(Transfer.class))).thenReturn(transfer);

            assertThrows(InsufficientFundsException.class,
                    () -> transferService.executeTransfer(fromWalletId, toWalletId, amount, txnId));

            verify(transfersDeclinedCounter).increment();
            verify(insufficientFundsCounter).increment();
            verify(walletRepository, never()).credit(any(), anyLong());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for self-transfer")
        void shouldThrowForSelfTransfer() {
            UUID sameWallet = UUID.randomUUID();

            assertThrows(IllegalArgumentException.class,
                    () -> transferService.executeTransfer(sameWallet, sameWallet, 1000L, "txn_self"));

            verifyNoInteractions(transferRepository);
            verifyNoInteractions(walletRepository);
        }

        @Test
        @DisplayName("should lock wallets in sorted order for deadlock prevention")
        void shouldLockInSortedOrder() {
            String txnId = "txn_lock";
            Long amount = 100L;
            Transfer transfer = createTestTransfer(txnId, fromWalletId, toWalletId, amount);

            when(transferRepository.saveAndFlush(any(Transfer.class))).thenReturn(transfer);
            when(walletRepository.findByIdsForUpdate(any())).thenReturn(List.of());
            when(walletRepository.conditionalDebit(fromWalletId, amount)).thenReturn(1);
            when(walletRepository.credit(toWalletId, amount)).thenReturn(1);
            when(transferRepository.save(any(Transfer.class))).thenReturn(transfer);

            transferService.executeTransfer(fromWalletId, toWalletId, amount, txnId);

            // Verify wallets are locked with sorted IDs
            UUID smaller = fromWalletId.compareTo(toWalletId) < 0 ? fromWalletId : toWalletId;
            UUID larger = fromWalletId.compareTo(toWalletId) < 0 ? toWalletId : fromWalletId;
            verify(walletRepository).findByIdsForUpdate(List.of(smaller, larger));
        }
    }

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("should return transfer when found")
        void shouldReturnTransfer() {
            UUID transferId = UUID.randomUUID();
            Transfer transfer = createTestTransfer("txn_get", fromWalletId, toWalletId, 5000L);
            transfer.setId(transferId);

            when(transferRepository.findById(transferId)).thenReturn(Optional.of(transfer));

            TransferResponse response = transferService.getById(transferId);

            assertNotNull(response);
            assertEquals(transferId, response.getId());
            assertEquals("txn_get", response.getTransactionId());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when not found")
        void shouldThrowWhenNotFound() {
            UUID transferId = UUID.randomUUID();
            when(transferRepository.findById(transferId)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transferService.getById(transferId));
            assertTrue(ex.getMessage().contains(transferId.toString()));
        }
    }

    @Nested
    @DisplayName("getByTransactionId")
    class GetByTransactionIdTests {

        @Test
        @DisplayName("should return transfer when found by transaction ID")
        void shouldReturnTransfer() {
            String txnId = "txn_lookup";
            Transfer transfer = createTestTransfer(txnId, fromWalletId, toWalletId, 3000L);

            when(transferRepository.findByTransactionId(txnId)).thenReturn(Optional.of(transfer));

            TransferResponse response = transferService.getByTransactionId(txnId);

            assertNotNull(response);
            assertEquals(txnId, response.getTransactionId());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when transaction ID not found")
        void shouldThrowWhenNotFound() {
            String txnId = "txn_missing";
            when(transferRepository.findByTransactionId(txnId)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transferService.getByTransactionId(txnId));
            assertTrue(ex.getMessage().contains(txnId));
        }
    }
}
