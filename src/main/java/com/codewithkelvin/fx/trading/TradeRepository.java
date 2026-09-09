package com.codewithkelvin.fx.trading;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Blotter filtering goes through {@link JpaSpecificationExecutor} rather than a
 * JPQL query full of {@code (:param is null or ...)} clauses: every filter is
 * optional, and composing predicates is both easier to read and easier for the
 * database to plan than one query with six dead branches in it.
 */
public interface TradeRepository extends JpaRepository<Trade, Long>, JpaSpecificationExecutor<Trade> {

    Optional<Trade> findByTradeRef(String tradeRef);

    Optional<Trade> findByExternalRef(String externalRef);

    List<Trade> findByStatusInOrderByValueDate(Collection<TradeStatus> statuses);

    /** The settlement queue: confirmed trades whose value date has arrived. */
    List<Trade> findByStatusAndValueDateLessThanEqualOrderByValueDate(
            TradeStatus status, LocalDate asOf);

    long countByStatus(TradeStatus status);

    @Query(value = "select nextval('trade_ref_seq')", nativeQuery = true)
    Long nextTradeRefSequence();
}
