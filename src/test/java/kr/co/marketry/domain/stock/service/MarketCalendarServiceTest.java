package kr.co.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.common.enums.Country;
import kr.co.marketry.common.event.MarketCalendarChangedEvent;
import kr.co.marketry.domain.stock.entity.MarketCalendar;
import kr.co.marketry.domain.stock.enums.MarketCalendarStatus;
import kr.co.marketry.domain.stock.repository.MarketCalendarRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class MarketCalendarServiceTest {
    private final MarketCalendarRepository repository = mock(MarketCalendarRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final MarketCalendarService service = new MarketCalendarService(repository, publisher);
    private final LocalDate date = LocalDate.of(2026, 10, 9);

    @Test
    void 빈날짜는조회없이빈맵을반환한다() {
        assertThat(service.findByCountryAndDateIn(Country.KR, List.of())).isEmpty();
        verify(repository, never()).findByCountryAndDateIn(any(), any());
    }

    @Test
    void 조회실패로기존정상행을덮어쓰지않는다() {
        var calendar = MarketCalendar.create(Country.KR, date, MarketCalendarStatus.HOLIDAY, null);
        when(repository.findByCountryAndDate(Country.KR, date)).thenReturn(Optional.of(calendar));
        service.saveFailure(Country.KR, date);
        assertThat(calendar.getStatus()).isEqualTo(MarketCalendarStatus.HOLIDAY);
        verify(repository, never()).save(any());
    }

    @Test
    void 행이없으면요청일만실패로저장한다() {
        when(repository.findByCountryAndDate(Country.KR, date)).thenReturn(Optional.empty());
        service.saveFailure(Country.KR, date);
        var captor = org.mockito.ArgumentCaptor.forClass(MarketCalendar.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDate()).isEqualTo(date);
        assertThat(captor.getValue().getStatus()).isEqualTo(MarketCalendarStatus.FAILED);
        assertThat(captor.getValue().getIntegrated()).isNull();
    }

    @Test
    void 기존실패행을성공으로복구하고변경이벤트를발행한다() {
        var failed = MarketCalendar.create(Country.KR, date, MarketCalendarStatus.FAILED, null);
        when(repository.findByCountryAndDateIn(Country.KR, List.of(date))).thenReturn(List.of(failed));
        service.saveSuccess(
                Country.KR, List.of(MarketCalendar.create(Country.KR, date, MarketCalendarStatus.HOLIDAY, null)));
        assertThat(failed.getStatus()).isEqualTo(MarketCalendarStatus.HOLIDAY);
        verify(publisher).publishEvent(new MarketCalendarChangedEvent(Country.KR, date));
        verify(repository).flush();
    }
}
