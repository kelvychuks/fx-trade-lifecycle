package com.codewithkelvin.fx.marketdata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FxRateRepository extends JpaRepository<FxRate, Long> {

    Optional<FxRate> findByPairIdAndRateDate(Long pairId, LocalDate rateDate);

    /**
     * The most recent snapshot on or before a date. Weekends and holidays have
     * no rates of their own, so valuation on a Monday for a Sunday date has to
     * fall back to Friday's close.
     */
    Optional<FxRate> findFirstByPairIdAndRateDateLessThanEqualOrderByRateDateDesc(
            Long pairId, LocalDate asOf);

    List<FxRate> findByRateDateOrderByPairSymbol(LocalDate rateDate);

    Optional<FxRate> findFirstByOrderByRateDateDesc();
}
