package com.p2pwallet.service;

import com.p2pwallet.dto.WalletResponse;
import com.p2pwallet.entity.Wallet;
import com.p2pwallet.repository.WalletRepository;
import io.micrometer.core.instrument.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository walletRepository;
    private final Counter walletsCreatedCounter;

    public WalletService(WalletRepository walletRepository,
                         @Qualifier("walletsCreatedCounter") Counter walletsCreatedCounter) {
        this.walletRepository = walletRepository;
        this.walletsCreatedCounter = walletsCreatedCounter;
    }

    /**
     * Race-free get-or-create.
     *
     * Uses INSERT ... ON CONFLICT (user_id) DO NOTHING at the Postgres level.
     * Two concurrent calls for the same user_id:
     *   - One wins the insert, the other gets 0 rows affected
     *   - Both proceed to SELECT and return the same wallet
     *
     * No application-level locking is needed — Postgres UNIQUE constraint
     * serializes the race.
     */
    @Transactional
    public WalletResponse getOrCreate(String userId) {
        int inserted = walletRepository.insertOnConflictDoNothing(userId);

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Wallet should exist after upsert for user: " + userId));

        if (inserted > 0) {
            walletsCreatedCounter.increment();
            log.info("event=wallet.created wallet_id={} user_id={}", wallet.getId(), userId);
        } else {
            log.info("event=wallet.fetched wallet_id={} user_id={} (already existed)", wallet.getId(), userId);
        }

        return toResponse(wallet);
    }

    /**
     * Get wallet by ID.
     */
    @Transactional(readOnly = true)
    public WalletResponse getById(UUID walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));

        log.info("event=wallet.balance_checked wallet_id={} balance={}", wallet.getId(), wallet.getBalance());
        return toResponse(wallet);
    }

    /**
     * Get wallet by user ID.
     */
    @Transactional(readOnly = true)
    public WalletResponse getByUserId(String userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for user: " + userId));

        log.info("event=wallet.fetched_by_user user_id={} wallet_id={}", userId, wallet.getId());
        return toResponse(wallet);
    }

    /**
     * Deposit funds into a wallet (for testing / seeding).
     */
    @Transactional
    public WalletResponse deposit(UUID walletId, Long amountPaise) {
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));
        log.info("event=wallet.balance.before.deposit wallet_id={} amount_paise={} current_balance={}",
                walletId, amountPaise, wallet.getBalance());

        walletRepository.credit(walletId, amountPaise);

        // Re-fetch to get updated balance
        wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalStateException("Wallet disappeared after credit"));

        log.info("event=wallet.deposited wallet_id={} amount_paise={} new_balance={}",
                walletId, amountPaise, wallet.getBalance());
        return toResponse(wallet);
    }

    private WalletResponse toResponse(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getUserId(),
                wallet.getBalance(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt()
        );
    }
}
