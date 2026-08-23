package com.p2pwallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateTransferRequest {

    @NotNull(message = "from wallet id is required")
    private UUID from;

    @NotNull(message = "to wallet id is required")
    private UUID to;

    @NotNull(message = "amount_paise is required")
    @Min(value = 1, message = "amount_paise must be at least 1")
    @JsonProperty("amount_paise")
    private Long amountPaise;

    @NotBlank(message = "transaction_id is required")
    @JsonProperty("transaction_id")
    private String transactionId;
}
