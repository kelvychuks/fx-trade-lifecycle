package com.codewithkelvin.fx.pricing;

import java.time.LocalDate;

/**
 * Derives the day cash actually moves.
 * <p>
 * Getting this wrong is the classic FX booking error: the trade looks fine on
 * the blotter and then fails settlement, or prices off the wrong number of days.
 * The rules implemented here are the market conventions:
 *
 * <ol>
 *   <li><b>Spot</b> is the trade date plus the pair's spot lag (two business days
 *       for almost everything), counting only days on which <em>both</em>
 *       currencies are open.</li>
 *   <li>The spot date must additionally be a US business day, even for a pair
 *       with no dollar in it — the dollar leg of the settlement chain has to
 *       clear. If it lands on a US holiday, it rolls forward.</li>
 *   <li><b>Forwards</b> are measured from spot, then adjusted <b>Modified
 *       Following</b>: roll forward to the next open day, unless that crosses
 *       into the next month, in which case roll back instead. A "3 month"
 *       trade never quietly becomes a four-month one.</li>
 *   <li><b>End-of-month rule</b>: if spot is the last business day of its month,
 *       every month-based forward lands on the last business day of its month.
 *       Spot 28 Feb plus one month is 31 March, not 28 March.</li>
 * </ol>
 *
 * Plain class, no framework: every rule above is a unit test.
 */
public class ValueDateCalculator {

    /**
     * The dollar's settlement calendar constrains almost every FX value date
     * because the correspondent banking chain runs through it.
     */
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

        // Rule 2: the settlement date itself must also be good in USD.
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

    /** Public because the amend path re-derives a value date from a known spot. */
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
