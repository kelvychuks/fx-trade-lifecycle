package com.codewithkelvin.fx.pricing;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Settlement calendar: which days each currency's payment system is open.
 * <p>
 * Deliberately a plain object with no Spring or JPA in it, so the value-date
 * rules that depend on it can be unit tested against a hand-built calendar
 * without a database.
 */
public class BusinessDayCalendar {

    private final Map<String, Set<LocalDate>> holidaysByCurrency;

    public BusinessDayCalendar(Map<String, Set<LocalDate>> holidaysByCurrency) {
        var copy = new HashMap<String, Set<LocalDate>>();
        holidaysByCurrency.forEach((ccy, dates) -> copy.put(ccy, Set.copyOf(dates)));
        this.holidaysByCurrency = Collections.unmodifiableMap(copy);
    }

    public static BusinessDayCalendar weekendsOnly() {
        return new BusinessDayCalendar(Map.of());
    }

    /** Convenience for tests: {@code of("USD", List.of(date), "EUR", List.of(...))}. */
    public static BusinessDayCalendar of(String ccy, List<LocalDate> holidays) {
        return new BusinessDayCalendar(Map.of(ccy, new HashSet<>(holidays)));
    }

    public boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    public boolean isHoliday(String currency, LocalDate date) {
        var holidays = holidaysByCurrency.get(currency);
        return holidays != null && holidays.contains(date);
    }

    /**
     * A day is good for settlement only if every currency involved is open.
     * One closed calendar is enough to move the value date.
     */
    public boolean isBusinessDay(LocalDate date, String... currencies) {
        if (isWeekend(date)) {
            return false;
        }
        for (var currency : currencies) {
            if (isHoliday(currency, date)) {
                return false;
            }
        }
        return true;
    }

    public LocalDate nextBusinessDay(LocalDate date, String... currencies) {
        var next = date.plusDays(1);
        while (!isBusinessDay(next, currencies)) {
            next = next.plusDays(1);
        }
        return next;
    }

    public LocalDate previousBusinessDay(LocalDate date, String... currencies) {
        var previous = date.minusDays(1);
        while (!isBusinessDay(previous, currencies)) {
            previous = previous.minusDays(1);
        }
        return previous;
    }

    /** The last day of {@code date}'s month on which all the currencies are open. */
    public LocalDate lastBusinessDayOfMonth(LocalDate date, String... currencies) {
        var candidate = date.withDayOfMonth(date.lengthOfMonth());
        while (!isBusinessDay(candidate, currencies)) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }
}
