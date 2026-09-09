package com.codewithkelvin.fx.trading.dto;

import com.codewithkelvin.fx.trading.Direction;
import com.codewithkelvin.fx.trading.Product;
import com.codewithkelvin.fx.trading.Trade;
import com.codewithkelvin.fx.trading.TradeEvent;
import com.codewithkelvin.fx.trading.TradeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * What a blotter row needs. Deliberately flat and denormalised — a dealer's
 * screen wants "EURUSD" and "MERIDIAN", not nested objects to drill into.
 */
public record TradeDto(
        String tradeRef,
        String externalRef,
        String pair,
        String baseCcy,
        String quoteCcy,
        Product product,
        Direction direction,
        String tenor,
        BigDecimal notional,
        BigDecimal rate,
        BigDecimal counterAmount,
        LocalDate tradeDate,
        LocalDate valueDate,
        String book,
        TradeStatus status,
        String counterpartyCode,
        String counterpartyName,
        String capturedBy,
        String confirmedBy,
        String cancelReason,
        Instant capturedAt,
        Instant updatedAt,
        Integer version
) {

    public static TradeDto from(Trade trade) {
        return new TradeDto(
                trade.getTradeRef(),
                trade.getExternalRef(),
                trade.getPair().getSymbol(),
                trade.getPair().getBaseCcy(),
                trade.getPair().getQuoteCcy(),
                trade.getProduct(),
                trade.getDirection(),
                trade.getTenor(),
                trade.getNotional(),
                trade.getRate(),
                trade.getCounterAmount(),
                trade.getTradeDate(),
                trade.getValueDate(),
                trade.getBook(),
                trade.getStatus(),
                trade.getCounterparty().getCode(),
                trade.getCounterparty().getName(),
                trade.getCapturedBy() == null ? null : trade.getCapturedBy().getUsername(),
                trade.getConfirmedBy() == null ? null : trade.getConfirmedBy().getUsername(),
                trade.getCancelReason(),
                trade.getCapturedAt(),
                trade.getUpdatedAt(),
                trade.getVersion());
    }

    public record TradeEventDto(
            int sequenceNo,
            String eventType,
            TradeStatus fromStatus,
            TradeStatus toStatus,
            String actor,
            String detail,
            Instant occurredAt
    ) {
        public static TradeEventDto from(TradeEvent event) {
            return new TradeEventDto(
                    event.getSequenceNo(),
                    event.getEventType().name(),
                    event.getFromStatus(),
                    event.getToStatus(),
                    event.getActor(),
                    event.getDetail(),
                    event.getOccurredAt());
        }

        public static List<TradeEventDto> from(List<TradeEvent> events) {
            return events.stream().map(TradeEventDto::from).toList();
        }
    }
}
