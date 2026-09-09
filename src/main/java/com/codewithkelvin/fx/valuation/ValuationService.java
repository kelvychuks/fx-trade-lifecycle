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
 * End-of-day mark-to-market: what would it cost to close each live trade out
 * today? The comparison is against the market rate for the trade's own value
 * date, not today's spot, so a three-month forward is marked against a
 * three-month forward.
 *
 * <pre>
 *   MTM = direction x notional x (market rate - dealt rate)    [quote currency]
 *   PV  = MTM x discount factor to the value date
 * </pre>
 *
 * <p>Simplifications: mid rates rather than bid/offer, a single deposit rate
 * rather than a discount curve, and no conversion into a reporting currency.
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
                // One pair missing market data must not abort the whole run.
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

        // Past value date and unsettled: no forward points left, so mark against
        // spot with no discounting.
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

    /** Unrealised P&amp;L for the day, totalled by quote currency. */
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
     * Not summed into a single number. Converting to a reporting currency needs
     * its own rates and is a separate decision.
     */
    public record PnlSummary(LocalDate valuationDate, int valuations,
                             Map<String, BigDecimal> presentValueByCurrency,
                             Map<String, BigDecimal> presentValueByBook) {
    }
}
