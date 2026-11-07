package kr.co.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import kr.co.marketry.domain.stock.client.KiwoomApiClient;
import org.junit.jupiter.api.Test;

class MarketIndexDailyPreviewServiceTest {
    private final KiwoomApiClient client = mock(KiwoomApiClient.class);
    private final MarketIndexDailyPreviewService service = new MarketIndexDailyPreviewService(client);

    private static Map<String, Object> page(String... dates) {
        return Map.of(
                "inds_dt_pole_qry",
                List.of(dates).stream().map(date -> Map.of("dt", date)).toList());
    }

    @Test
    void 가장_오래된_날짜_전날로_이어_받고_빈_응답이면_멈춘다() {
        when(client.postFirstPage(eq("ka20006"), any(), any())).thenAnswer(call -> {
            Map<String, String> payload = call.getArgument(2);
            return switch (payload.get("base_dt")) {
                case "20261106" -> page("20261106", "20261105");
                case "20261104" -> page("20261104");
                default -> page();
            };
        });

        var kospi = service.preview(LocalDate.of(2024, 4, 18), LocalDate.of(2026, 11, 6)).stream()
                .filter(result -> result.market().equals("KOSPI"))
                .findFirst()
                .orElseThrow();

        assertThat(kospi.days()).isEqualTo(3);
        assertThat(kospi.oldest()).isEqualTo("20261104");
        assertThat(kospi.newest()).isEqualTo("20261106");
        assertThat(kospi.calls()).isEqualTo(3);
    }
}
