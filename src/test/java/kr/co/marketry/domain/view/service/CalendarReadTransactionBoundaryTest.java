package kr.co.marketry.domain.view.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import kr.co.marketry.domain.stock.service.SectorPriceSnapshotService;
import kr.co.marketry.domain.view.enums.ChangeRateMode;
import kr.co.marketry.domain.view.enums.MarketQuery;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 달력 조회(MarketCalendarService)는 REQUIRES_NEW라 새 연결을 빌린다. HTTP로 들어오는 읽기 진입점이 바깥 트랜잭션을 쥔 채 이를 부르면
 * 동시 요청이 풀 크기에 닿을 때 서로 두 번째 연결을 기다린다. 이 진입점들이 바깥 트랜잭션 없이 시작하는지 고정한다.
 */
class CalendarReadTransactionBoundaryTest {

    @Test
    void 달력을_읽는_HTTP_진입점은_바깥_트랜잭션_없이_시작한다() throws NoSuchMethodException {
        List<Method> entryPoints = List.of(
                TreemapQueryService.class.getMethod(
                        "getDefaultTreemap",
                        MarketQuery.class,
                        LocalDateTime.class,
                        boolean.class,
                        ChangeRateMode.class),
                TreemapQueryService.class.getMethod(
                        "getCustomTreemap",
                        MarketQuery.class,
                        LocalDateTime.class,
                        boolean.class,
                        ChangeRateMode.class),
                TreemapQueryService.class.getMethod(
                        "getPublishedTreemap",
                        MarketQuery.class,
                        LocalDateTime.class,
                        boolean.class,
                        ChangeRateMode.class),
                TreemapQueryService.class.getMethod("getClosingSnapshotDays", MarketQuery.class, YearMonth.class),
                TreemapQueryService.class.getMethod("getTradingDayGap", LocalDate.class, LocalDate.class),
                MarketQueryService.class.getMethod("getMarketSummary"),
                MarketIndexHistoryService.class.getMethod("getClosingHistory", YearMonth.class),
                SectorPriceSnapshotService.class.getMethod("findClosingSnapshotTimes", List.class, YearMonth.class));

        assertThat(entryPoints).allSatisfy(method -> {
            Transactional transactional = method.getAnnotation(Transactional.class);
            assertThat(transactional)
                    .as(method.getDeclaringClass().getSimpleName() + "." + method.getName())
                    .isNotNull();
            assertThat(transactional.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
        });
    }
}
