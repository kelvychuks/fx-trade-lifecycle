package com.codewithkelvin.fx.valuation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Runs the valuation after the New York close.
 * <p>
 * Single-instance only: with two replicas both would run it. The fix in a real
 * deployment is a lock the instances contend for (ShedLock, or a row in the
 * database) — noted here because "it worked on one instance" is exactly the
 * kind of thing that breaks quietly on the day you scale out.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.valuation.scheduled", havingValue = "true", matchIfMissing = true)
public class EndOfDayScheduler {

    private final ValuationService valuationService;

    @Scheduled(cron = "${app.valuation.cron:0 30 22 * * MON-FRI}", zone = "UTC")
    public void runEndOfDay() {
        var today = LocalDate.now();
        log.info("Starting scheduled end-of-day valuation for {}", today);

        var run = valuationService.runEndOfDay(today);

        if (!run.skipped().isEmpty()) {
            log.warn("End-of-day valuation for {} skipped {} trades", today, run.skipped().size());
        }
    }
}
