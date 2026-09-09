package com.codewithkelvin.fx.marketdata;

import com.codewithkelvin.fx.reference.CurrencyPair;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One day's market snapshot for one pair: the spot mid and the two deposit
 * rates a forward is priced from. Deposit rates live here rather than on the
 * currency because they move daily — they are market data, not reference data.
 */
@Entity
@Table(name = "fx_rate")
@Getter
@Setter
@NoArgsConstructor
public class FxRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pair_id", nullable = false)
    private CurrencyPair pair;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "spot_mid", nullable = false)
    private BigDecimal spotMid;

    /** Annualised, as a decimal: 0.0425 is 4.25%. */
    @Column(name = "base_rate", nullable = false)
    private BigDecimal baseRate;

    @Column(name = "quote_rate", nullable = false)
    private BigDecimal quoteRate;

    @Column(name = "source", nullable = false)
    private String source = "DEMO";
}
