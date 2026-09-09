package com.codewithkelvin.fx.trading.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Every field is optional; whatever is supplied replaces what is there. */
@Schema(description = "Changes to an unconfirmed trade")
public record AmendTradeRequest(

        @DecimalMin(value = "0.01", message = "notional must be greater than zero")
        BigDecimal notional,

        String tenor,

        LocalDate valueDate,

        BigDecimal rate,

        @Size(max = 200)
        String reason
) {
}
