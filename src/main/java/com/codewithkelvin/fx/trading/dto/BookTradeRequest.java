package com.codewithkelvin.fx.trading.dto;

import com.codewithkelvin.fx.trading.Direction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A booking ticket.
 * <p>
 * Only four things are required: what, which way, how much, and with whom.
 * Everything else has a market convention behind it — leave the tenor out and
 * you get spot, leave the rate out and you get the market rate.
 */
@Schema(description = "Booking ticket for an FX spot or forward trade")
public record BookTradeRequest(

        @Schema(example = "EURUSD")
        @NotBlank(message = "pair is required")
        String pair,

        @NotNull(message = "direction is required")
        Direction direction,

        @Schema(description = "Amount of the base currency", example = "1000000.00")
        @NotNull(message = "notional is required")
        @DecimalMin(value = "0.01", message = "notional must be greater than zero")
        BigDecimal notional,

        @Schema(example = "MERIDIAN")
        @NotBlank(message = "counterparty is required")
        String counterparty,

        @Schema(description = "SP, W1, W2, W3, M1, M2, M3, M6, M9 or Y1. Defaults to spot.",
                example = "M3")
        String tenor,

        @Schema(description = "Broken date. Overrides the tenor when supplied.")
        LocalDate valueDate,

        @Schema(description = "Dealt rate. Defaults to the market rate; a rate too far "
                + "from the market is rejected.")
        BigDecimal rate,

        @Schema(description = "Defaults to today")
        LocalDate tradeDate,

        @Size(max = 30)
        String book,

        @Schema(description = "Idempotency key. Sending the same one twice returns the "
                + "original trade instead of booking a second.")
        @Size(max = 60)
        String externalRef
) {
}
