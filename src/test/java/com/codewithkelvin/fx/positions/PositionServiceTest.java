package com.codewithkelvin.fx.positions;

import com.codewithkelvin.fx.reference.Counterparty;
import com.codewithkelvin.fx.reference.CurrencyPair;
import com.codewithkelvin.fx.trading.Direction;
import com.codewithkelvin.fx.trading.Trade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Position keeping is arithmetic with a trap in it: every trade moves two
 * currencies in opposite directions, and the dollar leg of one pair nets
 * against the dollar leg of another.
 */
class PositionServiceTest {

    private static CurrencyPair pair(String symbol, String base, String quote) {
        var currencyPair = new CurrencyPair();
        currencyPair.setSymbol(symbol);
        currencyPair.setBaseCcy(base);
        currencyPair.setQuoteCcy(quote);
        currencyPair.setSpotLagDays((short) 2);
        currencyPair.setPipFactor(10000);
        currencyPair.setRateScale((short) 5);
        return currencyPair;
    }

    private static Counterparty counterparty(String code, String name, String limit) {
        var cp = new Counterparty();
        cp.setCode(code);
        cp.setName(name);
        cp.setTradeLimit(new BigDecimal(limit));
        return cp;
    }

    private static Trade trade(CurrencyPair pair, Counterparty cp, Direction direction,
                               String notional, String counterAmount) {
        var trade = new Trade();
        trade.setPair(pair);
        trade.setCounterparty(cp);
        trade.setDirection(direction);
        trade.setNotional(new BigDecimal(notional));
        trade.setCounterAmount(new BigDecimal(counterAmount));
        return trade;
    }

    @Test
    @DisplayName("buying a pair goes long the base and short the quote")
    void oneTradeMovesTwoCurrencies() {
        var eurusd = pair("EURUSD", "EUR", "USD");
        var cp = counterparty("MERIDIAN", "Meridian Bank plc", "25000000");

        var positions = PositionService.aggregateByCurrency(List.of(
                trade(eurusd, cp, Direction.BUY, "1000000.00", "1090000.00")));

        assertThat(positions).hasSize(2);
        assertThat(positions.get(0).currency()).isEqualTo("EUR");
        assertThat(positions.get(0).netAmount()).isEqualByComparingTo("1000000.00");
        assertThat(positions.get(1).currency()).isEqualTo("USD");
        assertThat(positions.get(1).netAmount()).isEqualByComparingTo("-1090000.00");
    }

    @Test
    @DisplayName("opposite trades in the same pair offset")
    void oppositeTradesNetOff() {
        var eurusd = pair("EURUSD", "EUR", "USD");
        var cp = counterparty("MERIDIAN", "Meridian Bank plc", "25000000");

        var positions = PositionService.aggregateByCurrency(List.of(
                trade(eurusd, cp, Direction.BUY, "1000000.00", "1090000.00"),
                trade(eurusd, cp, Direction.SELL, "400000.00", "440000.00")));

        var eur = positions.get(0);
        assertThat(eur.netAmount()).isEqualByComparingTo("600000.00");
        assertThat(eur.boughtAmount()).isEqualByComparingTo("1000000.00");
        assertThat(eur.soldAmount()).isEqualByComparingTo("400000.00");
        assertThat(eur.legs()).isEqualTo(2);
    }

    @Test
    @DisplayName("the dollar leg of one pair nets against the dollar leg of another")
    void dollarLegsNetAcrossPairs() {
        var eurusd = pair("EURUSD", "EUR", "USD");
        var usdjpy = pair("USDJPY", "USD", "JPY");
        var cp = counterparty("MERIDIAN", "Meridian Bank plc", "25000000");

        var positions = PositionService.aggregateByCurrency(List.of(
                trade(eurusd, cp, Direction.BUY, "1000000.00", "1090000.00"),
                trade(eurusd, cp, Direction.SELL, "400000.00", "440000.00"),
                trade(usdjpy, cp, Direction.BUY, "500000.00", "75000000.00")));

        // -1,090,000 from the first, +440,000 from the second, +500,000 from
        // being long dollars against yen.
        var usd = positions.stream().filter(p -> p.currency().equals("USD")).findFirst().orElseThrow();
        assertThat(usd.netAmount()).isEqualByComparingTo("-150000.00");

        var jpy = positions.stream().filter(p -> p.currency().equals("JPY")).findFirst().orElseThrow();
        assertThat(jpy.netAmount()).isEqualByComparingTo("-75000000.00");
    }

    @Test
    @DisplayName("an empty book has no position, not a zero position in every currency")
    void emptyBookHasNoPositions() {
        assertThat(PositionService.aggregateByCurrency(List.of())).isEmpty();
    }

    @Test
    @DisplayName("counterparty exposure keeps the largest single notional for limit checking")
    void counterpartyExposureTracksLargestNotional() {
        var eurusd = pair("EURUSD", "EUR", "USD");
        var meridian = counterparty("MERIDIAN", "Meridian Bank plc", "25000000");
        var atlas = counterparty("ATLASCB", "Atlas Commercial Bank", "5000000");

        var exposures = PositionService.aggregateByCounterparty(List.of(
                trade(eurusd, meridian, Direction.BUY, "1000000.00", "1090000.00"),
                trade(eurusd, meridian, Direction.BUY, "3500000.00", "3815000.00"),
                trade(eurusd, atlas, Direction.SELL, "250000.00", "272500.00")));

        assertThat(exposures).hasSize(2);

        var atlasExposure = exposures.get(0);
        assertThat(atlasExposure.counterpartyCode()).isEqualTo("ATLASCB");
        assertThat(atlasExposure.tradeCount()).isEqualTo(1);
        assertThat(atlasExposure.netByCurrency().get("EUR")).isEqualByComparingTo("-250000.00");

        var meridianExposure = exposures.get(1);
        assertThat(meridianExposure.tradeCount()).isEqualTo(2);
        assertThat(meridianExposure.largestNotional()).isEqualByComparingTo("3500000.00");
        assertThat(meridianExposure.netByCurrency().get("EUR")).isEqualByComparingTo("4500000.00");
        assertThat(meridianExposure.tradeLimit()).isEqualByComparingTo("25000000");
    }
}
