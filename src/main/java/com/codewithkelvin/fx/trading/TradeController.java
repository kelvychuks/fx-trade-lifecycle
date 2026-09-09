package com.codewithkelvin.fx.trading;

import com.codewithkelvin.fx.trading.dto.AmendTradeRequest;
import com.codewithkelvin.fx.trading.dto.BookTradeRequest;
import com.codewithkelvin.fx.trading.dto.CancelTradeRequest;
import com.codewithkelvin.fx.trading.dto.TradeDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
@Tag(name = "Trades", description = "Capture and lifecycle")
public class TradeController {

    private final TradeService tradeService;

    @GetMapping
    @Operation(summary = "Blotter: every filter is optional")
    public Page<TradeDto> search(
            @RequestParam(required = false) TradeStatus status,
            @RequestParam(required = false) String pair,
            @RequestParam(required = false) String counterparty,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        var pageable = PageRequest.of(page, Math.min(size, 200),
                Sort.by(Sort.Direction.DESC, "capturedAt"));

        return tradeService.search(status, pair, counterparty, from, to, pageable)
                .map(TradeDto::from);
    }

    @GetMapping("/{tradeRef}")
    @Operation(summary = "One trade")
    public TradeDto get(@PathVariable String tradeRef) {
        return TradeDto.from(tradeService.require(tradeRef));
    }

    @GetMapping("/{tradeRef}/events")
    @Operation(summary = "The trade's full audit trail, oldest first")
    public List<TradeDto.TradeEventDto> events(@PathVariable String tradeRef) {
        return TradeDto.TradeEventDto.from(tradeService.auditTrail(tradeRef));
    }

    @PostMapping
    @Operation(summary = "Book a trade (TRADER)")
    public ResponseEntity<TradeDto> book(@Valid @RequestBody BookTradeRequest request,
                                         UriComponentsBuilder uriBuilder) {
        var trade = tradeService.book(request);
        var location = uriBuilder.path("/api/trades/{ref}")
                .buildAndExpand(trade.getTradeRef()).toUri();

        return ResponseEntity.created(location).body(TradeDto.from(trade));
    }

    @PostMapping("/{tradeRef}/validate")
    @Operation(summary = "Run the desk's checks (MIDDLE_OFFICE)")
    public TradeDto validate(@PathVariable String tradeRef) {
        return TradeDto.from(tradeService.validate(tradeRef));
    }

    @PostMapping("/{tradeRef}/confirm")
    @Operation(summary = "Confirm with the counterparty (MIDDLE_OFFICE, four-eyes)")
    public TradeDto confirm(@PathVariable String tradeRef) {
        return TradeDto.from(tradeService.confirm(tradeRef));
    }

    @PostMapping("/{tradeRef}/settle")
    @Operation(summary = "Settle on or after value date (MIDDLE_OFFICE)")
    public TradeDto settle(@PathVariable String tradeRef) {
        return TradeDto.from(tradeService.settle(tradeRef));
    }

    @PostMapping("/{tradeRef}/amend")
    @Operation(summary = "Amend an unconfirmed trade; sends it back for re-validation (TRADER)")
    public TradeDto amend(@PathVariable String tradeRef,
                          @Valid @RequestBody AmendTradeRequest request) {
        return TradeDto.from(tradeService.amend(tradeRef, request));
    }

    @PostMapping("/{tradeRef}/cancel")
    @Operation(summary = "Cancel a trade that has not settled; a reason is required")
    public TradeDto cancel(@PathVariable String tradeRef,
                           @Valid @RequestBody CancelTradeRequest request) {
        return TradeDto.from(tradeService.cancel(tradeRef, request.reason()));
    }
}
