package com.p2pwallet.controller;

import com.p2pwallet.dto.CreateWalletRequest;
import com.p2pwallet.dto.WalletResponse;
import com.p2pwallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    /**
     * POST /wallets — get-or-create a wallet for a user.
     * Race-free: concurrent calls for the same user yield one wallet.
     */
    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(@Valid @RequestBody CreateWalletRequest request) {
        WalletResponse response = walletService.getOrCreate(request.getUserId());
        return ResponseEntity.ok(response);
    }

    /**
     * GET /wallets/{id} — current balance.
     */
    @GetMapping("/{id}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable UUID id) {
        WalletResponse response = walletService.getById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /wallets/user/{userId} — get wallet by user ID.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<WalletResponse> getWalletByUserId(@PathVariable String userId) {
        WalletResponse response = walletService.getByUserId(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /wallets/{id}/deposit — deposit funds (for testing / seeding).
     */
    @PostMapping("/{id}/deposit")
    public ResponseEntity<WalletResponse> deposit(@PathVariable UUID id,
                                                   @RequestBody Map<String, Long> body) {
        Long amount = body.get("amount_paise");
        if (amount == null) {
            throw new IllegalArgumentException("amount_paise is required");
        }
        WalletResponse response = walletService.deposit(id, amount);
        return ResponseEntity.ok(response);
    }
}
