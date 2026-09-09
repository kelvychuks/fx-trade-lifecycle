package com.codewithkelvin.fx.marketdata;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/market-data")
@RequiredArgsConstructor
@Tag(name = "Market data", description = "Spot rates and deposit rates")
public class MarketDataController {

    private final MarketDataService marketDataService;

    @GetMapping("/rates")
    @Operation(summary = "Every pair's snapshot for a date")
    public List<FxRateDto> rates(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        var on = date == null ? marketDataService.latestRateDate() : date;
        return marketDataService.ratesOn(on).stream().map(FxRateDto::from).toList();
    }

    @PostMapping("/rates")
    @PreAuthorize("hasRole('MIDDLE_OFFICE')")
    @Operation(summary = "Load or replace one day's snapshot (MIDDLE_OFFICE)")
    public FxRateDto upsert(@Valid @RequestBody UpsertRateRequest request) {
        return FxRateDto.from(marketDataService.upsert(
                request.pair(), request.rateDate(), request.spotMid(),
                request.baseRate(), request.quoteRate(), request.source()));
    }

    public record UpsertRateRequest(
            @NotBlank String pair,
            @NotNull LocalDate rateDate,
            @NotNull @DecimalMin(value = "0.00000001") BigDecimal spotMid,
            @NotNull BigDecimal baseRate,
            @NotNull BigDecimal quoteRate,
            String source) {
    }

    public record FxRateDto(String pair, LocalDate rateDate, BigDecimal spotMid,
                            BigDecimal baseRate, BigDecimal quoteRate, String source) {
        static FxRateDto from(FxRate rate) {
            return new FxRateDto(rate.getPair().getSymbol(), rate.getRateDate(), rate.getSpotMid(),
                    rate.getBaseRate(), rate.getQuoteRate(), rate.getSource());
        }
    }
}
