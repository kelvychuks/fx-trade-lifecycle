package com.codewithkelvin.fx.pricing;

import com.codewithkelvin.fx.common.BusinessRuleException;

import java.time.Period;
import java.util.Arrays;

/**
 * Standard FX tenors, measured from the spot date rather than the trade date —
 * a "1M" forward matures one month after spot, not one month after today.
 */
public enum Tenor {

    SP("Spot", Period.ZERO, false),
    W1("1 week", Period.ofWeeks(1), false),
    W2("2 weeks", Period.ofWeeks(2), false),
    W3("3 weeks", Period.ofWeeks(3), false),
    M1("1 month", Period.ofMonths(1), true),
    M2("2 months", Period.ofMonths(2), true),
    M3("3 months", Period.ofMonths(3), true),
    M6("6 months", Period.ofMonths(6), true),
    M9("9 months", Period.ofMonths(9), true),
    Y1("1 year", Period.ofYears(1), true);

    private final String label;
    private final Period period;
    private final boolean monthBased;

    Tenor(String label, Period period, boolean monthBased) {
        this.label = label;
        this.period = period;
        this.monthBased = monthBased;
    }

    public String label() {
        return label;
    }

    public Period period() {
        return period;
    }

    /**
     * Month-based tenors follow the end-of-month rule; week-based ones never do.
     * Spot plus one month from 28 February is 31 March, not 28 March.
     */
    public boolean isMonthBased() {
        return monthBased;
    }

    public boolean isSpot() {
        return this == SP;
    }

    public static Tenor parse(String value) {
        if (value == null || value.isBlank()) {
            return SP;
        }
        var normalised = value.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(t -> t.name().equals(normalised))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        "UNKNOWN_TENOR",
                        "Unknown tenor '" + value + "'. Supported: " + Arrays.toString(values())));
    }
}
