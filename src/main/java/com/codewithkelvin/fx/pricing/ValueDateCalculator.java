package com.codewithkelvin.fx.pricing;

import java.time.LocalDate;

/**
 * Value date derivation. Market conventions:
 *
 * <ol>
 *   <li>Spot = trade date + the pair's spot lag, counting only days when both
 *       currencies are open.</li>
 *   <li>The spot date must also be a USD business day, even for a cross with no
 *       dollar in it. If it lands on a US holiday it rolls forward.</li>
 *   <li>Forwards run from spot, adjusted Modified Following: roll forward, or
 *       back if rolling forward crosses into the next month.</li>
 *   <li>End-of-month: if spot is the last business day of its month, month
 *       tenors land on the last business day of theirs. Spot 28 Feb + 1M is
 *       31 Mar.</li>
 * </ol>
 */
public class ValueDateCalculator {

    /** Correspondent banking runs through USD, so its calendar constrains most value dates. */
    private static final String USD = "USD";

    private final BusinessDayCalendar calendar;

    public ValueDateCalculator(BusinessDayCalendar calendar) {
        this.calendar = calendar;
    }

    public LocalDate spotDate(LocalDate tradeDate, String baseCcy, String quoteCcy, int spotLagDays) {
        var date = tradeDate;
        var counted = 0;

        while (counted < spotLagDays) {
            date = date.plusDays(1);
            if (calendar.isBusinessDay(date, baseCcy, quoteCcy)) {
                counted++;
            }
        }

        // Rule 2: settlement date must also be good in USD.
        while (!calendar.isBusinessDay(date, baseCcy, quoteCcy, USD)) {
            date = date.plusDays(1);
        }

        return date;
    }

    public LocalDate valueDate(LocalDate tradeDate, String baseCcy, String quoteCcy,
                               int spotLagDays, Tenor tenor) {
        var spot = spotDate(tradeDate, baseCcy, quoteCcy, spotLagDays);
        if (tenor.isSpot()) {
            return spot;
        }

        var unadjusted = spot.plus(tenor.period());

        if (tenor.isMonthBased() && isLastBusinessDayOfMonth(spot, baseCcy, quoteCcy)) {
            return calendar.lastBusinessDayOfMonth(unadjusted, baseCcy, quoteCcy, USD);
        }

        return modifiedFollowing(unadjusted, baseCcy, quoteCcy);
    }

    /** Public: the amend path re-derives a value date from a known spot. */
    public LocalDate modifiedFollowing(LocalDate date, String baseCcy, String quoteCcy) {
        var rolled = date;
        while (!calendar.isBusinessDay(rolled, baseCcy, quoteCcy, USD)) {
            rolled = rolled.plusDays(1);
        }

        if (rolled.getMonthValue() != date.getMonthValue() || rolled.getYear() != date.getYear()) {
            rolled = date;
            while (!calendar.isBusinessDay(rolled, baseCcy, quoteCcy, USD)) {
                rolled = rolled.minusDays(1);
            }
        }

        return rolled;
    }

    public boolean isLastBusinessDayOfMonth(LocalDate date, String baseCcy, String quoteCcy) {
        return date.equals(calendar.lastBusinessDayOfMonth(date, baseCcy, quoteCcy, USD));
    }

    public boolean isSettlementDay(LocalDate date, String baseCcy, String quoteCcy) {
        return calendar.isBusinessDay(date, baseCcy, quoteCcy, USD);
    }

    public BusinessDayCalendar calendar() {
        return calendar;
    }
}
