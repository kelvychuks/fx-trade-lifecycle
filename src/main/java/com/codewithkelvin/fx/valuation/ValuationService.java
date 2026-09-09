package com.codewithkelvin.fx.valuation;

import com.codewithkelvin.fx.marketdata.MarketDataService;
import com.codewithkelvin.fx.pricing.ForwardPricer;
import com.codewithkelvin.fx.pricing.PricingService;
import com.codewithkelvin.fx.trading.Trade;
import com.codewithkelvin.fx.trading.TradeRepository;
import com.codewithkelvin.fx.trading.TradeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * End-of-day mark-to-market.
 * <p>
 * For each live trade, the question is: what would it cost to close this out
 * today? That is the difference between the rate the trade was dealt at and the
 * rate the market offers now <em>for the same value date</em> — not today's
 * spot. A three-month forward is compared with a three-month forward.
 *
 * <pre>
 *   MTM = direction x notional x (market rate - dealt rate)    [quote currency]
 *   PV  = MTM x discount factor to the value date
 * </pre>
 *
 * The discounting matters: an unrealised gain that only lands in six months is
 * not worth its face value today, and a P&amp;L report that says otherwise is
 * overstating the desk's position.
 *
 * <p><b>What this is not.</b> A production revaluation values against bid or
 * offer rather than mid depending on which way the position would be closed,
 * discounts on a proper curve rather than a single deposit rate, and converts
 * every currency into one reporting currency. Those are known, deliberate
 * omissions, not oversights.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValuationService {

    private final TradeRepository tradeRepository;
    private final TradeValuationRepository valuationRepository;
    private final PricingService pricingService;
    private final MarketDataService marketDataService;

    @Transactional
    public ValuationRun runEndOfDay(LocalDate valuationDate) {
        var trades = tradeRepository.findByStatusInOrderByValueDate(
                EnumSet.of(TradeStatus.CAPTURED, TradeStatus.VALIDATED, TradeStatus.CONFIRMED));

        var priced = 0;
        var skipped = new ArrayList<String>();

        for (var trade : trades) {
            try {
                valuationRepository.save(value(trade, valuationDate));
                priced++;
            } catch (RuntimeException ex) {
                // One pair with no market data must not abort the whole run.
                // The desk needs the marks it can produce, plus a list of what
                // it could not.
                log.warn("Could not value {}: {}", trade.getTradeRef(), ex.getMessage());
                skipped.add(trade.getTradeRef() + ": " + ex.getMessage());
            }
        }

        log.info("Valuation run for {} priced {} trades, skipped {}",
                valuationDate, priced, skipped.size());

        return new ValuationRun(valuationDate, trades.size(), priced, skipped);
    }

    TradeValuation value(Trade trade, LocalDate valuationDate) {
        var pair = trade.getPair();
        var market = marketDataService.requireRate(pair, valuationDate);

        // Past its value date and still unsettled: there are no forward points
        // left to earn, so it marks against spot with no discounting.
        var matured = trade.isMatured(valuationDate);

        var marketRate = matured
                ? ForwardPricer.roundRate(market.getSpotMid(), pair.getRateScale())
                : pricingService.rateFor(pair, valuationDate, trade.getValueDate());

        var daysToValue = matured ? 0
                : ForwardPricer.daysBetween(valuationDate, trade.getValueDate());

        var quoteMinorUnits = pricingService.quoteMinorUnits(pair);

        var mtm = marketRate.subtract(trade.getRate())
                .multiply(trade.getNotional())
                .multiply(BigDecimal.valueOf(trade.getDirection().sign()))
                .setScale(quoteMinorUnits, RoundingMode.HALF_UP);

        var discountFactor = ForwardPricer.discountFactor(market.getQuoteRate(), daysToValue);
        var presentValue = mtm.multiply(discountFactor).setScale(quoteMinorUnits, RoundingMode.HALF_UP);

        var valuation = valuationRepository
                .findByTradeIdAndValuationDate(trade.getId(), valuationDate)
                .orElseGet(() -> {
                    var created = new TradeValuation();
                    created.setTrade(trade);
                    created.setValuationDate(valuationDate);
                    return created;
                });

        valuation.setMarketRate(marketRate);
        valuation.setMtmQuoteCcy(mtm);
        valuation.setDiscountFactor(discountFactor);
        valuation.setPvQuoteCcy(presentValue);

        return valuation;
    }

    @Transactional(readOnly = true)
    public List<TradeValuation> valuationsOn(LocalDate date) {
        return valuationRepository.findByValuationDateOrderByTradeTradeRef(date);
    }

    /** Totals by quote currency: the desk's unrealised P&amp;L for the day. */
    @Transactional(readOnly = true)
    public PnlSummary summary(LocalDate date) {
        var valuations = valuationsOn(date);

        Map<String, BigDecimal> pvByCurrency = new LinkedHashMap<>();
        Map<String, BigDecimal> pvByBook = new LinkedHashMap<>();

        for (var valuation : valuations) {
            pvByCurrency.merge(valuation.getTrade().getPair().getQuoteCcy(),
                    valuation.getPvQuoteCcy(), BigDecimal::add);
            pvByBook.merge(valuation.getTrade().getBook(),
                    valuation.getPvQuoteCcy(), BigDecimal::add);
        }

        return new PnlSummary(date, valuations.size(), pvByCurrency, pvByBook);
    }

    public record ValuationRun(LocalDate valuationDate, int tradesConsidered,
                               int tradesPriced, List<String> skipped) {
    }

    /**
     * Deliberately not summed into one number: adding dollars to yen is how a
     * P&amp;L report starts lying. Converting to a reporting currency is a
     * separate decision that needs its own rates.
     */
    public record PnlSummary(LocalDate valuationDate, int valuations,
                             Map<String, BigDecimal> presentValueByCurrency,
                             Map<String, BigDecimal> presentValueByBook) {
    }
}
