package com.codewithkelvin.fx.security;

/**
 * Segregation of duties, as a type.
 * <p>
 * A trading desk does not let the person who books a trade also confirm it —
 * that separation is what stops a bad trade being papered over by the person
 * who made it. Here the rule lives in the domain, not in the UI.
 */
public enum UserRole {

    /** Books, amends and cancels trades. */
    TRADER,

    /** Validates, confirms and settles trades, and loads market data. */
    MIDDLE_OFFICE,

    /** Reads everything, changes nothing. */
    VIEWER
}
