package dev.eolmae.marketry.domain.stock.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import dev.eolmae.marketry.domain.stock.service.StockDailyPriceService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StockDailyPriceBackfillLoaderTest {
    private final StockDailyPriceClient client = mock(StockDailyPriceClient.class);
    private final StockDailyPriceService prices = mock(StockDailyPriceService.class);
    private final LocalDate today = LocalDate.of(2026, 10, 11);

    @Test
    void 실패로그에_가격파싱의_구체적사유를_남긴다() {
        var failure = new BadRequestException(ErrorCode.KIWOOM_RESPONSE_PARSE_FAILED, "첫 페이지 일봉 없음 또는 최대 건수 초과");
        assertThat(StockDailyPriceBackfillManualTest.failureReason(failure))
                .contains("KIWOOM_RESPONSE_PARSE_FAILED", "첫 페이지 일봉 없음 또는 최대 건수 초과");
    }

    @Test
    void 실패로그에_인증응답이나_원인예외는_노출하지않는다() {
        var failure = new BadRequestException(
                ErrorCode.KIWOOM_TOKEN_ISSUE_FAILED,
                new RuntimeException("test-sensitive-response"),
                "test-sensitive-response");
        assertThat(StockDailyPriceBackfillManualTest.failureReason(failure))
                .contains("KIWOOM_TOKEN_ISSUE_FAILED")
                .doesNotContain("test-sensitive-response");
    }

    @Test
    void 첫페이지600개를_추가요청없이_모두저장한다() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int day = 1; day <= 600; day++) {
            rows.add(row(today.minusDays(day)));
        }
        when(client.fetchFirstPage(anyString(), any(), any())).thenReturn(rows);
        doCallRealMethod().when(client).parsePrices(anyString(), any(), anyList(), any());
        when(prices.savePage(anyList()))
                .thenAnswer(call -> new StockDailyPriceService.SaveResult(((List<?>) call.getArgument(0)).size(), 0));
        var result = new StockDailyPriceBackfillLoader(client, prices)
                .load("005930", ExchangeType.KRX, today, today, today.atTime(5, 0));
        assertThat(result.inserted()).isEqualTo(600);
        verify(client, times(1)).fetchFirstPage(anyString(), any(), any());
    }

    @Test
    void 오늘행은_제외하고_NXT의_짧은이력도_그대로저장한다() {
        when(client.fetchFirstPage(anyString(), any(), any())).thenReturn(List.of(row(today), row(today.minusDays(1))));
        doCallRealMethod().when(client).parsePrices(anyString(), any(), anyList(), any());
        when(prices.savePage(anyList()))
                .thenAnswer(call -> new StockDailyPriceService.SaveResult(((List<?>) call.getArgument(0)).size(), 0));
        assertThat(new StockDailyPriceBackfillLoader(client, prices)
                        .load("005930", ExchangeType.NXT, today, today, today.atTime(5, 0))
                        .inserted())
                .isEqualTo(1);
    }

    @Test
    void 정상_빈응답은_저장없이_성공으로_건너뛴다() {
        when(client.fetchFirstPage(anyString(), any(), any())).thenReturn(List.of());
        var result = new StockDailyPriceBackfillLoader(client, prices)
                .load("196490", ExchangeType.KRX, today, today, LocalDateTime.now());
        assertThat(result).isEqualTo(new StockDailyPriceService.SaveResult(0, 0));
        verify(prices, never()).savePage(anyList());
    }

    @Test
    void 최대건수를_넘는_첫페이지는_실패로_처리한다() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int day = 1; day <= 601; day++) {
            rows.add(row(today.minusDays(day)));
        }
        when(client.fetchFirstPage(anyString(), any(), any())).thenReturn(rows);
        assertThatThrownBy(() -> new StockDailyPriceBackfillLoader(client, prices)
                        .load("005930", ExchangeType.KRX, today, today, LocalDateTime.now()))
                .isInstanceOf(BadRequestException.class);
    }

    private Map<String, Object> row(LocalDate date) {
        return Map.of(
                "dt",
                date.format(DateTimeFormatter.BASIC_ISO_DATE),
                "open_pric",
                "100",
                "high_pric",
                "110",
                "low_pric",
                "90",
                "cur_prc",
                "100",
                "trde_qty",
                "1000");
    }
}
