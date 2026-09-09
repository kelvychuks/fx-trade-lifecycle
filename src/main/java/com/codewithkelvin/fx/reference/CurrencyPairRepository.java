package com.codewithkelvin.fx.reference;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CurrencyPairRepository extends JpaRepository<CurrencyPair, Long> {

    Optional<CurrencyPair> findBySymbol(String symbol);

    List<CurrencyPair> findByActiveTrueOrderBySymbol();
}
