package kr.co.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import kr.co.marketry.domain.stock.client.KiwoomApiClient;
import kr.co.marketry.domain.stock.client.StockDailyPriceClient;
import kr.co.marketry.domain.stock.repository.HistoryBackfillProgressRepository;
import kr.co.marketry.domain.stock.repository.MarketIndexDailyRepository;
import org.junit.jupiter.api.Test;

class HistoryBackfillServiceTest {
    private final KiwoomApiClient api = mock(KiwoomApiClient.class);
    private final MarketIndexDailyRepository indexRepository = mock(MarketIndexDailyRepository.class);
    private final HistoryBackfillProgressRepository progress = mock(HistoryBackfillProgressRepository.class);

    private final HistoryBackfillService service =
            new HistoryBackfillService(
                    api,
                    mock(StockDailyPriceClient.class),
                    mock(StockDailyPriceService.class),
                    mock(StockInfoCacheService.class),
                    indexRepository,
                    progress) {
                @Override
                void pause(Duration duration) {}

                @Override
                LocalDateTime now() {
                    return LocalDate.of(2026, 11, 7).atTime(10, 0);
                }
            };

    private static Map<String, Object> row(String date) {
        return Map.of(
                "dt",
                date,
                "open_pric",
                "100000",
                "high_pric",
                "100000",
                "low_pric",
                "100000",
                "cur_prc",
                "505969",
                "trde_qty",
                "1",
                "trde_prica",
                "2");
    }

    @Test
    void 지수_값은_100으로_나눈다() {
        var parsed = HistoryBackfillService.parseIndexRow("KOSPI", row("20261106"), LocalDateTime.now());

        assertThat(parsed.getCloseValue()).isEqualByComparingTo(new BigDecimal("5059.69"));
        assertThat(parsed.getId().getTradeDate()).isEqualTo(LocalDate.of(2026, 11, 6));
    }

    @Test
    void 장중과_새벽_수집_시간에는_쉰다() {
        LocalDateTime monday = LocalDate.of(2026, 11, 9).atStartOfDay();
        assertThat(HistoryBackfillService.isQuietTime(monday.withHour(10))).isFalse();
        assertThat(HistoryBackfillService.isQuietTime(monday.withHour(21))).isTrue();
        assertThat(HistoryBackfillService.isQuietTime(monday.withHour(5))).isFalse();
        LocalDateTime saturday = LocalDate.of(2026, 11, 7).atStartOfDay();
        assertThat(HistoryBackfillService.isQuietTime(saturday.withHour(10))).isTrue();
        assertThat(HistoryBackfillService.isQuietTime(saturday.withHour(5))).isFalse();
    }

    @Test
    void 끝난_항목은_호출하지_않는다() {
        when(progress.existsById(any())).thenReturn(true);

        service.run(HistoryBackfillService.KOREA_INDEX, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 11, 6));

        verify(api, never()).postFirstPage(any(), any(), any());
        assertThat(service.status().skipped()).isEqualTo(2);
    }

    @Test
    void 시작일_밖의_행은_저장하지_않고_시작일을_넘으면_멈춘다() {
        when(api.postFirstPage(eq("ka20006"), any(), any()))
                .thenReturn(Map.of("inds_dt_pole_qry", List.of(row("20200102"), row("20191230"))));

        service.run(HistoryBackfillService.KOREA_INDEX, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 2));

        verify(api, times(2)).postFirstPage(any(), any(), any());
        verify(indexRepository, times(2)).saveAll(anyList());
        assertThat(service.status().savedRows()).isEqualTo(2);
        assertThat(service.status().running()).isFalse();
    }
}
