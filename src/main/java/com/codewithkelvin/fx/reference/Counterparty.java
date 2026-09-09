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

import java.math.BigDecimal;

@Entity
@Table(name = "counterparty")
@Getter
@Setter
@NoArgsConstructor
public class Counterparty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 12)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "country", length = 2)
    private String country;

    /** Largest notional accepted on a single trade, in the pair's base currency. */
    @Column(name = "trade_limit")
    private BigDecimal tradeLimit;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
