package com.codewithkelvin.fx.security;

/**
 * Segregation of duties. A desk does not let the person who books a trade also
 * confirm it; that separation is what stops a bad trade being papered over by
 * whoever made it.
 */
public enum UserRole {

    /** Books, amends and cancels trades. */
    TRADER,

    /** Validates, confirms and settles trades, and loads market data. */
    MIDDLE_OFFICE,

    /** Reads everything, changes nothing. */
    VIEWER
}
