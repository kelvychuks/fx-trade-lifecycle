package com.codewithkelvin.fx.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "currency")
@Getter
@Setter
@NoArgsConstructor
public class Currency {

    @Id
    @Column(name = "code", length = 3)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    /** Decimal places the currency settles in. JPY is 0, most others 2. */
    @Column(name = "minor_units", nullable = false)
    private Short minorUnits;
}
