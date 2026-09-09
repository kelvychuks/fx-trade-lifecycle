package com.codewithkelvin.fx.trading;

/**
 * Always expressed from the desk's point of view, and always about the base
 * currency. BUY EURUSD means the desk receives euros and pays dollars.
 */
public enum Direction {

    BUY,
    SELL;

    /** +1 for a long base-currency position, -1 for a short one. */
    public int sign() {
        return this == BUY ? 1 : -1;
    }

    public Direction opposite() {
        return this == BUY ? SELL : BUY;
    }
}
