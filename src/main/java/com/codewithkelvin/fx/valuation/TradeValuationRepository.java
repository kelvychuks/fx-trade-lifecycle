package com.codewithkelvin.fx.valuation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TradeValuationRepository extends JpaRepository<TradeValuation, Long> {

    Optional<TradeValuation> findByTradeIdAndValuationDate(Long tradeId, LocalDate valuationDate);

    List<TradeValuation> findByValuationDateOrderByTradeTradeRef(LocalDate valuationDate);

    List<TradeValuation> findByTradeIdOrderByValuationDate(Long tradeId);
}
