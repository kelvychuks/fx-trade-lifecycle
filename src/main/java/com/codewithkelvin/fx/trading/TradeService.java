package com.codewithkelvin.fx.trading;

import com.codewithkelvin.fx.common.BusinessRuleException;
import com.codewithkelvin.fx.common.NotFoundException;
import com.codewithkelvin.fx.pricing.ForwardPricer;
import com.codewithkelvin.fx.pricing.PricingService;
import com.codewithkelvin.fx.pricing.Tenor;
import com.codewithkelvin.fx.reference.Counterparty;
import com.codewithkelvin.fx.reference.CounterpartyRepository;
import com.codewithkelvin.fx.reference.CurrencyPair;
import com.codewithkelvin.fx.reference.CurrencyPairRepository;
import com.codewithkelvin.fx.security.AppUser;
import com.codewithkelvin.fx.security.CurrentUserService;
import com.codewithkelvin.fx.trading.dto.AmendTradeRequest;
import com.codewithkelvin.fx.trading.dto.BookTradeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

/**
 * Everything a trade can have done to it.
 * <p>
 * Three ideas hold this class together:
 * <ul>
 *   <li><b>One gate for every state change.</b> Nothing sets {@code status}
 *       directly; every change goes through {@link #transition}, which checks
 *       {@link TradeStatus#canTransitionTo} and writes an audit event. An
 *       illegal transition is impossible to perform by accident.</li>
 *   <li><b>Rules where the rule lives.</b> Who may do what is a
 *       {@code @PreAuthorize} on the method it protects, not a URL pattern in a
 *       config class far away.</li>
 *   <li><b>Validation is re-run after amendment.</b> Changing the economics of
 *       a validated trade drops it back to CAPTURED, because a validation
 *       performed against different terms is worthless.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeService {

    private final TradeRepository tradeRepository;
    private final TradeEventRepository eventRepository;
    private final CurrencyPairRepository pairRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final PricingService pricingService;
    private final CurrentUserService currentUserService;

    /**
     * How far a manually entered rate may sit from the market before the desk
     * refuses it. An off-market rate is how a loss gets hidden inside a trade,
     * so it is a rejection, not a warning.
     */
    @Value("${app.trading.off-market-tolerance:0.05}")
    private BigDecimal offMarketTolerance;

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Trade require(String tradeRef) {
        return tradeRepository.findByTradeRef(tradeRef)
                .orElseThrow(() -> NotFoundException.of("Trade", tradeRef));
    }

    @Transactional(readOnly = true)
    public Page<Trade> search(TradeStatus status, String pairSymbol, String counterpartyCode,
                              LocalDate from, LocalDate to, Pageable pageable) {
        Specification<Trade> spec = (root, query, cb) -> cb.conjunction();
        spec = spec.and(TradeSpecifications.hasStatus(status))
                .and(TradeSpecifications.hasPair(pairSymbol))
                .and(TradeSpecifications.hasCounterparty(counterpartyCode))
                .and(TradeSpecifications.tradedFrom(from))
                .and(TradeSpecifications.tradedTo(to));

        return tradeRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public List<TradeEvent> auditTrail(String tradeRef) {
        return eventRepository.findByTradeIdOrderBySequenceNo(require(tradeRef).getId());
    }

    @Transactional(readOnly = true)
    public List<Trade> liveTrades() {
        return tradeRepository.findByStatusInOrderByValueDate(
                EnumSet.of(TradeStatus.CAPTURED, TradeStatus.VALIDATED, TradeStatus.CONFIRMED));
    }

    // ------------------------------------------------------------------
    // Booking
    // ------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasRole('TRADER')")
    public Trade book(BookTradeRequest request) {
        // Idempotency first: a retried request returns the original trade
        // rather than booking a second one.
        if (request.externalRef() != null && !request.externalRef().isBlank()) {
            var existing = tradeRepository.findByExternalRef(request.externalRef());
            if (existing.isPresent()) {
                log.info("Booking request {} already produced trade {}",
                        request.externalRef(), existing.get().getTradeRef());
                return existing.get();
            }
        }

        var trader = currentUserService.require();
        var pair = requireActivePair(request.pair());
        var counterparty = requireActiveCounterparty(request.counterparty());
        var tenor = Tenor.parse(request.tenor());
        var tradeDate = request.tradeDate() == null ? LocalDate.now() : request.tradeDate();

        var valueDate = resolveValueDate(pair, tradeDate, tenor, request.valueDate());
        var product = valueDate.equals(pricingService.spotDate(pair, tradeDate))
                ? Product.SPOT
                : Product.FORWARD;

        var marketRate = pricingService.rateFor(pair, tradeDate, valueDate);
        var rate = resolveRate(request.rate(), marketRate, pair);

        checkNotional(request.notional(), counterparty, pair);

        var trade = new Trade();
        trade.setTradeRef(nextTradeRef(tradeDate));
        trade.setExternalRef(emptyToNull(request.externalRef()));
        trade.setPair(pair);
        trade.setCounterparty(counterparty);
        trade.setProduct(product);
        trade.setDirection(request.direction());
        trade.setTenor(tenor.name());
        trade.setNotional(request.notional().setScale(2, RoundingMode.HALF_UP));
        trade.setRate(rate);
        trade.setCounterAmount(ForwardPricer.counterAmount(
                request.notional(), rate, pricingService.quoteMinorUnits(pair)));
        trade.setTradeDate(tradeDate);
        trade.setValueDate(valueDate);
        trade.setBook(request.book() == null || request.book().isBlank() ? "FX-MAIN" : request.book());
        trade.setStatus(TradeStatus.CAPTURED);
        trade.setCapturedBy(trader);

        var saved = tradeRepository.save(trade);

        recordEvent(saved, TradeEventType.CAPTURE, null, TradeStatus.CAPTURED, trader,
                "%s %s %s %s @ %s value %s".formatted(
                        request.direction(), pair.getSymbol(), trade.getNotional().toPlainString(),
                        pair.getBaseCcy(), rate.toPlainString(), valueDate));

        return saved;
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Middle office checks the trade against the desk's rules. Everything that
     * could have gone stale between capture and now is re-checked: a
     * counterparty can be suspended in the minutes after a trade is booked.
     */
    @Transactional
    @PreAuthorize("hasRole('MIDDLE_OFFICE')")
    public Trade validate(String tradeRef) {
        var trade = require(tradeRef);
        var actor = currentUserService.require();

        if (!trade.getCounterparty().isActive()) {
            throw new BusinessRuleException("COUNTERPARTY_INACTIVE",
                    "Counterparty " + trade.getCounterparty().getCode() + " is not active");
        }
        if (!trade.getPair().isActive()) {
            throw new BusinessRuleException("PAIR_INACTIVE",
                    trade.getPair().getSymbol() + " is no longer tradeable");
        }
        if (!pricingService.isSettlementDay(trade.getPair(), trade.getValueDate())) {
            throw new BusinessRuleException("BAD_VALUE_DATE",
                    "Value date " + trade.getValueDate() + " is not a settlement day for "
                            + trade.getPair().getSymbol());
        }
        checkNotional(trade.getNotional(), trade.getCounterparty(), trade.getPair());

        return transition(trade, TradeEventType.VALIDATE, TradeStatus.VALIDATED, actor,
                "Checks passed: counterparty active, value date good, within limit");
    }

    /**
     * Four-eyes: whoever booked the trade cannot be the one who confirms it.
     * This is the whole reason the roles exist, so it is enforced here rather
     * than assumed of the user interface.
     */
    @Transactional
    @PreAuthorize("hasRole('MIDDLE_OFFICE')")
    public Trade confirm(String tradeRef) {
        var trade = require(tradeRef);
        var actor = currentUserService.require();

        if (trade.getCapturedBy().getId().equals(actor.getId())) {
            throw new BusinessRuleException("FOUR_EYES_VIOLATION",
                    "A trade cannot be confirmed by the person who booked it");
        }

        trade.setConfirmedBy(actor);
        return transition(trade, TradeEventType.CONFIRM, TradeStatus.CONFIRMED, actor,
                "Confirmed with " + trade.getCounterparty().getName());
    }

    /** Settlement is a fact about a date, not a decision. */
    @Transactional
    @PreAuthorize("hasRole('MIDDLE_OFFICE')")
    public Trade settle(String tradeRef) {
        var trade = require(tradeRef);
        var actor = currentUserService.require();

        if (!trade.isMatured(LocalDate.now())) {
            throw new BusinessRuleException("NOT_YET_DUE",
                    "Trade settles on " + trade.getValueDate() + "; it cannot settle before then");
        }

        return transition(trade, TradeEventType.SETTLE, TradeStatus.SETTLED, actor,
                "Settled %s %s against %s %s".formatted(
                        trade.getNotional().toPlainString(), trade.getPair().getBaseCcy(),
                        trade.getCounterAmount().toPlainString(), trade.getPair().getQuoteCcy()));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('TRADER', 'MIDDLE_OFFICE')")
    public Trade cancel(String tradeRef, String reason) {
        var trade = require(tradeRef);
        var actor = currentUserService.require();

        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("REASON_REQUIRED",
                    "Cancelling a trade requires a reason");
        }

        trade.setCancelReason(reason);
        return transition(trade, TradeEventType.CANCEL, TradeStatus.CANCELLED, actor, reason);
    }

    /**
     * Amendment re-prices and re-dates the trade, then sends it back to
     * CAPTURED so validation runs again against the new terms.
     */
    @Transactional
    @PreAuthorize("hasRole('TRADER')")
    public Trade amend(String tradeRef, AmendTradeRequest request) {
        var trade = require(tradeRef);
        var actor = currentUserService.require();

        if (!trade.getStatus().isAmendable()) {
            throw new BusinessRuleException("NOT_AMENDABLE",
                    "A " + trade.getStatus() + " trade cannot be amended");
        }

        var before = "%s @ %s value %s".formatted(
                trade.getNotional().toPlainString(), trade.getRate().toPlainString(),
                trade.getValueDate());

        if (request.notional() != null) {
            checkNotional(request.notional(), trade.getCounterparty(), trade.getPair());
            trade.setNotional(request.notional().setScale(2, RoundingMode.HALF_UP));
        }

        if (request.tenor() != null || request.valueDate() != null) {
            var tenor = Tenor.parse(request.tenor() == null ? trade.getTenor() : request.tenor());
            var valueDate = resolveValueDate(trade.getPair(), trade.getTradeDate(), tenor,
                    request.valueDate());
            trade.setTenor(tenor.name());
            trade.setValueDate(valueDate);
            trade.setProduct(valueDate.equals(pricingService.spotDate(trade.getPair(), trade.getTradeDate()))
                    ? Product.SPOT : Product.FORWARD);
        }

        // Re-price against the market as of the original trade date: an
        // amendment corrects a booking, it does not re-trade at today's rate.
        var marketRate = pricingService.rateFor(trade.getPair(), trade.getTradeDate(),
                trade.getValueDate());
        trade.setRate(resolveRate(request.rate(), marketRate, trade.getPair()));
        trade.setCounterAmount(ForwardPricer.counterAmount(
                trade.getNotional(), trade.getRate(), pricingService.quoteMinorUnits(trade.getPair())));

        var after = "%s @ %s value %s".formatted(
                trade.getNotional().toPlainString(), trade.getRate().toPlainString(),
                trade.getValueDate());

        return transition(trade, TradeEventType.AMEND, TradeStatus.CAPTURED, actor,
                "Amended from [" + before + "] to [" + after + "]"
                        + (request.reason() == null ? "" : " — " + request.reason()));
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** The single gate. Every status change in the system passes through here. */
    private Trade transition(Trade trade, TradeEventType eventType, TradeStatus target,
                             AppUser actor, String detail) {
        var current = trade.getStatus();

        if (!current.canTransitionTo(target)) {
            throw new BusinessRuleException("ILLEGAL_TRANSITION",
                    "Cannot %s a trade that is %s".formatted(
                            eventType.name().toLowerCase(), current));
        }

        trade.setStatus(target);
        var saved = tradeRepository.save(trade);
        recordEvent(saved, eventType, current, target, actor, detail);
        return saved;
    }

    private void recordEvent(Trade trade, TradeEventType type, TradeStatus from,
                             TradeStatus to, AppUser actor, String detail) {
        var event = new TradeEvent();
        event.setTrade(trade);
        event.setSequenceNo(eventRepository.countByTradeId(trade.getId()) + 1);
        event.setEventType(type);
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setActor(actor.getUsername());
        event.setDetail(truncate(detail));
        eventRepository.save(event);
    }

    private LocalDate resolveValueDate(CurrencyPair pair, LocalDate tradeDate, Tenor tenor,
                                       LocalDate requested) {
        if (requested == null) {
            return pricingService.valueDate(pair, tradeDate, tenor);
        }

        // A "broken date" — any date the client asks for that is not a standard
        // tenor. Legitimate, and still has to be a day the money can move.
        if (!pricingService.isSettlementDay(pair, requested)) {
            throw new BusinessRuleException("BAD_VALUE_DATE",
                    requested + " is a weekend or a holiday for " + pair.getSymbol());
        }

        var spot = pricingService.spotDate(pair, tradeDate);
        if (requested.isBefore(spot)) {
            throw new BusinessRuleException("BEFORE_SPOT",
                    "Value date " + requested + " is before spot (" + spot + ")");
        }

        return requested;
    }

    private BigDecimal resolveRate(BigDecimal requested, BigDecimal marketRate, CurrencyPair pair) {
        if (requested == null) {
            return marketRate;
        }
        if (requested.signum() <= 0) {
            throw new BusinessRuleException("INVALID_RATE", "Rate must be positive");
        }

        var deviation = requested.subtract(marketRate).abs()
                .divide(marketRate, 6, RoundingMode.HALF_UP);

        if (deviation.compareTo(offMarketTolerance) > 0) {
            throw new BusinessRuleException("OFF_MARKET_RATE",
                    "Rate %s is %s%% away from the market rate of %s, beyond the %s%% tolerance"
                            .formatted(requested.toPlainString(),
                                    deviation.multiply(BigDecimal.valueOf(100))
                                            .setScale(2, RoundingMode.HALF_UP).toPlainString(),
                                    marketRate.toPlainString(),
                                    offMarketTolerance.multiply(BigDecimal.valueOf(100))
                                            .stripTrailingZeros().toPlainString()));
        }

        return ForwardPricer.roundRate(requested, pair.getRateScale());
    }

    private void checkNotional(BigDecimal notional, Counterparty counterparty, CurrencyPair pair) {
        if (notional == null || notional.signum() <= 0) {
            throw new BusinessRuleException("INVALID_NOTIONAL", "Notional must be greater than zero");
        }
        if (counterparty.getTradeLimit() != null
                && notional.compareTo(counterparty.getTradeLimit()) > 0) {
            throw new BusinessRuleException("LIMIT_BREACH",
                    "Notional %s %s exceeds the %s limit of %s".formatted(
                            notional.toPlainString(), pair.getBaseCcy(),
                            counterparty.getCode(), counterparty.getTradeLimit().toPlainString()));
        }
    }

    private CurrencyPair requireActivePair(String symbol) {
        var pair = pairRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> NotFoundException.of("Currency pair", symbol));
        if (!pair.isActive()) {
            throw new BusinessRuleException("PAIR_INACTIVE", symbol + " is not tradeable");
        }
        return pair;
    }

    private Counterparty requireActiveCounterparty(String code) {
        var counterparty = counterpartyRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> NotFoundException.of("Counterparty", code));
        if (!counterparty.isActive()) {
            throw new BusinessRuleException("COUNTERPARTY_INACTIVE",
                    counterparty.getName() + " is not an active counterparty");
        }
        return counterparty;
    }

    private String nextTradeRef(LocalDate tradeDate) {
        return "FX-%d-%06d".formatted(tradeDate.getYear(), tradeRepository.nextTradeRefSequence());
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= 500 ? detail : detail.substring(0, 497) + "...";
    }
}
