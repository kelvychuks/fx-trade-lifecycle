package com.codewithkelvin.fx.trading;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One entry in a trade's audit trail. Append-only by contract: nothing in this
 * codebase updates or deletes a TradeEvent. The status column on the trade is a
 * cache; this table is the record of what happened.
 */
@Entity
@Table(name = "trade_event")
@Getter
@Setter
@NoArgsConstructor
public class TradeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trade_id", nullable = false)
    private Trade trade;

    /** 1, 2, 3... per trade. Gaps would mean something was deleted. */
    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private TradeEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private TradeStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private TradeStatus toStatus;

    @Column(name = "actor", nullable = false)
    private String actor;

    @Column(name = "detail", length = 500)
    private String detail;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
}
