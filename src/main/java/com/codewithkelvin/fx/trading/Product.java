package com.codewithkelvin.fx.trading;

public enum Product {

    /** Settles on the spot date: two business days for most pairs. */
    SPOT,

    /** Settles on a date beyond spot, priced off the interest rate differential. */
    FORWARD
}
