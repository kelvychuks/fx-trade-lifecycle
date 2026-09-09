package com.codewithkelvin.fx.trading;

/**
 * The trade lifecycle, and the only place that says which move follows which.
 * <p>
 * <pre>
 *   CAPTURED ──validate──▶ VALIDATED ──confirm──▶ CONFIRMED ──settle──▶ SETTLED
 *      │  ▲                   │  │                    │
 *      │  └────── amend ──────┘  │                    │
 *      └──────── cancel ─────────┴──── cancel ────────┘
 * </pre>
 *
 * Two rules worth stating out loud, because they are the ones people get wrong:
 * <ul>
 *   <li>Amending a validated trade sends it back to CAPTURED. The economics
 *       changed, so the checks have to run again — a trade must never carry a
 *       validation that was performed against different terms.</li>
 *   <li>A CONFIRMED trade can still be cancelled, by agreement, right up until
 *       it settles. A SETTLED trade cannot: the money has moved, and the
 *       correction for that is a new offsetting trade, not an edit.</li>
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

    /** Nothing further can happen to the trade. */
    public boolean isTerminal() {
        return this == SETTLED || this == CANCELLED;
    }

    /**
     * The trade still carries FX risk: it exists and has not settled or been
     * cancelled. This is the set that feeds position keeping and revaluation.
     */
    public boolean isLive() {
        return this == CAPTURED || this == VALIDATED || this == CONFIRMED;
    }

    public boolean isAmendable() {
        return this == CAPTURED || this == VALIDATED;
    }
}
