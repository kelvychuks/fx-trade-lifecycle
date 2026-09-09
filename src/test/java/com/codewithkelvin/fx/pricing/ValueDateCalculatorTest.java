package com.codewithkelvin.fx.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The value-date rules, tested against hand-built calendars.
 * <p>
 * These are the cases that actually go wrong in production: a spot date that
 * lands on a holiday in only one of the two currencies, a forward that would
 * roll into the following month, and the end-of-month rule that catches
 * everyone the first time they meet it.
 */
class ValueDateCalculatorTest {

    private static final LocalDate TUESDAY = LocalDate.of(2026, 9, 8);

    private static ValueDateCalculator weekendsOnly() {
        return new ValueDateCalculator(BusinessDayCalendar.weekendsOnly());
    }

    private static ValueDateCalculator withHolidays(Map<String, Set<LocalDate>> holidays) {
        return new ValueDateCalculator(new BusinessDayCalendar(holidays));
    }

    @Nested
    @DisplayName("Spot date")
    class SpotDate {

        @Test
        @DisplayName("is two business days after the trade date")
        void spotIsTPlusTwo() {
            var spot = weekendsOnly().spotDate(TUESDAY, "EUR", "USD", 2);

            assertThat(spot).isEqualTo(LocalDate.of(2026, 9, 10));
        }

        @Test
        @DisplayName("steps over the weekend rather than counting it")
        void weekendsAreNotBusinessDays() {
            var thursday = LocalDate.of(2026, 9, 10);

            var spot = weekendsOnly().spotDate(thursday, "EUR", "USD", 2);

            // Friday is one, then Saturday and Sunday are skipped entirely.
            assertThat(spot).isEqualTo(LocalDate.of(2026, 9, 14));
            assertThat(spot.getDayOfWeek().getValue()).isEqualTo(1);
        }

        @Test
        @DisplayName("a holiday in either currency does not count as a business day")
        void holidayInOneCurrencyPushesSpotOut() {
            var calculator = withHolidays(Map.of(
                    "USD", Set.of(LocalDate.of(2026, 9, 9))));

            var spot = calculator.spotDate(TUESDAY, "EUR", "USD", 2);

            // Wednesday is closed in the US, so the two good days are Thursday
            // and Friday.
            assertThat(spot).isEqualTo(LocalDate.of(2026, 9, 11));
        }

        @Test
        @DisplayName("the dollar calendar constrains a pair with no dollar in it")
        void usdHolidayMovesACrossRateSpot() {
            var calculator = withHolidays(Map.of(
                    "USD", Set.of(LocalDate.of(2026, 9, 10))));

            var spot = calculator.spotDate(TUESDAY, "EUR", "GBP", 2);

            // EUR and GBP are both open on the 9th and 10th, so counting lands
            // on Thursday the 10th — but that is a US holiday, and the dollar
            // leg of the settlement chain has to clear, so it rolls to Friday.
            assertThat(spot).isEqualTo(LocalDate.of(2026, 9, 11));
        }

        @Test
        @DisplayName("respects a pair whose convention is T+1")
        void honoursSpotLag() {
            var spot = weekendsOnly().spotDate(TUESDAY, "USD", "CAD", 1);

            assertThat(spot).isEqualTo(LocalDate.of(2026, 9, 9));
        }
    }

    @Nested
    @DisplayName("Forward value date")
    class ForwardDate {

        @Test
        @DisplayName("one month from spot, rolled forward off a weekend")
        void modifiedFollowingRollsForward() {
            // Spot is Thursday 10 September; one month lands on Saturday
            // 10 October, which rolls forward to Monday the 12th.
            var valueDate = weekendsOnly().valueDate(TUESDAY, "EUR", "USD", 2, Tenor.M1);

            assertThat(valueDate).isEqualTo(LocalDate.of(2026, 10, 12));
        }

        @Test
        @DisplayName("rolls backward instead of crossing into the next month")
        void modifiedFollowingRollsBackAtMonthEnd() {
            // Saturday 31 October: rolling forward would land in November, so
            // the convention rolls back to Friday the 30th.
            var adjusted = weekendsOnly()
                    .modifiedFollowing(LocalDate.of(2026, 10, 31), "EUR", "USD");

            assertThat(adjusted).isEqualTo(LocalDate.of(2026, 10, 30));
        }

        @Test
        @DisplayName("end-of-month rule: last business day maps to last business day")
        void endOfMonthRule() {
            // Trade Wednesday 24 February 2027 -> spot Friday the 26th, which is
            // the last business day of February (the 27th and 28th are a
            // weekend). One month later must therefore be the last business day
            // of March, not the 26th.
            var tradeDate = LocalDate.of(2027, 2, 24);
            var calculator = weekendsOnly();

            var spot = calculator.spotDate(tradeDate, "EUR", "USD", 2);
            var valueDate = calculator.valueDate(tradeDate, "EUR", "USD", 2, Tenor.M1);

            assertThat(spot).isEqualTo(LocalDate.of(2027, 2, 26));
            assertThat(calculator.isLastBusinessDayOfMonth(spot, "EUR", "USD")).isTrue();
            assertThat(valueDate).isEqualTo(LocalDate.of(2027, 3, 31));
        }

        @Test
        @DisplayName("week tenors are never subject to the end-of-month rule")
        void weekTenorsIgnoreEndOfMonth() {
            var tradeDate = LocalDate.of(2027, 2, 24);

            var valueDate = weekendsOnly().valueDate(tradeDate, "EUR", "USD", 2, Tenor.W1);

            // Spot 26 February plus one week, plainly.
            assertThat(valueDate).isEqualTo(LocalDate.of(2027, 3, 5));
        }

        @Test
        @DisplayName("a spot tenor is just the spot date")
        void spotTenorReturnsSpot() {
            var calculator = weekendsOnly();

            assertThat(calculator.valueDate(TUESDAY, "EUR", "USD", 2, Tenor.SP))
                    .isEqualTo(calculator.spotDate(TUESDAY, "EUR", "USD", 2));
        }

        @Test
        @DisplayName("a holiday on the forward date pushes it to the next open day")
        void holidayOnValueDateRollsForward() {
            var calculator = withHolidays(Map.of(
                    "USD", Set.of(LocalDate.of(2026, 10, 12))));

            var valueDate = calculator.valueDate(TUESDAY, "EUR", "USD", 2, Tenor.M1);

            assertThat(valueDate).isEqualTo(LocalDate.of(2026, 10, 13));
        }
    }

    @Nested
    @DisplayName("Calendar")
    class Calendar {

        @Test
        void knowsWeekendsAndHolidays() {
            var calendar = BusinessDayCalendar.of("USD", List.of(LocalDate.of(2026, 12, 25)));

            assertThat(calendar.isBusinessDay(LocalDate.of(2026, 12, 25), "USD")).isFalse();
            assertThat(calendar.isBusinessDay(LocalDate.of(2026, 12, 25), "EUR")).isTrue();
            assertThat(calendar.isBusinessDay(LocalDate.of(2026, 12, 26), "EUR")).isFalse();
            assertThat(calendar.isBusinessDay(LocalDate.of(2026, 12, 24), "USD")).isTrue();
        }

        @Test
        void findsTheLastBusinessDayOfAMonth() {
            var calendar = BusinessDayCalendar.weekendsOnly();

            // 31 October 2026 is a Saturday.
            assertThat(calendar.lastBusinessDayOfMonth(LocalDate.of(2026, 10, 5), "USD"))
                    .isEqualTo(LocalDate.of(2026, 10, 30));
        }
    }
}
