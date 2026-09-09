package com.codewithkelvin.fx.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ForwardPricerTest {

    private static final BigDecimal EURUSD_SPOT = new BigDecimal("1.09250");
    private static final BigDecimal EUR_RATE = new BigDecimal("0.0290");
    private static final BigDecimal USD_RATE = new BigDecimal("0.0425");

    @Test
    @DisplayName("prices a three-month EURUSD forward off interest rate parity")
    void pricesAThreeMonthForward() {
        // F = 1.0925 x (1 + 0.0425 x 90/360) / (1 + 0.0290 x 90/360)
        var forward = ForwardPricer.forwardRate(EURUSD_SPOT, EUR_RATE, USD_RATE, 90);

        assertThat(ForwardPricer.roundRate(forward, 5)).isEqualByComparingTo("1.09616");
    }

    @Test
    @DisplayName("the currency with the lower interest rate trades at a forward premium")
    void lowerRateCurrencyTradesAtAPremium() {
        // Euro rates are below dollar rates, so a euro bought forward costs
        // more dollars than one bought spot.
        var forward = ForwardPricer.forwardRate(EURUSD_SPOT, EUR_RATE, USD_RATE, 180);

        assertThat(forward).isGreaterThan(EURUSD_SPOT);
    }

    @Test
    @DisplayName("and the higher interest rate currency trades at a discount")
    void higherRateCurrencyTradesAtADiscount() {
        // USDJPY: the dollar is the base and yields far more than the yen, so
        // the forward is below spot.
        var spot = new BigDecimal("152.400");
        var forward = ForwardPricer.forwardRate(spot, new BigDecimal("0.0425"),
                new BigDecimal("0.0035"), 90);

        assertThat(ForwardPricer.roundRate(forward, 3)).isEqualByComparingTo("150.930");
        assertThat(forward).isLessThan(spot);
    }

    @Test
    @DisplayName("forward points are the rate difference in pips")
    void forwardPointsAreExpressedInPips() {
        var forward = ForwardPricer.roundRate(
                ForwardPricer.forwardRate(EURUSD_SPOT, EUR_RATE, USD_RATE, 90), 5);

        assertThat(ForwardPricer.forwardPoints(forward, EURUSD_SPOT, 10000))
                .isEqualByComparingTo("36.60");
    }

    @Test
    @DisplayName("a JPY pair's pips are the second decimal, not the fourth")
    void jpyPairsUseADifferentPipFactor() {
        var spot = new BigDecimal("152.400");
        var forward = ForwardPricer.roundRate(
                ForwardPricer.forwardRate(spot, new BigDecimal("0.0425"),
                        new BigDecimal("0.0035"), 90), 3);

        assertThat(ForwardPricer.forwardPoints(forward, spot, 100))
                .isEqualByComparingTo("-147.00");
    }

    @Test
    @DisplayName("a trade settling on the spot date has no forward points")
    void spotHasNoForwardPoints() {
        assertThat(ForwardPricer.forwardRate(EURUSD_SPOT, EUR_RATE, USD_RATE, 0))
                .isEqualByComparingTo(EURUSD_SPOT);
        assertThat(ForwardPricer.forwardRate(EURUSD_SPOT, EUR_RATE, USD_RATE, -5))
                .isEqualByComparingTo(EURUSD_SPOT);
    }

    @Test
    @DisplayName("discounts a future cash flow back to today")
    void discountsToPresentValue() {
        assertThat(ForwardPricer.discountFactor(USD_RATE, 180))
                .isEqualByComparingTo("0.9791921665");

        // Nothing to discount for money due today.
        assertThat(ForwardPricer.discountFactor(USD_RATE, 0)).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("the counter amount is rounded to the quote currency's minor units")
    void counterAmountRespectsMinorUnits() {
        var notional = new BigDecimal("1000000.00");

        assertThat(ForwardPricer.counterAmount(notional, new BigDecimal("1.09616"), 2))
                .isEqualByComparingTo("1096160.00");

        // Yen has no minor units: 152.44 x 1,000,000 is a whole number of yen.
        assertThat(ForwardPricer.counterAmount(notional, new BigDecimal("152.444"), 0))
                .isEqualByComparingTo("152444000");
    }

    @Test
    void countsCalendarDaysBetweenDates() {
        assertThat(ForwardPricer.daysBetween(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 12, 9)))
                .isEqualTo(90);
    }
}
