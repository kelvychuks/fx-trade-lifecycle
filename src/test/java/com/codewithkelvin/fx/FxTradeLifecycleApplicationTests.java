package com.codewithkelvin.fx;

import com.codewithkelvin.fx.trading.TradeRepository;
import com.codewithkelvin.fx.trading.TradeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@EnabledIf("com.codewithkelvin.fx.DockerAvailable#check")
class FxTradeLifecycleApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TradeRepository tradeRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void migrationsCreateTheExpectedSchema() {
        var tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'",
                String.class);

        assertThat(tables).contains(
                "app_user", "currency", "currency_pair", "counterparty", "holiday",
                "fx_rate", "trade", "trade_event", "trade_valuation");
    }

    @Test
    void referenceDataIsLoaded() {
        var pairs = jdbcTemplate.queryForObject(
                "select count(*) from currency_pair where active", Integer.class);
        var holidays = jdbcTemplate.queryForObject("select count(*) from holiday", Integer.class);

        assertThat(pairs).isEqualTo(7);
        assertThat(holidays).isGreaterThan(30);
    }

    @Test
    void theDeskHasABookOfTradesAcrossSeveralStates() {
        assertThat(tradeRepository.count()).isGreaterThan(20);

        // A demo that only shows captured trades demonstrates nothing about the
        // lifecycle, so the seed has to spread across it.
        var distinctStates = jdbcTemplate.queryForObject(
                "select count(distinct status) from trade", Integer.class);
        assertThat(distinctStates).isGreaterThanOrEqualTo(3);

        assertThat(tradeRepository.countByStatus(TradeStatus.CONFIRMED)).isPositive();
    }

    @Test
    void everyTradeHasAnAuditTrailStartingWithItsCapture() {
        var tradesWithoutACaptureEvent = jdbcTemplate.queryForObject("""
                select count(*) from trade t
                where not exists (
                    select 1 from trade_event e
                    where e.trade_id = t.id and e.sequence_no = 1 and e.event_type = 'CAPTURE')
                """, Integer.class);

        assertThat(tradesWithoutACaptureEvent).isZero();
    }

    @Test
    void liveTradesAreValuedByTheEndOfDayRun() {
        var valuations = jdbcTemplate.queryForObject(
                "select count(*) from trade_valuation", Integer.class);

        assertThat(valuations).isPositive();
    }
}
