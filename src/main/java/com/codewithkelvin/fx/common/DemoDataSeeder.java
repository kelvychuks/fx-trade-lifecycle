package com.codewithkelvin.fx.common;

import com.codewithkelvin.fx.marketdata.MarketDataService;
import com.codewithkelvin.fx.pricing.PricingService;
import com.codewithkelvin.fx.reference.CounterpartyRepository;
import com.codewithkelvin.fx.reference.CurrencyPairRepository;
import com.codewithkelvin.fx.security.AppUser;
import com.codewithkelvin.fx.security.AppUserRepository;
import com.codewithkelvin.fx.security.UserRole;
import com.codewithkelvin.fx.trading.Direction;
import com.codewithkelvin.fx.trading.TradeRepository;
import com.codewithkelvin.fx.trading.TradeService;
import com.codewithkelvin.fx.trading.dto.BookTradeRequest;
import com.codewithkelvin.fx.valuation.ValuationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Fills an empty database with a desk that looks like it has been running for a
 * fortnight: users, two weeks of market data, and a spread of trades across
 * every lifecycle state.
 * <p>
 * Two decisions worth explaining. First, market data is generated <em>relative
 * to today</em> rather than pinned to fixed dates in a migration, so the hosted
 * demo still shows a live blotter a year from now instead of an empty one.
 * Second, trades are booked through {@link TradeService} rather than inserted
 * straight into the tables — the seed data therefore obeys every rule the API
 * obeys, including four-eyes confirmation, and seeding doubles as a smoke test
 * of the whole booking path on every fresh deploy.
 */
