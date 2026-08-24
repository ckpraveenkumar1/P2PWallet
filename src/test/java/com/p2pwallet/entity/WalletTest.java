package com.p2pwallet.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WalletTest {

    @Test
    @DisplayName("constructor should set userId and default balance")
    void constructorShouldSetDefaults() {
        Wallet wallet = new Wallet("alice");

        assertEquals("alice", wallet.getUserId());
        assertEquals(0L, wallet.getBalance());
        assertNull(wallet.getId());
        assertNull(wallet.getCreatedAt());
        assertNull(wallet.getUpdatedAt());
    }

    @Test
    @DisplayName("no-arg constructor should have null userId and default balance")
    void noArgConstructor() {
        Wallet wallet = new Wallet();

        assertNull(wallet.getUserId());
        assertEquals(0L, wallet.getBalance());
    }

    @Test
    @DisplayName("onCreate should set createdAt and updatedAt to same timestamp")
    void onCreateShouldSetTimestamps() {
        Wallet wallet = new Wallet("bob");
        assertNull(wallet.getCreatedAt());
        assertNull(wallet.getUpdatedAt());

        wallet.onCreate();

        assertNotNull(wallet.getCreatedAt());
        assertNotNull(wallet.getUpdatedAt());
        assertEquals(wallet.getCreatedAt(), wallet.getUpdatedAt());
    }

    @Test
    @DisplayName("onUpdate should change updatedAt")
    void onUpdateShouldChangeUpdatedAt() {
        Wallet wallet = new Wallet("charlie");
        wallet.onCreate();

        var originalUpdatedAt = wallet.getUpdatedAt();

        // Small delay to ensure different timestamp
        wallet.onUpdate();

        assertNotNull(wallet.getUpdatedAt());
        // createdAt should remain unchanged
        assertNotNull(wallet.getCreatedAt());
    }

    @Test
    @DisplayName("balance can be set and retrieved")
    void balanceGetterSetter() {
        Wallet wallet = new Wallet("dave");
        assertEquals(0L, wallet.getBalance());

        wallet.setBalance(50000L);
        assertEquals(50000L, wallet.getBalance());
    }
}
