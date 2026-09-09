package com.codewithkelvin.fx.trading;

import com.codewithkelvin.fx.DockerAvailable;
import com.codewithkelvin.fx.TestcontainersConfiguration;
import com.codewithkelvin.fx.common.BusinessRuleException;
import com.codewithkelvin.fx.security.AppUser;
import com.codewithkelvin.fx.security.AppUserRepository;
import com.codewithkelvin.fx.security.UserRole;
import com.codewithkelvin.fx.trading.dto.AmendTradeRequest;
import com.codewithkelvin.fx.trading.dto.BookTradeRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The lifecycle end to end, against a real database and through the real
 * service — including the authorisation rules, which are the part most likely
 * to be quietly broken by a refactor.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@EnabledIf("com.codewithkelvin.fx.DockerAvailable#check")
class TradeLifecycleIntegrationTest {

    private static final String TRADER = "trader@fxdesk.dev";
    private static final String MIDDLE_OFFICE = "mo@fxdesk.dev";

    @Autowired
    private TradeService tradeService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("a trade walks capture -> validate -> confirm, recording who did what")
    void happyPath() {
        asTrader();
        var trade = tradeService.book(request("EURUSD", Direction.BUY, "1000000.00",
                "MERIDIAN", "M3"));

        assertThat(trade.getTradeRef()).startsWith("FX-");
        assertThat(trade.getStatus()).isEqualTo(TradeStatus.CAPTURED);
        assertThat(trade.getProduct()).isEqualTo(Product.FORWARD);
        assertThat(trade.getCapturedBy().getUsername()).isEqualTo(TRADER);
        assertThat(trade.getCounterAmount()).isPositive();
        assertThat(trade.getValueDate()).isAfter(trade.getTradeDate());

        asMiddleOffice();
        assertThat(tradeService.validate(trade.getTradeRef()).getStatus())
                .isEqualTo(TradeStatus.VALIDATED);

        var confirmed = tradeService.confirm(trade.getTradeRef());
        assertThat(confirmed.getStatus()).isEqualTo(TradeStatus.CONFIRMED);
        assertThat(confirmed.getConfirmedBy().getUsername()).isEqualTo(MIDDLE_OFFICE);

        var events = tradeService.auditTrail(trade.getTradeRef());
        assertThat(events).hasSize(3);
        assertThat(events).extracting(TradeEvent::getEventType).containsExactly(
                TradeEventType.CAPTURE, TradeEventType.VALIDATE, TradeEventType.CONFIRM);
        assertThat(events).extracting(TradeEvent::getSequenceNo).containsExactly(1, 2, 3);
        assertThat(events.get(2).getActor()).isEqualTo(MIDDLE_OFFICE);
    }

