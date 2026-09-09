package com.codewithkelvin.fx.trading;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/** Composable blotter filters. Each returns null when the filter is not set. */
public final class TradeSpecifications {

    private TradeSpecifications() {
    }

    public static Specification<Trade> hasStatus(TradeStatus status) {
        return status == null ? null
                : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Trade> hasPair(String symbol) {
        return symbol == null || symbol.isBlank() ? null
                : (root, query, cb) -> cb.equal(root.get("pair").get("symbol"), symbol.toUpperCase());
    }

    public static Specification<Trade> hasCounterparty(String code) {
        return code == null || code.isBlank() ? null
                : (root, query, cb) -> cb.equal(root.get("counterparty").get("code"), code.toUpperCase());
    }

    public static Specification<Trade> tradedFrom(LocalDate from) {
        return from == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("tradeDate"), from);
    }

    public static Specification<Trade> tradedTo(LocalDate to) {
        return to == null ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("tradeDate"), to);
    }

    public static Specification<Trade> valueDateFrom(LocalDate from) {
        return from == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("valueDate"), from);
    }

    public static Specification<Trade> valueDateTo(LocalDate to) {
        return to == null ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("valueDate"), to);
    }
}
