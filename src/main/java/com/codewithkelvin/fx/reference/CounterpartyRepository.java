package com.codewithkelvin.fx.reference;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CounterpartyRepository extends JpaRepository<Counterparty, Long> {

    Optional<Counterparty> findByCode(String code);

    List<Counterparty> findAllByOrderByCode();
}
