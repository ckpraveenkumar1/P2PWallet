package com.p2pwallet.service;

import com.p2pwallet.dto.WalletResponse;
import com.p2pwallet.entity.Wallet;
import com.p2pwallet.repository.WalletRepository;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private Counter walletsCreatedCounter;

    private WalletService walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(walletRepository, walletsCreatedCounter);
    }

    private Wallet createTestWallet(String userId) {
        Wallet wallet = new Wallet(userId);
        wallet.setId(UUID.randomUUID());
        wallet.setBalance(0L);
        wallet.setCreatedAt(Instant.now());
        wallet.setUpdatedAt(Instant.now());
        return wallet;
    }

    @Nested
    @DisplayName("getOrCreate")
    class GetOrCreateTests {

        @Test
        @DisplayName("should create a new wallet when user does not exist")
        void shouldCreateNewWallet() {
            String userId = "alice";
            Wallet wallet = createTestWallet(userId);

            when(walletRepository.insertOnConflictDoNothing(userId)).thenReturn(1);
            when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

            WalletResponse response = walletService.getOrCreate(userId);

            assertNotNull(response);
            assertEquals(userId, response.getUserId());
            assertEquals(0L, response.getBalance());
            verify(walletsCreatedCounter, times(1)).increment();
            verify(walletRepository).insertOnConflictDoNothing(userId);
            verify(walletRepository).findByUserId(userId);
        }

        @Test
        @DisplayName("should return existing wallet when user already exists")
        void shouldReturnExistingWallet() {
            String userId = "alice";
            Wallet wallet = createTestWallet(userId);
            wallet.setBalance(5000L);

            when(walletRepository.insertOnConflictDoNothing(userId)).thenReturn(0);
            when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

            WalletResponse response = walletService.getOrCreate(userId);

            assertNotNull(response);
            assertEquals(userId, response.getUserId());
            assertEquals(5000L, response.getBalance());
            verify(walletsCreatedCounter, never()).increment();
        }

        @Test
        @DisplayName("should throw IllegalStateException if wallet not found after upsert")
        void shouldThrowIfWalletMissingAfterUpsert() {
            String userId = "alice";

            when(walletRepository.insertOnConflictDoNothing(userId)).thenReturn(1);
            when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

            assertThrows(IllegalStateException.class, () -> walletService.getOrCreate(userId));
        }
    }

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("should return wallet when found")
        void shouldReturnWallet() {
            UUID walletId = UUID.randomUUID();
            Wallet wallet = createTestWallet("alice");
            wallet.setId(walletId);
            wallet.setBalance(10000L);

            when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));

            WalletResponse response = walletService.getById(walletId);

            assertNotNull(response);
            assertEquals(walletId, response.getId());
            assertEquals(10000L, response.getBalance());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when wallet not found")
        void shouldThrowWhenNotFound() {
            UUID walletId = UUID.randomUUID();
            when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> walletService.getById(walletId));
            assertTrue(ex.getMessage().contains(walletId.toString()));
        }
    }

    @Nested
    @DisplayName("getByUserId")
    class GetByUserIdTests {

        @Test
        @DisplayName("should return wallet when user found")
        void shouldReturnWallet() {
            String userId = "bob";
            Wallet wallet = createTestWallet(userId);
            wallet.setBalance(3000L);

            when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

            WalletResponse response = walletService.getByUserId(userId);

            assertNotNull(response);
            assertEquals(userId, response.getUserId());
            assertEquals(3000L, response.getBalance());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when user not found")
        void shouldThrowWhenUserNotFound() {
            String userId = "unknown";
            when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> walletService.getByUserId(userId));
            assertTrue(ex.getMessage().contains(userId));
        }
    }

    @Nested
    @DisplayName("deposit")
    class DepositTests {

        @Test
        @DisplayName("should deposit funds and return updated wallet")
        void shouldDepositFunds() {
            UUID walletId = UUID.randomUUID();
            Wallet walletBefore = createTestWallet("alice");
            walletBefore.setId(walletId);
            walletBefore.setBalance(5000L);

            Wallet walletAfter = createTestWallet("alice");
            walletAfter.setId(walletId);
            walletAfter.setBalance(15000L);

            when(walletRepository.findById(walletId))
                    .thenReturn(Optional.of(walletBefore))
                    .thenReturn(Optional.of(walletAfter));
            when(walletRepository.credit(walletId, 10000L)).thenReturn(1);

            WalletResponse response = walletService.deposit(walletId, 10000L);

            assertNotNull(response);
            assertEquals(15000L, response.getBalance());
            verify(walletRepository).credit(walletId, 10000L);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for zero amount")
        void shouldThrowForZeroAmount() {
            UUID walletId = UUID.randomUUID();

            assertThrows(IllegalArgumentException.class,
                    () -> walletService.deposit(walletId, 0L));
            verify(walletRepository, never()).credit(any(), anyLong());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for negative amount")
        void shouldThrowForNegativeAmount() {
            UUID walletId = UUID.randomUUID();

            assertThrows(IllegalArgumentException.class,
                    () -> walletService.deposit(walletId, -100L));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when wallet not found")
        void shouldThrowWhenWalletNotFound() {
            UUID walletId = UUID.randomUUID();
            when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> walletService.deposit(walletId, 1000L));
        }
    }
}
