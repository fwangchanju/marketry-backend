package kr.co.marketry.domain.stock.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.common.enums.Country;
import kr.co.marketry.domain.stock.entity.MarketCalendar;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketCalendarRepository extends JpaRepository<MarketCalendar, Long> {
    Optional<MarketCalendar> findByCountryAndDate(Country country, LocalDate date);

    List<MarketCalendar> findByCountryAndDateIn(Country country, Collection<LocalDate> dates);
}
