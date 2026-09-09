package com.codewithkelvin.fx.pricing;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Forward pricing by covered interest parity.
 * <p>
 * A forward rate is not a forecast. It is the only rate at which a bank cannot
 * be arbitraged, given today's spot and the two currencies' interest rates:
 * borrow the base currency, sell it spot, deposit the proceeds in the quote
 * currency, and the forward rate is whatever makes that trade break even.
 *
 * <pre>
 *   F = S x (1 + r_quote x t) / (1 + r_base x t)        t = days / 360
 * </pre>
 *
 * The currency with the higher interest rate trades at a forward discount.
 *
 * <p><b>Conventions and their limits.</b> Simple interest on ACT/360 for both
 * legs. Real desks use each currency's own day-count basis (GBP and several
 * others are ACT/365), compound beyond a year, and price off a term structure
 * of rates rather than one deposit rate per currency. Those are the honest
 * simplifications here; the arithmetic below is otherwise the real thing.
 *
 * <p>All arithmetic is {@link BigDecimal} with an explicit {@link MathContext}.
 * A double would be fine for display and wrong for money.
 */
public final class ForwardPricer {

    /** Enough working precision that rounding only happens at the boundary. */
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

    /**
     * The difference between forward and spot, expressed in pips — how a dealer
     * actually quotes a forward ("EURUSD 3M at 42 points").
     */
    public static BigDecimal forwardPoints(BigDecimal forward, BigDecimal spot, int pipFactor) {
        return forward.subtract(spot, MC)
                .multiply(BigDecimal.valueOf(pipFactor), MC)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Discount factor for bringing a future cash flow in the quote currency back
     * to today. Used by the valuation run: an unrealised gain due in six months
     * is not worth its face value now.
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

    /**
     * The other side of the deal. Buying 1,000,000 EURUSD at 1.09250 means
     * paying 1,092,500.00 USD.
     */
    public static BigDecimal counterAmount(BigDecimal notional, BigDecimal rate, int quoteMinorUnits) {
        return notional.multiply(rate, MC).setScale(quoteMinorUnits, RoundingMode.HALF_UP);
    }
}
