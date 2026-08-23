package com.p2pwallet.controller;

import com.p2pwallet.dto.CreateTransferRequest;
import com.p2pwallet.dto.TransferResponse;
import com.p2pwallet.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * POST /transfers — move money from one wallet to another.
     * Idempotent: same idempotency_key returns the same result.
     * Same key with different body returns 409 Conflict.
     */
    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(@Valid @RequestBody CreateTransferRequest request) {
        TransferResponse response = transferService.executeTransfer(
                request.getFrom(),
                request.getTo(),
                request.getAmountPaise(),
                request.getTransactionId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /transfers/{id} — transfer status.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> getTransfer(@PathVariable UUID id) {
        TransferResponse response = transferService.getById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /transfers/transaction/{transactionId} — look up transfer by transaction ID.
     */
    @GetMapping("/transaction/{transactionId}")
    public ResponseEntity<TransferResponse> getTransferByTransactionId(@PathVariable String transactionId) {
        TransferResponse response = transferService.getByTransactionId(transactionId);
        return ResponseEntity.ok(response);
    }
}
