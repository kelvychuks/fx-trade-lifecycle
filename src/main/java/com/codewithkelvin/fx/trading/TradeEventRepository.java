package com.codewithkelvin.fx.trading;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeEventRepository extends JpaRepository<TradeEvent, Long> {

    List<TradeEvent> findByTradeIdOrderBySequenceNo(Long tradeId);

    int countByTradeId(Long tradeId);
}
