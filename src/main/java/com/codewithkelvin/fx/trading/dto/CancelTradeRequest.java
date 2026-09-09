package com.codewithkelvin.fx.trading.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelTradeRequest(
        @NotBlank(message = "a cancellation reason is required")
        @Size(max = 200)
        String reason
) {
}
