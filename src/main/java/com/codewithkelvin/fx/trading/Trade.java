package com.codewithkelvin.fx.trading;

import com.codewithkelvin.fx.reference.Counterparty;
import com.codewithkelvin.fx.reference.CurrencyPair;
import com.codewithkelvin.fx.security.AppUser;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "trade")
@Getter
@Setter
@NoArgsConstructor
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The reference a human quotes: FX-2026-001042. */
    @Column(name = "trade_ref", nullable = false, updatable = false)
    private String tradeRef;

    /**
     * Caller-supplied idempotency key. Unique, so a retried booking request —
     * a flaky network, an impatient click — cannot create a second trade.
     */
    @Column(name = "external_ref")
    private String externalRef;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "pair_id", nullable = false)
    private CurrencyPair pair;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "counterparty_id", nullable = false)
    private Counterparty counterparty;

    @Enumerated(EnumType.STRING)
    @Column(name = "product", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private Direction direction;

    @Column(name = "tenor", length = 6)
    private String tenor;

    /** Amount of the base currency. */
    @Column(name = "notional", nullable = false)
    private BigDecimal notional;

    /** Quote currency per one unit of base currency. */
    @Column(name = "rate", nullable = false)
    private BigDecimal rate;

    /** Amount of the quote currency: notional x rate, frozen at booking. */
    @Column(name = "counter_amount", nullable = false)
    private BigDecimal counterAmount;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "book", nullable = false)
    private String book;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TradeStatus status;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "captured_by", nullable = false)
    private AppUser capturedBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "confirmed_by")
    private AppUser confirmedBy;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt = Instant.now();

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /**
     * Optimistic locking. Two people acting on the same trade at the same moment
     * is not hypothetical on a desk; the second one gets a 409 instead of
     * silently overwriting the first.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    /** Signed amount of the base currency this trade puts on the book. */
    public BigDecimal signedBaseAmount() {
        return direction == Direction.BUY ? notional : notional.negate();
    }

    /** Signed amount of the quote currency, which is always the other way round. */
    public BigDecimal signedQuoteAmount() {
        return direction == Direction.BUY ? counterAmount.negate() : counterAmount;
    }

    public boolean isMatured(LocalDate asOf) {
        return !valueDate.isAfter(asOf);
    }
}
