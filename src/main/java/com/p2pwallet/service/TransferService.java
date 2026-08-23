package com.p2pwallet.service;

import com.p2pwallet.dto.TransferResponse;
import com.p2pwallet.entity.Transfer;
import com.p2pwallet.exception.IdempotencyConflictException;
import com.p2pwallet.exception.InsufficientFundsException;
import com.p2pwallet.repository.TransferRepository;
import com.p2pwallet.repository.WalletRepository;
import io.micrometer.core.instrument.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final TransferRepository transferRepository;
    private final WalletRepository walletRepository;
    private final Counter transfersCompletedCounter;
    private final Counter transfersDeclinedCounter;
    private final Counter insufficientFundsCounter;
    private final Counter idempotentReplaysCounter;
    private final TransactionTemplate requiresNewTx;

    public TransferService(TransferRepository transferRepository,
                           WalletRepository walletRepository,
                           PlatformTransactionManager txManager,
                           @Qualifier("transfersCompletedCounter") Counter transfersCompletedCounter,
                           @Qualifier("transfersDeclinedCounter") Counter transfersDeclinedCounter,
                           @Qualifier("transfersDeclinedInsufficientFundsCounter") Counter insufficientFundsCounter,
                           @Qualifier("idempotentReplaysCounter") Counter idempotentReplaysCounter) {
        this.transferRepository = transferRepository;
        this.walletRepository = walletRepository;
        this.transfersCompletedCounter = transfersCompletedCounter;
        this.transfersDeclinedCounter = transfersDeclinedCounter;
        this.insufficientFundsCounter = insufficientFundsCounter;
        this.idempotentReplaysCounter = idempotentReplaysCounter;

        this.requiresNewTx = new TransactionTemplate(txManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx.setReadOnly(true);
    }

    /**
     * Execute a peer-to-peer transfer with full invariant enforcement.
     *
     * MECHANISM: Within a single Postgres transaction:
     *
     * 1. TRY TO INSERT the transfer record (transaction_id is UNIQUE).
     *    - If insert fails (duplicate key) → check if body matches:
     *      - Same body → idempotent replay, return original result
     *      - Different body → 409 Conflict
     *
     * 2. LOCK both wallets via SELECT ... FOR UPDATE ORDER BY id.
     *    - Sorted lock order prevents deadlocks when two concurrent transfers
     *      lock the same pair of wallets in opposite directions (A→B vs B→A).
     *
     * 3. CONDITIONAL DEBIT: UPDATE wallets SET balance = balance - amount
     *    WHERE id = :from AND balance >= :amount
     *    - If 0 rows updated → insufficient funds → mark DECLINED, commit, return.
     *    - The CHECK(balance >= 0) on the column is a safety net.
     *
     * 4. CREDIT: UPDATE wallets SET balance = balance + amount WHERE id = :to
     *
     * 5. Mark transfer COMPLETED.
     *
     * All 5 steps are in a single BEGIN/COMMIT — conservation is guaranteed
     * because debit and credit are atomic.
     */
    @Transactional
    public TransferResponse executeTransfer(UUID fromWalletId, UUID toWalletId,
                                            Long amountPaise, String transactionId) {
        // Validate: can't transfer to self
        if (fromWalletId.equals(toWalletId)) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet");
        }

        // Step 1: Idempotency — try to insert the transfer record
        Transfer transfer;
        try {
            transfer = new Transfer(transactionId, fromWalletId, toWalletId, amountPaise);
            transfer = transferRepository.saveAndFlush(transfer);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE constraint violation on transaction_id — key already exists
            log.error("Constraint violation. Transaction_id - {} already exists", transactionId, e);
            return handleIdempotentReplay(transactionId, fromWalletId, toWalletId, amountPaise);
        }

        // Step 2: Lock both wallets in sorted order (deadlock prevention)
        List<UUID> sortedIds = Stream.of(fromWalletId, toWalletId)
                .sorted()
                .toList();
        walletRepository.findByIdsForUpdate(sortedIds);

        // Step 3: Conditional debit (no-overdraft)
        int debited = walletRepository.conditionalDebit(fromWalletId, amountPaise);
        if (debited == 0) {
            // Insufficient funds — mark transfer as DECLINED
            transfer.setStatus("DECLINED");
            transfer.setDeclineReason("Insufficient funds");
            transferRepository.save(transfer);

            transfersDeclinedCounter.increment();
            insufficientFundsCounter.increment();
            log.info("event=transfer.declined transfer_id={} from={} to={} amount_paise={} reason=insufficient_funds",
                    transfer.getId(), fromWalletId, toWalletId, amountPaise);

            throw new InsufficientFundsException(
                    "Insufficient funds in wallet " + fromWalletId + " for transfer of " + amountPaise + " paise");
        }

        // Step 4: Credit
        walletRepository.credit(toWalletId, amountPaise);

        // Step 5: Mark completed (already default, but explicit for clarity)
        transfer.setStatus("COMPLETED");
        transferRepository.save(transfer);

        transfersCompletedCounter.increment();
        log.info("event=transfer.completed transfer_id={} from={} to={} amount_paise={}",
                transfer.getId(), fromWalletId, toWalletId, amountPaise);

        return toResponse(transfer);
    }

    /**
     * Handle an idempotent replay or conflict.
     *
     * Runs the lookup in a NEW transaction because the original transaction's
     * Hibernate session is poisoned after the DataIntegrityViolationException.
     */
    private TransferResponse handleIdempotentReplay(String transactionId,
                                                     UUID fromWalletId, UUID toWalletId, Long amountPaise) {
        Transfer existing = requiresNewTx.execute(status ->
                transferRepository.findByTransactionId(transactionId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Transfer with transaction_id should exist after constraint violation: " + transactionId))
        );

        // Check if the body matches
        if (!existing.bodyMatches(fromWalletId, toWalletId, amountPaise)) {
            log.info("event=transfer.conflict transaction_id={} " +
                            "existing_from={} existing_to={} existing_amount={} " +
                            "new_from={} new_to={} new_amount={}",
                    transactionId,
                    existing.getFromWalletId(), existing.getToWalletId(), existing.getAmountPaise(),
                    fromWalletId, toWalletId, amountPaise);
            throw new IdempotencyConflictException(
                    "Transaction ID '" + transactionId + "' already used with different parameters");
        }

        // Same body — idempotent replay
        idempotentReplaysCounter.increment();
        log.info("event=transfer.idempotent_replay transfer_id={} transaction_id={}",
                existing.getId(), transactionId);
        return toResponse(existing);
    }

    /**
     * Get transfer by ID.
     */
    @Transactional(readOnly = true)
    public TransferResponse getById(UUID transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferId));
        return toResponse(transfer);
    }

    /**
     * Get transfer by transaction ID (idempotency key).
     */
    @Transactional(readOnly = true)
    public TransferResponse getByTransactionId(String transactionId) {
        Transfer transfer = transferRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found for transaction_id: " + transactionId));
        return toResponse(transfer);
    }

    private TransferResponse toResponse(Transfer t) {
        return new TransferResponse(
                t.getId(),
                t.getTransactionId(),
                t.getFromWalletId(),
                t.getToWalletId(),
                t.getAmountPaise(),
                t.getStatus(),
                t.getDeclineReason(),
                t.getCreatedAt()
        );
    }
}
