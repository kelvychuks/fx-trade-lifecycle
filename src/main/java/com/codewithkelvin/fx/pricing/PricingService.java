package com.codewithkelvin.fx.pricing;

import com.codewithkelvin.fx.marketdata.FxRate;
import com.codewithkelvin.fx.marketdata.MarketDataService;
import com.codewithkelvin.fx.reference.CurrencyRepository;
import com.codewithkelvin.fx.reference.CurrencyPair;
import com.codewithkelvin.fx.reference.Holiday;
import com.codewithkelvin.fx.reference.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns reference data plus a market snapshot into a price and a value date.
 * <p>
 * The two pieces of real domain logic ({@link ValueDateCalculator} and
 * {@link ForwardPricer}) are deliberately framework-free; this class is the thin
 * layer that feeds them from the database.
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private final HolidayRepository holidayRepository;
    private final CurrencyRepository currencyRepository;
    private final MarketDataService marketDataService;

    /**
     * The holiday calendar changes a few times a year and is read on every
     * booking, so it is held in memory and rebuilt on demand rather than
     * queried per trade.
     */
    private volatile ValueDateCalculator cachedCalculator;

    public ValueDateCalculator calculator() {
        var calculator = cachedCalculator;
        if (calculator == null) {
            synchronized (this) {
                if (cachedCalculator == null) {
                    cachedCalculator = new ValueDateCalculator(loadCalendar());
                }
                calculator = cachedCalculator;
            }
        }
        return calculator;
    }

    /** Call after loading or changing holidays. */
    public void reloadCalendar() {
        synchronized (this) {
            cachedCalculator = new ValueDateCalculator(loadCalendar());
        }
    }

    private BusinessDayCalendar loadCalendar() {
        Map<String, Set<LocalDate>> byCurrency = new HashMap<>();
        for (Holiday holiday : holidayRepository.findAll()) {
            byCurrency.computeIfAbsent(holiday.getCurrencyCode(), key -> new HashSet<>())
                    .add(holiday.getHolidayDate());
        }
        return new BusinessDayCalendar(byCurrency);
    }

    public LocalDate spotDate(CurrencyPair pair, LocalDate tradeDate) {
        return calculator().spotDate(tradeDate, pair.getBaseCcy(), pair.getQuoteCcy(),
                pair.getSpotLagDays());
    }

    public LocalDate valueDate(CurrencyPair pair, LocalDate tradeDate, Tenor tenor) {
        return calculator().valueDate(tradeDate, pair.getBaseCcy(), pair.getQuoteCcy(),
                pair.getSpotLagDays(), tenor);
    }

    public boolean isSettlementDay(CurrencyPair pair, LocalDate date) {
        return calculator().isSettlementDay(date, pair.getBaseCcy(), pair.getQuoteCcy());
    }

    /**
     * The all-in rate for a value date, given the market as of a date.
     * Spot trades price at spot; anything further out picks up forward points.
     */
    @Transactional(readOnly = true)
    public BigDecimal rateFor(CurrencyPair pair, LocalDate asOf, LocalDate valueDate) {
        var market = marketDataService.requireRate(pair, asOf);
        var spot = spotDate(pair, asOf);

        var days = ForwardPricer.daysBetween(spot, valueDate);
        var raw = ForwardPricer.forwardRate(
                market.getSpotMid(), market.getBaseRate(), market.getQuoteRate(), days);

        return ForwardPricer.roundRate(raw, pair.getRateScale());
    }

    @Transactional(readOnly = true)
    public Quote quote(CurrencyPair pair, Tenor tenor, LocalDate tradeDate, BigDecimal notional) {
        FxRate market = marketDataService.requireRate(pair, tradeDate);

        var spotDate = spotDate(pair, tradeDate);
        var valueDate = valueDate(pair, tradeDate, tenor);
        var days = ForwardPricer.daysBetween(spotDate, valueDate);

        var spotRate = ForwardPricer.roundRate(market.getSpotMid(), pair.getRateScale());
        var forwardRate = ForwardPricer.roundRate(
                ForwardPricer.forwardRate(market.getSpotMid(), market.getBaseRate(),
                        market.getQuoteRate(), days),
                pair.getRateScale());
        var points = ForwardPricer.forwardPoints(forwardRate, spotRate, pair.getPipFactor());

        var counterAmount = notional == null ? null
                : ForwardPricer.counterAmount(notional, forwardRate, quoteMinorUnits(pair));

        return new Quote(
                pair.getSymbol(), tenor, market.getRateDate(), spotDate, valueDate, days,
                spotRate, forwardRate, points,
                market.getBaseRate(), market.getQuoteRate(),
                notional, counterAmount);
    }

    @Transactional(readOnly = true)
    public int quoteMinorUnits(CurrencyPair pair) {
        return currencyRepository.findById(pair.getQuoteCcy())
                .map(currency -> currency.getMinorUnits().intValue())
                .orElse(2);
    }

    public String supportedTenors() {
        return java.util.Arrays.stream(Tenor.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    /** A price, and everything needed to justify it. */
    public record Quote(
            String pair,
            Tenor tenor,
            LocalDate marketDataDate,
            LocalDate spotDate,
            LocalDate valueDate,
            long daysFromSpot,
            BigDecimal spotRate,
            BigDecimal forwardRate,
            BigDecimal forwardPoints,
            BigDecimal baseInterestRate,
            BigDecimal quoteInterestRate,
            BigDecimal notional,
            BigDecimal counterAmount
    ) {
    }
}
