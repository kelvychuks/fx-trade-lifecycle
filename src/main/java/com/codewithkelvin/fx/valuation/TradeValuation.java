package com.codewithkelvin.fx.valuation;

import com.codewithkelvin.fx.trading.Trade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One trade's mark for one day. Re-running a day overwrites its own row. */
@Entity
@Table(name = "trade_valuation")
@Getter
@Setter
@NoArgsConstructor
public class TradeValuation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "trade_id", nullable = false)
    private Trade trade;

    @Column(name = "valuation_date", nullable = false)
    private LocalDate valuationDate;

    /** The rate the trade could be closed out at today, for its own value date. */
    @Column(name = "market_rate", nullable = false)
    private BigDecimal marketRate;

    @Column(name = "mtm_quote_ccy", nullable = false)
    private BigDecimal mtmQuoteCcy;

    @Column(name = "discount_factor", nullable = false)
    private BigDecimal discountFactor;

    @Column(name = "pv_quote_ccy", nullable = false)
    private BigDecimal pvQuoteCcy;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
