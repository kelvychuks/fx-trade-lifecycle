package com.codewithkelvin.fx.trading;

/**
 * The lifecycle, and the only place that defines which move follows which.
 *
 * <pre>
 *   CAPTURED --validate--> VALIDATED --confirm--> CONFIRMED --settle--> SETTLED
 *      ^                       |                      |
 *      +-------- amend --------+                      |
 *      +-------- cancel -------+------ cancel --------+
 * </pre>
 *
 * Two rules that are easy to get wrong:
 * <ul>
 *   <li>Amending a validated trade returns it to CAPTURED. The economics
 *       changed, so validation has to run again against the new terms.</li>
 *   <li>A CONFIRMED trade can still be cancelled by agreement until it settles.
 *       A SETTLED one cannot: the cash has moved, and the correction is a new
 *       offsetting trade.</li>
 * </ul>
 */
public enum TradeStatus {

    CAPTURED,
    VALIDATED,
    CONFIRMED,
    SETTLED,
    CANCELLED;

    public boolean canTransitionTo(TradeStatus target) {
        return switch (this) {
            case CAPTURED -> target == VALIDATED || target == CANCELLED || target == CAPTURED;
            case VALIDATED -> target == CONFIRMED || target == CAPTURED || target == CANCELLED;
            case CONFIRMED -> target == SETTLED || target == CANCELLED;
            case SETTLED, CANCELLED -> false;
        };
    }

    /** Nothing further can happen to it. */
    public boolean isTerminal() {
        return this == SETTLED || this == CANCELLED;
    }

    /** Still carries FX risk. This is the set that feeds positions and revaluation. */
    public boolean isLive() {
        return this == CAPTURED || this == VALIDATED || this == CONFIRMED;
    }

    public boolean isAmendable() {
        return this == CAPTURED || this == VALIDATED;
    }
}
