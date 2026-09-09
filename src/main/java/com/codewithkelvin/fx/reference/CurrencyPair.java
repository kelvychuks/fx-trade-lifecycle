package com.codewithkelvin.fx.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A tradeable pair, quoted as units of the quote currency per one unit of the
 * base currency. EURUSD 1.09 means one euro buys 1.09 dollars, so "buy EURUSD"
 * always means buy the base and sell the quote.
 */
@Entity
@Table(name = "currency_pair")
@Getter
@Setter
@NoArgsConstructor
public class CurrencyPair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 7)
    private String symbol;

    @Column(name = "base_ccy", nullable = false, length = 3)
    private String baseCcy;

    @Column(name = "quote_ccy", nullable = false, length = 3)
    private String quoteCcy;

    /** Business days from trade date to spot value date. */
    @Column(name = "spot_lag_days", nullable = false)
    private Short spotLagDays;

    /** Multiplier that turns a rate difference into forward points. */
    @Column(name = "pip_factor", nullable = false)
    private Integer pipFactor;

    /** Decimal places the pair is quoted to. */
    @Column(name = "rate_scale", nullable = false)
    private Short rateScale;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
