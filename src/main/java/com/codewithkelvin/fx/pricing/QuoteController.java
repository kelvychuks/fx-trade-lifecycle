package com.codewithkelvin.fx.pricing;

import com.codewithkelvin.fx.marketdata.MarketDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Pre-trade pricing. Every number a dealer would want to see before committing:
 * the spot, the forward, the points between them, the value date, and the two
 * interest rates that produced it.
 */
@RestController
@RequestMapping("/api/pricing")
@RequiredArgsConstructor
@Tag(name = "Pricing", description = "Quotes and value dates")
public class QuoteController {

    private final PricingService pricingService;
    private final MarketDataService marketDataService;

    @GetMapping("/quote")
    @Operation(summary = "Price a pair for a tenor, showing the workings")
    public PricingService.Quote quote(
            @RequestParam String pair,
            @RequestParam(defaultValue = "SP") String tenor,
            @RequestParam(required = false) BigDecimal notional,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {

        var currencyPair = marketDataService.requirePair(pair);
        var date = tradeDate == null ? LocalDate.now() : tradeDate;

        return pricingService.quote(currencyPair, Tenor.parse(tenor), date, notional);
    }

    @GetMapping("/value-date")
    @Operation(summary = "Just the dates: what spot is, and where a tenor lands")
    public ValueDateResponse valueDate(
            @RequestParam String pair,
            @RequestParam(defaultValue = "SP") String tenor,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {

        var currencyPair = marketDataService.requirePair(pair);
        var date = tradeDate == null ? LocalDate.now() : tradeDate;
        var parsed = Tenor.parse(tenor);

        return new ValueDateResponse(
                currencyPair.getSymbol(),
                date,
                parsed,
                pricingService.spotDate(currencyPair, date),
                pricingService.valueDate(currencyPair, date, parsed));
    }

    @GetMapping("/tenors")
    @Operation(summary = "Supported tenors")
    public List<TenorDto> tenors() {
        return Arrays.stream(Tenor.values())
                .map(t -> new TenorDto(t.name(), t.label(), t.isMonthBased()))
                .toList();
    }

    public record ValueDateResponse(String pair, LocalDate tradeDate, Tenor tenor,
                                    LocalDate spotDate, LocalDate valueDate) {
    }

    public record TenorDto(String code, String label, boolean endOfMonthRuleApplies) {
    }
}
