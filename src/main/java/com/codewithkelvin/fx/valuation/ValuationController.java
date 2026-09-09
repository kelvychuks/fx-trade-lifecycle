package com.codewithkelvin.fx.valuation;

import com.codewithkelvin.fx.trading.TradeStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/valuations")
@RequiredArgsConstructor
@Tag(name = "Valuation", description = "End-of-day mark-to-market")
public class ValuationController {

    private final ValuationService valuationService;

    @PostMapping("/run")
    @PreAuthorize("hasRole('MIDDLE_OFFICE')")
    @Operation(summary = "Run the end-of-day valuation (MIDDLE_OFFICE). Re-running a day is safe.")
    public ValuationService.ValuationRun run(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate valuationDate) {
        return valuationService.runEndOfDay(valuationDate == null ? LocalDate.now() : valuationDate);
    }

    @GetMapping
    @Operation(summary = "Every mark for a day")
    public List<ValuationRow> valuations(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate valuationDate) {

        var date = valuationDate == null ? LocalDate.now() : valuationDate;
        return valuationService.valuationsOn(date).stream().map(ValuationRow::from).toList();
    }

    @GetMapping("/summary")
    @Operation(summary = "Unrealised P&L for a day, by currency and by book")
    public ValuationService.PnlSummary summary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate valuationDate) {
        return valuationService.summary(valuationDate == null ? LocalDate.now() : valuationDate);
    }

    public record ValuationRow(
            String tradeRef,
            String pair,
            String quoteCcy,
            TradeStatus status,
            String book,
            LocalDate valueDate,
            BigDecimal dealtRate,
            BigDecimal marketRate,
            BigDecimal mtm,
            BigDecimal discountFactor,
            BigDecimal presentValue
    ) {
        static ValuationRow from(TradeValuation valuation) {
            var trade = valuation.getTrade();
            return new ValuationRow(
                    trade.getTradeRef(),
                    trade.getPair().getSymbol(),
                    trade.getPair().getQuoteCcy(),
                    trade.getStatus(),
                    trade.getBook(),
                    trade.getValueDate(),
                    trade.getRate(),
                    valuation.getMarketRate(),
                    valuation.getMtmQuoteCcy(),
                    valuation.getDiscountFactor(),
                    valuation.getPvQuoteCcy());
        }
    }
}
