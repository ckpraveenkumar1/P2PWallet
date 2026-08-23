package com.p2pwallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {

    private UUID id;

    @JsonProperty("transaction_id")
    private String transactionId;

    private UUID from;
    private UUID to;

    @JsonProperty("amount_paise")
    private long amountPaise;

    private String status;

    @JsonProperty("decline_reason")
    private String declineReason;

    @JsonProperty("created_at")
    private Instant createdAt;
}
