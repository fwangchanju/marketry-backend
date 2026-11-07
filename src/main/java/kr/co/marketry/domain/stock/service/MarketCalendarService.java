package kr.co.marketry.domain.stock.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.co.marketry.common.enums.Country;
import kr.co.marketry.common.event.MarketCalendarChangedEvent;
import kr.co.marketry.domain.stock.entity.MarketCalendar;
import kr.co.marketry.domain.stock.enums.MarketCalendarStatus;
import kr.co.marketry.domain.stock.repository.MarketCalendarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketCalendarService {
    private final MarketCalendarRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<MarketCalendar> findByCountryAndDate(Country country, LocalDate date) {
        return repository.findByCountryAndDate(country, date);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Map<LocalDate, MarketCalendar> findByCountryAndDateIn(Country country, Collection<LocalDate> dates) {
        if (dates.isEmpty()) {
            return Map.of();
        }
        return repository.findByCountryAndDateIn(country, dates).stream()
                .collect(Collectors.toMap(MarketCalendar::getDate, Function.identity()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveSuccess(Country country, List<MarketCalendar> calendars) {
        Map<LocalDate, MarketCalendar> existing =
                repository
                        .findByCountryAndDateIn(
                                country,
                                calendars.stream().map(MarketCalendar::getDate).toList())
                        .stream()
                        .collect(Collectors.toMap(MarketCalendar::getDate, Function.identity()));
        for (MarketCalendar calendar : calendars) {
            MarketCalendar persisted = existing.get(calendar.getDate());
            if (persisted == null) {
                repository.save(calendar);
            } else {
                persisted.update(calendar.getStatus(), calendar.getIntegrated());
            }
            eventPublisher.publishEvent(new MarketCalendarChangedEvent(country, calendar.getDate()));
        }
        repository.flush();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFailure(Country country, LocalDate date) {
        Optional<MarketCalendar> existing = repository.findByCountryAndDate(country, date);
        if (existing.isPresent()) {
            if (existing.get().getStatus() == MarketCalendarStatus.FAILED) {
                existing.get().update(MarketCalendarStatus.FAILED, null);
            }
            return;
        }
        repository.save(MarketCalendar.create(country, date, MarketCalendarStatus.FAILED, null));
    }
}
