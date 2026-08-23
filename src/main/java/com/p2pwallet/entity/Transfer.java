package com.p2pwallet.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers", indexes = {
        @Index(name = "idx_transfers_transaction_id", columnList = "transaction_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID DEFAULT gen_random_uuid()")
    private UUID id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private String transactionId;

    @Column(name = "from_wallet_id", nullable = false)
    private UUID fromWalletId;

    @Column(name = "to_wallet_id", nullable = false)
    private UUID toWalletId;

    @Column(name = "amount_paise", nullable = false)
    private Long amountPaise;

    @Column(nullable = false, length = 20)
    private String status = "COMPLETED";

    @Column(name = "decline_reason")
    private String declineReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Transfer(String transactionId, UUID fromWalletId, UUID toWalletId, Long amountPaise) {
        this.transactionId = transactionId;
        this.fromWalletId = fromWalletId;
        this.toWalletId = toWalletId;
        this.amountPaise = amountPaise;
        this.status = "COMPLETED";
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    /**
     * Checks whether the body of this transfer matches the given parameters.
     * Used for idempotency conflict detection.
     */
    public boolean bodyMatches(UUID fromWalletId, UUID toWalletId, Long amountPaise) {
        return this.fromWalletId.equals(fromWalletId)
                && this.toWalletId.equals(toWalletId)
                && this.amountPaise.equals(amountPaise);
    }
}
