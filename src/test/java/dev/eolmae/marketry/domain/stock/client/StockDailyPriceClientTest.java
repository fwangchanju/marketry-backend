package dev.eolmae.marketry.domain.stock.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StockDailyPriceClientTest {
    private final KiwoomApiClient api = mock(KiwoomApiClient.class);
    private final StockDailyPriceClient client = new StockDailyPriceClient(api);

    @Test
    void 거래소별_코드로_정규장API의_첫페이지만_요청한다() {
        when(api.postFirstPage(anyString(), anyString(), anyMap())).thenReturn(Map.of("result_list", List.of()));
        var date = LocalDate.of(2026, 10, 8);
        client.fetchFirstPage("005930", ExchangeType.NXT, date);
        verify(api)
                .postFirstPage(
                        "ka11081",
                        "/api/dostk/chart",
                        Map.of("stk_cd", "005930_NX", "base_dt", "20261008", "upd_stkpc_tp", "0"));
        assertThatThrownBy(() -> client.fetchFirstPage("005930", ExchangeType.SOR, date))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void 모든_값이_빈_placeholder와_빈목록은_정상_빈응답으로_빈목록을_반환한다() {
        var placeholder = new java.util.HashMap<String, Object>();
        placeholder.put("dt", "");
        placeholder.put("cur_prc", "  ");
        placeholder.put("trde_qty", null);
        when(api.postFirstPage(anyString(), anyString(), anyMap()))
                .thenReturn(Map.of("result_list", List.of(placeholder)))
                .thenReturn(Map.of("result_list", List.of()));
        var date = LocalDate.of(2026, 10, 8);

        assertThat(client.fetchFirstPage("196490", ExchangeType.KRX, date)).isEmpty();
        assertThat(client.fetchFirstPage("196490", ExchangeType.KRX, date)).isEmpty();
    }

    @Test
    void 값이_있는_행은_유지하고_placeholder만_버린다() {
        var row = Map.<String, Object>of("dt", "20261008", "cur_prc", "100");
        when(api.postFirstPage(anyString(), anyString(), anyMap()))
                .thenReturn(Map.of("result_list", List.of(Map.of("dt", ""), row)));

        assertThat(client.fetchFirstPage("005930", ExchangeType.KRX, LocalDate.of(2026, 10, 8)))
                .containsExactly(row);
    }

    @Test
    void 목록이_없거나_행구조가_깨진_응답은_정상_빈응답과_달리_예외다() {
        when(api.postFirstPage(anyString(), anyString(), anyMap()))
                .thenReturn(Map.of("return_code", 0))
                .thenReturn(Map.of("result_list", "invalid"))
                .thenReturn(Map.of("result_list", List.of("invalid")));
        var date = LocalDate.of(2026, 10, 8);

        for (int index = 0; index < 3; index++) {
            assertThatThrownBy(() -> client.fetchFirstPage("005930", ExchangeType.KRX, date))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void 등락부호_쉼표_거래정지0을_해석한다() {
        var values = Map.<String, Object>of(
                "dt",
                "20261008",
                "open_pric",
                "+10,000",
                "high_pric",
                "11000",
                "low_pric",
                "--9000",
                "cur_prc",
                "0",
                "trde_qty",
                "1,234");
        var price = client.parsePrices("005930", ExchangeType.KRX, List.of(values), LocalDateTime.of(2026, 10, 9, 5, 0))
                .getFirst();
        assertThat(price.getOpenPrice()).isEqualByComparingTo("10000");
        assertThat(price.getLowPrice()).isEqualByComparingTo("9000");
        assertThat(price.getClosePrice()).isZero();
        assertThat(price.getVolume()).isEqualTo(1234);
    }

    @Test
    void 잘못된숫자_음수거래량_중복날짜는_저장전에_거부한다() {
        var values = Map.<String, Object>of(
                "dt",
                "20261008",
                "open_pric",
                "10",
                "high_pric",
                "11",
                "low_pric",
                "9",
                "cur_prc",
                "10",
                "trde_qty",
                "100");
        var collectedAt = LocalDateTime.of(2026, 10, 9, 5, 0);
        assertThatThrownBy(() -> client.parsePrices("005930", ExchangeType.KRX, List.of(values, values), collectedAt))
                .isInstanceOf(BadRequestException.class);
        for (String field : List.of("dt", "cur_prc", "trde_qty")) {
            var invalid = new java.util.HashMap<>(values);
            invalid.put(field, field.equals("trde_qty") ? "-1" : "invalid");
            assertThatThrownBy(() -> client.parsePrices("005930", ExchangeType.KRX, List.of(invalid), collectedAt))
                    .isInstanceOf(BadRequestException.class);
        }
    }
}
