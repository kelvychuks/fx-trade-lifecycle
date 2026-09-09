package com.codewithkelvin.fx.positions;

import com.codewithkelvin.fx.trading.Trade;
import com.codewithkelvin.fx.trading.TradeRepository;
import com.codewithkelvin.fx.trading.TradeStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Position keeping. Every FX trade moves two currencies in opposite directions,
 * so a position is built by walking both legs of every trade: buying EURUSD 1m
 * at 1.09 is long 1,000,000 EUR and short 1,090,000 USD. What matters is the
 * net per currency across all pairs, since a long EURUSD and a long USDJPY
 * partly offset in dollars.
 *
 * <p>Cancelled trades are excluded. Settled ones are excluded by default: the
 * cash has moved, so they are history rather than exposure.
 *
 * <p>Aggregation is a pure function over a list of trades so it can be tested
 * without a database. At millions of trades this would belong in SQL or a
 * dedicated position store.
 */
@Service
@RequiredArgsConstructor
public class PositionService {

    private final TradeRepository tradeRepository;

    @Transactional(readOnly = true)
    public List<CurrencyPosition> currencyPositions(boolean includeSettled) {
        return aggregateByCurrency(tradesInScope(includeSettled));
    }

    @Transactional(readOnly = true)
    public List<CounterpartyExposure> counterpartyExposures(boolean includeSettled) {
        return aggregateByCounterparty(tradesInScope(includeSettled));
    }

    private List<Trade> tradesInScope(boolean includeSettled) {
        var statuses = includeSettled
                ? EnumSet.of(TradeStatus.CAPTURED, TradeStatus.VALIDATED,
                TradeStatus.CONFIRMED, TradeStatus.SETTLED)
                : EnumSet.of(TradeStatus.CAPTURED, TradeStatus.VALIDATED, TradeStatus.CONFIRMED);

        return tradeRepository.findByStatusInOrderByValueDate(statuses);
    }

    // ------------------------------------------------------------------
    // Pure aggregation
    // ------------------------------------------------------------------

    public static List<CurrencyPosition> aggregateByCurrency(List<Trade> trades) {
        Map<String, Accumulator> byCurrency = new TreeMap<>();

        for (var trade : trades) {
            byCurrency.computeIfAbsent(trade.getPair().getBaseCcy(), c -> new Accumulator())
                    .add(trade.signedBaseAmount());
            byCurrency.computeIfAbsent(trade.getPair().getQuoteCcy(), c -> new Accumulator())
                    .add(trade.signedQuoteAmount());
        }

        var positions = new ArrayList<CurrencyPosition>();
        byCurrency.forEach((currency, acc) -> positions.add(new CurrencyPosition(
                currency, acc.net, acc.bought, acc.sold.negate(), acc.legs)));

        positions.sort(Comparator.comparing(CurrencyPosition::currency));
        return positions;
    }

    public static List<CounterpartyExposure> aggregateByCounterparty(List<Trade> trades) {
        Map<String, Map<String, BigDecimal>> byCounterparty = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, BigDecimal> limits = new LinkedHashMap<>();
        Map<String, BigDecimal> largest = new LinkedHashMap<>();

        for (var trade : trades) {
            var code = trade.getCounterparty().getCode();
            names.putIfAbsent(code, trade.getCounterparty().getName());
            limits.putIfAbsent(code, trade.getCounterparty().getTradeLimit());
            counts.merge(code, 1, Integer::sum);
            largest.merge(code, trade.getNotional(), (a, b) -> a.compareTo(b) >= 0 ? a : b);

            var amounts = byCounterparty.computeIfAbsent(code, c -> new TreeMap<>());
            amounts.merge(trade.getPair().getBaseCcy(), trade.signedBaseAmount(), BigDecimal::add);
            amounts.merge(trade.getPair().getQuoteCcy(), trade.signedQuoteAmount(), BigDecimal::add);
        }

        var exposures = new ArrayList<CounterpartyExposure>();
        byCounterparty.forEach((code, amounts) -> exposures.add(new CounterpartyExposure(
                code, names.get(code), counts.get(code), limits.get(code),
                largest.get(code), amounts)));

        exposures.sort(Comparator.comparing(CounterpartyExposure::counterpartyCode));
        return exposures;
    }

    private static final class Accumulator {
        private BigDecimal net = BigDecimal.ZERO;
        private BigDecimal bought = BigDecimal.ZERO;
        private BigDecimal sold = BigDecimal.ZERO;
        private int legs;

        void add(BigDecimal amount) {
            net = net.add(amount);
            if (amount.signum() >= 0) {
                bought = bought.add(amount);
            } else {
                sold = sold.add(amount);
            }
            legs++;
        }
    }

    /**
     * @param netAmount    positive is long, negative is short
     * @param boughtAmount total received in this currency
     * @param soldAmount   total paid away, as a positive number
     * @param legs         trade legs touching this currency
     */
    public record CurrencyPosition(
            String currency,
            BigDecimal netAmount,
            BigDecimal boughtAmount,
            BigDecimal soldAmount,
            int legs
    ) {
    }

    public record CounterpartyExposure(
            String counterpartyCode,
            String counterpartyName,
            int tradeCount,
            BigDecimal tradeLimit,
            BigDecimal largestNotional,
            Map<String, BigDecimal> netByCurrency
    ) {
    }
}
