package com.codewithkelvin.fx.reference;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    List<Holiday> findByCurrencyCodeInOrderByHolidayDate(List<String> currencyCodes);

    List<Holiday> findByHolidayDateBetweenOrderByHolidayDate(LocalDate from, LocalDate to);
}
