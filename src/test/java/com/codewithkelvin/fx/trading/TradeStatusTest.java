package com.codewithkelvin.fx.trading;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lifecycle rules, stated as tests.
 * <p>
 * The illegal transitions matter more than the legal ones: a state machine that
 * lets everything through is not a state machine, and these are the assertions
 * that would fail if someone "simplified" it later.
 */
class TradeStatusTest {

    @Test
    @DisplayName("the happy path runs capture, validate, confirm, settle")
    void happyPath() {
        assertThat(TradeStatus.CAPTURED.canTransitionTo(TradeStatus.VALIDATED)).isTrue();
        assertThat(TradeStatus.VALIDATED.canTransitionTo(TradeStatus.CONFIRMED)).isTrue();
        assertThat(TradeStatus.CONFIRMED.canTransitionTo(TradeStatus.SETTLED)).isTrue();
    }

    @Test
    @DisplayName("a settled trade is finished: nothing can be done to it")
    void settledIsTerminal() {
        for (var target : TradeStatus.values()) {
            assertThat(TradeStatus.SETTLED.canTransitionTo(target))
                    .as("SETTLED -> %s", target)
                    .isFalse();
        }
        assertThat(TradeStatus.SETTLED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("a cancelled trade cannot be revived")
    void cancelledIsTerminal() {
        for (var target : TradeStatus.values()) {
            assertThat(TradeStatus.CANCELLED.canTransitionTo(target))
                    .as("CANCELLED -> %s", target)
                    .isFalse();
        }
        assertThat(TradeStatus.CANCELLED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("a trade cannot skip validation and go straight to confirmed")
    void cannotSkipValidation() {
        assertThat(TradeStatus.CAPTURED.canTransitionTo(TradeStatus.CONFIRMED)).isFalse();
        assertThat(TradeStatus.CAPTURED.canTransitionTo(TradeStatus.SETTLED)).isFalse();
    }

    @Test
    @DisplayName("a validated trade cannot settle without being confirmed")
    void cannotSettleWithoutConfirmation() {
        assertThat(TradeStatus.VALIDATED.canTransitionTo(TradeStatus.SETTLED)).isFalse();
    }

    @Test
    @DisplayName("amending a validated trade drops it back to captured for re-checking")
    void amendmentReturnsToCaptured() {
        assertThat(TradeStatus.VALIDATED.canTransitionTo(TradeStatus.CAPTURED)).isTrue();

        // ...but a confirmed trade is past the point where an amendment is a
        // correction rather than a renegotiation.
        assertThat(TradeStatus.CONFIRMED.canTransitionTo(TradeStatus.CAPTURED)).isFalse();
        assertThat(TradeStatus.CONFIRMED.isAmendable()).isFalse();
    }

    @Test
    @DisplayName("a confirmed trade can still be cancelled by agreement, up to settlement")
    void confirmedTradesCanStillBeCancelled() {
        assertThat(TradeStatus.CONFIRMED.canTransitionTo(TradeStatus.CANCELLED)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(TradeStatus.class)
    @DisplayName("every non-terminal status can be cancelled")
    void anythingUnfinishedCanBeCancelled(TradeStatus status) {
        assertThat(status.canTransitionTo(TradeStatus.CANCELLED))
                .isEqualTo(!status.isTerminal());
    }

    @Test
    @DisplayName("only unsettled, uncancelled trades carry risk")
    void liveMeansUnsettledAndUncancelled() {
        assertThat(TradeStatus.CAPTURED.isLive()).isTrue();
        assertThat(TradeStatus.VALIDATED.isLive()).isTrue();
        assertThat(TradeStatus.CONFIRMED.isLive()).isTrue();
        assertThat(TradeStatus.SETTLED.isLive()).isFalse();
        assertThat(TradeStatus.CANCELLED.isLive()).isFalse();
    }

    @Test
    void directionSignsAreOppositeAndCancelOut() {
        assertThat(Direction.BUY.sign()).isEqualTo(1);
        assertThat(Direction.SELL.sign()).isEqualTo(-1);
        assertThat(Direction.BUY.opposite()).isEqualTo(Direction.SELL);
    }
}