    @Test
    @DisplayName("a trade cannot be confirmed before it has been validated")
    void cannotSkipValidation() {
        asTrader();
        var trade = tradeService.book(request("GBPUSD", Direction.SELL, "500000.00",
                "KESTREL", "M1"));

        asMiddleOffice();
        assertThatThrownBy(() -> tradeService.confirm(trade.getTradeRef()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CAPTURED");
    }

    @Test
    @DisplayName("a forward cannot settle before its value date")
    void cannotSettleEarly() {
        asTrader();
        var trade = tradeService.book(request("EURUSD", Direction.BUY, "750000.00",
                "MERIDIAN", "M6"));

        asMiddleOffice();
        tradeService.validate(trade.getTradeRef());
        tradeService.confirm(trade.getTradeRef());

        assertThatThrownBy(() -> tradeService.settle(trade.getTradeRef()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot settle before");
    }

    @Test
    @DisplayName("the person who booked a trade cannot confirm it, even with the right role")
    void fourEyesRule() {
        // Simulates someone who booked a trade and later moved to middle
        // office: the roles alone would let them confirm their own trade.
        var dualHatted = createUser("moved-desk-" + UUID.randomUUID() + "@fxdesk.dev",
                UserRole.MIDDLE_OFFICE);

        authenticate(dualHatted.getUsername(), UserRole.TRADER);
        var trade = tradeService.book(request("EURUSD", Direction.BUY, "250000.00",
                "MERIDIAN", "SP"));

        authenticate(dualHatted.getUsername(), UserRole.MIDDLE_OFFICE);
        tradeService.validate(trade.getTradeRef());

        assertThatThrownBy(() -> tradeService.confirm(trade.getTradeRef()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be confirmed by the person who booked it");
    }

    @Test
    @DisplayName("a trader cannot confirm and middle office cannot book")
    void rolesAreEnforced() {
        asTrader();
        var trade = tradeService.book(request("EURUSD", Direction.BUY, "250000.00",
                "MERIDIAN", "SP"));

        assertThatThrownBy(() -> tradeService.validate(trade.getTradeRef()))
                .isInstanceOf(AccessDeniedException.class);

        asMiddleOffice();
        assertThatThrownBy(() -> tradeService.book(
                request("EURUSD", Direction.BUY, "250000.00", "MERIDIAN", "SP")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("replaying a booking request returns the original trade")
    void bookingIsIdempotent() {
        asTrader();
        var externalRef = "IDEMPOTENCY-" + UUID.randomUUID();

        var first = tradeService.book(new BookTradeRequest("EURUSD", Direction.BUY,
                new BigDecimal("1000000.00"), "MERIDIAN", "M1", null, null, null, "FX-MAIN",
                externalRef));
        var second = tradeService.book(new BookTradeRequest("EURUSD", Direction.BUY,
                new BigDecimal("1000000.00"), "MERIDIAN", "M1", null, null, null, "FX-MAIN",
                externalRef));

        assertThat(second.getTradeRef()).isEqualTo(first.getTradeRef());
        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("a rate far from the market is rejected rather than booked")
    void offMarketRatesAreRejected() {
        asTrader();

        assertThatThrownBy(() -> tradeService.book(new BookTradeRequest("EURUSD", Direction.BUY,
                new BigDecimal("1000000.00"), "MERIDIAN", "SP", null,
                new BigDecimal("0.50000"), null, "FX-MAIN", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("away from the market rate");
    }

    @Test
    @DisplayName("a notional above the counterparty's limit is rejected")
    void limitBreachesAreRejected() {
        asTrader();

        // Atlas Commercial Bank is limited to 5,000,000 per trade.
        assertThatThrownBy(() -> tradeService.book(request("EURUSD", Direction.BUY,
                "6000000.00", "ATLASCB", "SP")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    @DisplayName("an inactive counterparty cannot be traded with")
    void inactiveCounterpartiesAreRejected() {
        asTrader();

        assertThatThrownBy(() -> tradeService.book(request("EURUSD", Direction.BUY,
                "100000.00", "DELISTED", "SP")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not an active counterparty");
    }

    @Test
    @DisplayName("amending a validated trade re-prices it and sends it back for re-validation")
    void amendmentResetsValidation() {
        asTrader();
        var trade = tradeService.book(request("EURUSD", Direction.BUY, "1000000.00",
                "MERIDIAN", "M1"));

        asMiddleOffice();
        tradeService.validate(trade.getTradeRef());

        asTrader();
        var amended = tradeService.amend(trade.getTradeRef(),
                new AmendTradeRequest(new BigDecimal("2000000.00"), "M3", null, null,
                        "Client increased the hedge"));

        assertThat(amended.getStatus()).isEqualTo(TradeStatus.CAPTURED);
        assertThat(amended.getNotional()).isEqualByComparingTo("2000000.00");
        assertThat(amended.getTenor()).isEqualTo("M3");
        assertThat(amended.getCounterAmount())
                .isEqualByComparingTo(amended.getNotional().multiply(amended.getRate())
                        .setScale(2, java.math.RoundingMode.HALF_UP));

        var events = tradeService.auditTrail(trade.getTradeRef());
        assertThat(events).extracting(TradeEvent::getEventType)
                .containsExactly(TradeEventType.CAPTURE, TradeEventType.VALIDATE, TradeEventType.AMEND);
    }

    @Test
    @DisplayName("cancelling requires a reason, and closes the trade for good")
    void cancellationRequiresAReasonAndIsFinal() {
        asTrader();
        var trade = tradeService.book(request("USDJPY", Direction.SELL, "500000.00",
                "NORTHGT", "SP"));

        assertThatThrownBy(() -> tradeService.cancel(trade.getTradeRef(), "  "))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("requires a reason");

        var cancelled = tradeService.cancel(trade.getTradeRef(), "Booked against the wrong client");
        assertThat(cancelled.getStatus()).isEqualTo(TradeStatus.CANCELLED);
        assertThat(cancelled.getCancelReason()).isEqualTo("Booked against the wrong client");

        asMiddleOffice();
        assertThatThrownBy(() -> tradeService.validate(trade.getTradeRef()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CANCELLED");
    }

    @Test
    @DisplayName("a spot trade settles on the spot date, a forward does not")
    void productIsDerivedFromTheValueDate() {
        asTrader();

        var spot = tradeService.book(request("EURUSD", Direction.BUY, "100000.00",
                "MERIDIAN", "SP"));
        var forward = tradeService.book(request("EURUSD", Direction.BUY, "100000.00",
                "MERIDIAN", "M3"));

        assertThat(spot.getProduct()).isEqualTo(Product.SPOT);
        assertThat(forward.getProduct()).isEqualTo(Product.FORWARD);
        assertThat(forward.getValueDate()).isAfter(spot.getValueDate());

        // A forward on a premium currency prices away from spot.
        assertThat(forward.getRate()).isNotEqualByComparingTo(spot.getRate());
    }

    // ------------------------------------------------------------------

    private BookTradeRequest request(String pair, Direction direction, String notional,
                                     String counterparty, String tenor) {
        return new BookTradeRequest(pair, direction, new BigDecimal(notional), counterparty,
                tenor, null, null, LocalDate.now(), "FX-MAIN", null);
    }

    private AppUser createUser(String username, UserRole role) {
        var user = new AppUser();
        user.setUsername(username);
        user.setFullName("Test User");
        user.setPasswordHash(passwordEncoder.encode("Test1234!"));
        user.setRole(role);
        return userRepository.save(user);
    }

    private void asTrader() {
        authenticate(TRADER, UserRole.TRADER);
    }

    private void asMiddleOffice() {
        authenticate(MIDDLE_OFFICE, UserRole.MIDDLE_OFFICE);
    }

    private void authenticate(String username, UserRole role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
}
