package com.codewithkelvin.fx.positions;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/positions")
@RequiredArgsConstructor
@Tag(name = "Positions", description = "What the desk is long and short")
public class PositionController {

    private final PositionService positionService;

    @GetMapping
    @Operation(summary = "Net position per currency across every live trade")
    public List<PositionService.CurrencyPosition> byCurrency(
            @RequestParam(defaultValue = "false") boolean includeSettled) {
        return positionService.currencyPositions(includeSettled);
    }

    @GetMapping("/counterparties")
    @Operation(summary = "Exposure per counterparty, with limit utilisation")
    public List<PositionService.CounterpartyExposure> byCounterparty(
            @RequestParam(defaultValue = "false") boolean includeSettled) {
        return positionService.counterpartyExposures(includeSettled);
    }
}
