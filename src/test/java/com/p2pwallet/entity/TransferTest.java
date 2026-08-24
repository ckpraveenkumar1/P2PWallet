package com.p2pwallet.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransferTest {

    @Test
    @DisplayName("constructor should set all fields correctly")
    void constructorShouldSetFields() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        String txnId = "txn_001";
        Long amount = 5000L;

        Transfer transfer = new Transfer(txnId, from, to, amount);

        assertEquals(txnId, transfer.getTransactionId());
        assertEquals(from, transfer.getFromWalletId());
        assertEquals(to, transfer.getToWalletId());
        assertEquals(amount, transfer.getAmountPaise());
        assertEquals("COMPLETED", transfer.getStatus());
        assertNull(transfer.getDeclineReason());
    }

    @Test
    @DisplayName("bodyMatches should return true when all fields match")
    void bodyMatchesShouldReturnTrue() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        Long amount = 3000L;

        Transfer transfer = new Transfer("txn_002", from, to, amount);

        assertTrue(transfer.bodyMatches(from, to, amount));
    }

    @Test
    @DisplayName("bodyMatches should return false when from wallet differs")
    void bodyMatchesShouldReturnFalseForDifferentFrom() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        Long amount = 3000L;

        Transfer transfer = new Transfer("txn_003", from, to, amount);

        assertFalse(transfer.bodyMatches(UUID.randomUUID(), to, amount));
    }

    @Test
    @DisplayName("bodyMatches should return false when to wallet differs")
    void bodyMatchesShouldReturnFalseForDifferentTo() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        Long amount = 3000L;

        Transfer transfer = new Transfer("txn_004", from, to, amount);

        assertFalse(transfer.bodyMatches(from, UUID.randomUUID(), amount));
    }

    @Test
    @DisplayName("bodyMatches should return false when amount differs")
    void bodyMatchesShouldReturnFalseForDifferentAmount() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();

        Transfer transfer = new Transfer("txn_005", from, to, 3000L);

        assertFalse(transfer.bodyMatches(from, to, 5000L));
    }

    @Test
    @DisplayName("onCreate should set createdAt timestamp")
    void onCreateShouldSetTimestamp() {
        Transfer transfer = new Transfer("txn_006", UUID.randomUUID(), UUID.randomUUID(), 1000L);
        assertNull(transfer.getCreatedAt());

        transfer.onCreate();

        assertNotNull(transfer.getCreatedAt());
    }

    @Test
    @DisplayName("status can be changed to DECLINED")
    void statusCanBeDeclined() {
        Transfer transfer = new Transfer("txn_007", UUID.randomUUID(), UUID.randomUUID(), 1000L);
        assertEquals("COMPLETED", transfer.getStatus());

        transfer.setStatus("DECLINED");
        transfer.setDeclineReason("Insufficient funds");

        assertEquals("DECLINED", transfer.getStatus());
        assertEquals("Insufficient funds", transfer.getDeclineReason());
    }
}
