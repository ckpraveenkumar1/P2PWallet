package com.p2pwallet.repository;

import com.p2pwallet.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserId(String userId);

    /**
     * Upsert: insert a wallet if user_id doesn't exist, otherwise do nothing.
     * Returns the number of inserted rows (0 or 1).
     * The caller must then SELECT to get the wallet.
     */
    @Modifying
    @Query(value = "INSERT INTO wallets (id, user_id, balance, created_at, updated_at) " +
            "VALUES (gen_random_uuid(), :userId, 0, now(), now()) " +
            "ON CONFLICT (user_id) DO NOTHING",
            nativeQuery = true)
    int insertOnConflictDoNothing(@Param("userId") String userId);

    /**
     * Lock wallets by IDs in sorted order for transfer processing.
     * The ORDER BY id ensures consistent lock ordering to prevent deadlocks.
     */
    @Query(value = "SELECT * FROM wallets WHERE id IN :ids ORDER BY id FOR UPDATE",
            nativeQuery = true)
    List<Wallet> findByIdsForUpdate(@Param("ids") List<UUID> ids);

    /**
     * Conditional debit: only succeeds if balance >= amount.
     * Returns number of rows updated (0 = insufficient funds, 1 = success).
     */
    @Modifying
    @Query(value = "UPDATE wallets SET balance = balance - :amount, updated_at = now() " +
            "WHERE id = :id AND balance >= :amount",
            nativeQuery = true)
    int conditionalDebit(@Param("id") UUID id, @Param("amount") Long amount);

    /**
     * Credit a wallet. Always succeeds (no precondition).
     */
    @Modifying
    @Query(value = "UPDATE wallets SET balance = balance + :amount, updated_at = now() " +
            "WHERE id = :id",
            nativeQuery = true)
    int credit(@Param("id") UUID id, @Param("amount") Long amount);

    /**
     * Sum of all wallet balances — for conservation verification.
     */
    @Query(value = "SELECT COALESCE(SUM(balance), 0) FROM wallets", nativeQuery = true)
    Long sumAllBalances();
}
