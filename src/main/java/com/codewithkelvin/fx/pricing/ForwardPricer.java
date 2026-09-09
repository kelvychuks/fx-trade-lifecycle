package com.codewithkelvin.fx.pricing;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Forward pricing by covered interest parity:
 *
 * <pre>
 *   F = S x (1 + r_quote x t) / (1 + r_base x t)        t = days / 360
 * </pre>
 *
 * A forward is not a forecast. It is the rate that makes borrow-base /
 * sell-spot / deposit-quote break even, so the higher-yielding currency trades
 * at a forward discount.
 *
 * <p>Simplifications: simple interest, ACT/360 on both legs, one deposit rate
 * per currency. Real desks use each currency's own basis (GBP is ACT/365),
 * compound beyond a year, and price off a curve.
 */
public final class ForwardPricer {

    /** Working precision high enough that rounding only happens at the boundary. */
    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    private static final BigDecimal DAY_COUNT_BASIS = new BigDecimal("360");

    private ForwardPricer() {
    }

    /**
     * @param spot      spot mid rate, quote currency per one base currency
     * @param baseRate  annualised deposit rate for the base currency (0.0425 = 4.25%)
     * @param quoteRate annualised deposit rate for the quote currency
     * @param days      calendar days from spot date to the forward value date
     */
    public static BigDecimal forwardRate(BigDecimal spot, BigDecimal baseRate,
                                         BigDecimal quoteRate, long days) {
        if (days <= 0) {
            return spot;
        }

        var yearFraction = BigDecimal.valueOf(days).divide(DAY_COUNT_BASIS, MC);
        var quoteLeg = BigDecimal.ONE.add(quoteRate.multiply(yearFraction, MC), MC);
        var baseLeg = BigDecimal.ONE.add(baseRate.multiply(yearFraction, MC), MC);

        return spot.multiply(quoteLeg, MC).divide(baseLeg, MC);
    }

    /** Forward minus spot in pips, which is how a dealer quotes it: "EURUSD 3M at 42 points". */
    public static BigDecimal forwardPoints(BigDecimal forward, BigDecimal spot, int pipFactor) {
        return forward.subtract(spot, MC)
                .multiply(BigDecimal.valueOf(pipFactor), MC)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Discounts a future quote-currency cash flow back to today. An unrealised
     * gain due in six months is not worth its face value now.
     */
    public static BigDecimal discountFactor(BigDecimal quoteRate, long days) {
        if (days <= 0) {
            return BigDecimal.ONE;
        }

        var yearFraction = BigDecimal.valueOf(days).divide(DAY_COUNT_BASIS, MC);
        var denominator = BigDecimal.ONE.add(quoteRate.multiply(yearFraction, MC), MC);

        return BigDecimal.ONE.divide(denominator, MC).setScale(10, RoundingMode.HALF_UP);
    }

    public static long daysBetween(LocalDate from, LocalDate to) {
        return ChronoUnit.DAYS.between(from, to);
    }

    /** Rounds a rate to the number of decimals the pair is quoted in. */
    public static BigDecimal roundRate(BigDecimal rate, int scale) {
        return rate.setScale(scale, RoundingMode.HALF_UP);
    }

    /** Buying 1,000,000 EURUSD at 1.09250 means paying 1,092,500.00 USD. */
    public static BigDecimal counterAmount(BigDecimal notional, BigDecimal rate, int quoteMinorUnits) {
        return notional.multiply(rate, MC).setScale(quoteMinorUnits, RoundingMode.HALF_UP);
    }
}