@Slf4j
@Order(1)
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo.seed", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private static final String TRADER = "trader@fxdesk.dev";
    private static final String MIDDLE_OFFICE = "mo@fxdesk.dev";
    private static final String VIEWER = "viewer@fxdesk.dev";

    /** Indicative levels the random walk starts from. */
    private static final Map<String, BigDecimal> BASE_SPOTS = Map.of(
            "EURUSD", new BigDecimal("1.09250"),
            "GBPUSD", new BigDecimal("1.26400"),
            "USDJPY", new BigDecimal("152.400"),
            "USDCHF", new BigDecimal("0.88200"),
            "EURGBP", new BigDecimal("0.86400"),
            "USDNGN", new BigDecimal("1580.5000"),
            "USDZAR", new BigDecimal("18.24000"));

    /** Annualised deposit rates. The spread between two of these is a forward. */
    private static final Map<String, BigDecimal> DEPOSIT_RATES = Map.of(
            "USD", new BigDecimal("0.0425"),
            "EUR", new BigDecimal("0.0290"),
            "GBP", new BigDecimal("0.0450"),
            "JPY", new BigDecimal("0.0035"),
            "CHF", new BigDecimal("0.0120"),
            "NGN", new BigDecimal("0.2250"),
            "ZAR", new BigDecimal("0.0810"));

    private static final List<String> TENORS = List.of("SP", "SP", "W1", "M1", "M1", "M3", "M6", "Y1");
    private static final List<String> COUNTERPARTIES =
            List.of("MERIDIAN", "KESTREL", "NORTHGT", "ATLASCB", "HARMTTN");
    private static final List<String> BOOKS = List.of("FX-MAIN", "FX-CORP", "FX-PROP");

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrencyPairRepository pairRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final MarketDataService marketDataService;
    private final PricingService pricingService;
    private final TradeService tradeService;
    private final TradeRepository tradeRepository;
    private final ValuationService valuationService;

    /** Fixed seed: the demo looks the same on every deploy, which makes it debuggable. */
    private final Random random = new Random(20260908L);

    @Override
    public void run(ApplicationArguments args) {
        seedUsers();

        if (tradeRepository.count() > 0) {
            log.info("Trades already present, skipping demo seed");
            return;
        }

        pricingService.reloadCalendar();

        var today = LocalDate.now();
        seedMarketData(today);
        seedTrades(today);

        SecurityContextHolder.clearContext();

        var run = valuationService.runEndOfDay(today);
        log.info("Demo seed complete: {} trades, {} valued", tradeRepository.count(), run.tradesPriced());
    }

    // ------------------------------------------------------------------

    private void seedUsers() {
        createUser(TRADER, "Ada Okonjo", "Trader1234!", UserRole.TRADER);
        createUser(MIDDLE_OFFICE, "Ben Ilori", "Middle1234!", UserRole.MIDDLE_OFFICE);
        createUser(VIEWER, "Chi Adeyemi", "Viewer1234!", UserRole.VIEWER);
    }

    private void createUser(String username, String fullName, String password, UserRole role) {
        if (userRepository.existsByUsername(username)) {
            return;
        }

        var user = new AppUser();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        userRepository.save(user);

        log.info("Seeded demo user {} ({})", username, role);
    }

    /**
     * Fifteen business days of history, walking each spot by up to +/-0.35% a
     * day. Enough movement that marks are not all zero, not so much that the
     * numbers look absurd.
     */
    private void seedMarketData(LocalDate today) {
        var pairs = pairRepository.findByActiveTrueOrderBySymbol();

        for (var pair : pairs) {
            var spot = BASE_SPOTS.getOrDefault(pair.getSymbol(), new BigDecimal("1.00000"));
            var baseRate = DEPOSIT_RATES.getOrDefault(pair.getBaseCcy(), new BigDecimal("0.03"));
            var quoteRate = DEPOSIT_RATES.getOrDefault(pair.getQuoteCcy(), new BigDecimal("0.03"));

            var date = today.minusDays(25);
            while (!date.isAfter(today)) {
                if (isWeekday(date)) {
                    var drift = BigDecimal.valueOf((random.nextDouble() - 0.5) * 0.007);
                    spot = spot.add(spot.multiply(drift))
                            .setScale(pair.getRateScale() + 3, RoundingMode.HALF_UP);

                    marketDataService.upsert(pair.getSymbol(), date, spot, baseRate, quoteRate, "DEMO");
                }
                date = date.plusDays(1);
            }
        }

        log.info("Seeded market data for {} pairs", pairs.size());
    }

    private void seedTrades(LocalDate today) {
        var pairs = pairRepository.findByActiveTrueOrderBySymbol().stream()
                .map(pair -> pair.getSymbol())
                .toList();

        for (var i = 0; i < 42; i++) {
            var tradeDate = businessDayWithin(today, 12);

            var request = new BookTradeRequest(
                    pairs.get(random.nextInt(pairs.size())),
                    random.nextBoolean() ? Direction.BUY : Direction.SELL,
                    notional(),
                    COUNTERPARTIES.get(random.nextInt(COUNTERPARTIES.size())),
                    TENORS.get(random.nextInt(TENORS.size())),
                    null,
                    null,
                    tradeDate,
                    BOOKS.get(random.nextInt(BOOKS.size())),
                    "SEED-" + i);

            try {
                actAs(TRADER, UserRole.TRADER);
                var trade = tradeService.book(request);
                progress(trade.getTradeRef(), today);
            } catch (RuntimeException ex) {
                log.warn("Skipped seed trade {}: {}", i, ex.getMessage());
            }
        }
    }

    /** Pushes each trade a random distance down the lifecycle. */
    private void progress(String tradeRef, LocalDate today) {
        var roll = random.nextInt(100);

        if (roll < 15) {
            return; // stays CAPTURED, waiting for middle office
        }

        if (roll < 25) {
            actAs(TRADER, UserRole.TRADER);
            tradeService.cancel(tradeRef, "Cancelled by mutual agreement with the counterparty");
            return;
        }

        actAs(MIDDLE_OFFICE, UserRole.MIDDLE_OFFICE);
        tradeService.validate(tradeRef);
        if (roll < 45) {
            return; // VALIDATED, awaiting confirmation
        }

        tradeService.confirm(tradeRef);
        if (roll < 80) {
            return; // CONFIRMED, awaiting settlement
        }

        var trade = tradeService.require(tradeRef);
        if (trade.isMatured(today)) {
            tradeService.settle(tradeRef);
        }
    }

    /**
     * Booking runs through the real service, which enforces roles — so the
     * seeder authenticates as the user whose job each step is.
     */
    private void actAs(String username, UserRole role) {
        var authentication = new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private BigDecimal notional() {
        var lots = new int[]{250_000, 500_000, 750_000, 1_000_000, 1_500_000, 2_000_000, 3_500_000};
        return BigDecimal.valueOf(lots[random.nextInt(lots.length)]).setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate businessDayWithin(LocalDate today, int maxDaysBack) {
        var date = today.minusDays(random.nextInt(maxDaysBack));
        while (!isWeekday(date)) {
            date = date.minusDays(1);
        }
        return date;
    }

    private static boolean isWeekday(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}
