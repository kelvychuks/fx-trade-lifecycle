package com.codewithkelvin.fx.reference;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Everything a booking screen needs to populate its dropdowns. */
@RestController
@RequestMapping("/api/reference")
@RequiredArgsConstructor
@Tag(name = "Reference data")
public class ReferenceController {

    private final CurrencyPairRepository pairRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final HolidayRepository holidayRepository;

    @GetMapping("/currency-pairs")
    @Operation(summary = "Tradeable pairs")
    public List<CurrencyPairDto> currencyPairs() {
        return pairRepository.findByActiveTrueOrderBySymbol().stream()
                .map(CurrencyPairDto::from)
                .toList();
    }

    @GetMapping("/counterparties")
    @Operation(summary = "Counterparties, including inactive ones")
    public List<CounterpartyDto> counterparties() {
        return counterpartyRepository.findAllByOrderByCode().stream()
                .map(CounterpartyDto::from)
                .toList();
    }

    @GetMapping("/holidays")
    @Operation(summary = "Settlement holidays, optionally for one currency")
    public List<HolidayDto> holidays(@RequestParam(required = false) String currency) {
        var holidays = currency == null
                ? holidayRepository.findAll()
                : holidayRepository.findByCurrencyCodeInOrderByHolidayDate(
                        List.of(currency.toUpperCase()));

        return holidays.stream()
                .sorted((a, b) -> a.getHolidayDate().compareTo(b.getHolidayDate()))
                .map(HolidayDto::from)
                .toList();
    }

    public record CurrencyPairDto(String symbol, String baseCcy, String quoteCcy,
                                  int spotLagDays, int pipFactor, int rateScale) {
        static CurrencyPairDto from(CurrencyPair pair) {
            return new CurrencyPairDto(pair.getSymbol(), pair.getBaseCcy(), pair.getQuoteCcy(),
                    pair.getSpotLagDays(), pair.getPipFactor(), pair.getRateScale());
        }
    }

    public record CounterpartyDto(String code, String name, String country,
                                  BigDecimal tradeLimit, boolean active) {
        static CounterpartyDto from(Counterparty counterparty) {
            return new CounterpartyDto(counterparty.getCode(), counterparty.getName(),
                    counterparty.getCountry(), counterparty.getTradeLimit(), counterparty.isActive());
        }
    }

    public record HolidayDto(String currency, LocalDate date, String description) {
        static HolidayDto from(Holiday holiday) {
            return new HolidayDto(holiday.getCurrencyCode(), holiday.getHolidayDate(),
                    holiday.getDescription());
        }
    }
}
