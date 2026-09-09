package com.codewithkelvin.fx.marketdata;

import com.codewithkelvin.fx.common.BusinessRuleException;
import com.codewithkelvin.fx.common.NotFoundException;
import com.codewithkelvin.fx.reference.CurrencyPair;
import com.codewithkelvin.fx.reference.CurrencyPairRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final FxRateRepository rateRepository;
    private final CurrencyPairRepository pairRepository;

    @Transactional(readOnly = true)
    public CurrencyPair requirePair(String symbol) {
        return pairRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> NotFoundException.of("Currency pair", symbol));
    }

    /**
     * Latest snapshot on or before {@code asOf}. Refusing to price without
     * market data is deliberate: a desk that quietly prices off a stale or
     * missing rate is worse than one that says no.
     */
    @Transactional(readOnly = true)
    public FxRate requireRate(CurrencyPair pair, LocalDate asOf) {
        return rateRepository
                .findFirstByPairIdAndRateDateLessThanEqualOrderByRateDateDesc(pair.getId(), asOf)
                .orElseThrow(() -> new BusinessRuleException(
                        "NO_MARKET_DATA",
                        "No market data for " + pair.getSymbol() + " on or before " + asOf));
    }

    @Transactional(readOnly = true)
    public List<FxRate> ratesOn(LocalDate date) {
        return rateRepository.findByRateDateOrderByPairSymbol(date);
    }

    @Transactional(readOnly = true)
    public LocalDate latestRateDate() {
        return rateRepository.findFirstByOrderByRateDateDesc()
                .map(FxRate::getRateDate)
                .orElse(LocalDate.now());
    }

    /** Insert or replace a day's snapshot. Loading the same day twice is safe. */
    @Transactional
    public FxRate upsert(String symbol, LocalDate rateDate, BigDecimal spotMid,
                         BigDecimal baseRate, BigDecimal quoteRate, String source) {
        var pair = requirePair(symbol);

        if (spotMid == null || spotMid.signum() <= 0) {
            throw new BusinessRuleException("INVALID_RATE", "Spot rate must be positive");
        }

        var rate = rateRepository.findByPairIdAndRateDate(pair.getId(), rateDate)
                .orElseGet(() -> {
                    var created = new FxRate();
                    created.setPair(pair);
                    created.setRateDate(rateDate);
                    return created;
                });

        rate.setSpotMid(spotMid);
        rate.setBaseRate(baseRate);
        rate.setQuoteRate(quoteRate);
        rate.setSource(source == null ? "MANUAL" : source);

        return rateRepository.save(rate);
    }
}
