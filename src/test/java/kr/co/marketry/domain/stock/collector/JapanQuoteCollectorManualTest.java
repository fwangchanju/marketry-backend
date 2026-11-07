package kr.co.marketry.domain.stock.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient;
import kr.co.marketry.domain.stock.entity.OverseasStockQuote;
import kr.co.marketry.domain.stock.entity.OverseasStockQuoteId;
import kr.co.marketry.domain.stock.repository.OverseasStockQuoteRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClient;

/**
 * 실제 야후 응답으로 수집 계산을 확인한다. 11/4 시세를 기준으로 받아 계산한 값이 사용자가 넣은 11/6 종가와 맞아야 한다.
 * 토요일·일요일이나 11/6이 최신 거래일일 때만 맞는다. 실행: ./gradlew manualTest --tests "*.JapanQuoteCollectorManualTest" -i
 */
@Tag("manual")
class JapanQuoteCollectorManualTest {
    private static final Pattern ROW =
            Pattern.compile("\\('JAPAN', '([^']+)', TIMESTAMP '[^']+', ([\\d.]+), ([\\d.]+), (-?[\\d.]+), (\\d+)\\)");
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 11, 4, 15, 30);

    @Test
    @SuppressWarnings("unchecked")
    void 야후_값으로_계산한_결과가_저장된_종가와_맞는다() throws IOException {
        Map<String, BigDecimal[]> base = readRows("V37__japan_quote_seed.sql");
        Map<String, BigDecimal[]> expected = readRows("V40__japan_quote_20261106.sql");
        List<OverseasStockQuote> baseQuotes = base.entrySet().stream()
                .map(row -> OverseasStockQuote.create(
                        new OverseasStockQuoteId("JAPAN", row.getKey(), BASE_TIME),
                        row.getValue()[0],
                        row.getValue()[1],
                        row.getValue()[2],
                        row.getValue()[3]))
                .toList();
        OverseasStockQuoteRepository repository = mock(OverseasStockQuoteRepository.class);
        when(repository.findFirstByIdMarketOrderByIdSnapshotTimeDesc("JAPAN"))
                .thenReturn(Optional.of(baseQuotes.getFirst()));
        when(repository.findAllByIdMarketAndIdSnapshotTime("JAPAN", BASE_TIME)).thenReturn(baseQuotes);

        JapanQuoteCollector.Result result =
                new JapanQuoteCollector(new YahooFinanceQuoteClient(RestClient.create()), repository).collect();

        System.out.println("결과: " + result);
        assertThat(result.saved()).isTrue();
        assertThat(result.snapshotTime()).isEqualTo(LocalDateTime.of(2026, 11, 6, 15, 30));
        assertThat(result.successCount()).isEqualTo(expected.size());
        ArgumentCaptor<Iterable<OverseasStockQuote>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captor.capture());
        double largestValueGap = 0;
        for (OverseasStockQuote quote : (List<OverseasStockQuote>) captor.getValue()) {
            BigDecimal[] row = expected.get(quote.getId().getStockCode());
            assertThat(quote.getCurrentPrice()).isEqualByComparingTo(row[0]);
            assertThat(quote.getLastPrice()).isEqualByComparingTo(row[1]);
            assertThat(quote.getChangeRate().subtract(row[2]).abs()).isLessThanOrEqualTo(new BigDecimal("0.02"));
            double gap = Math.abs(quote.getTotalMarketValue().doubleValue() / row[3].doubleValue() - 1);
            largestValueGap = Math.max(largestValueGap, gap);
        }
        System.out.printf("시가총액 최대 오차: %.3f%%%n", largestValueGap * 100);
        assertThat(largestValueGap).isLessThan(0.01);
    }

    private static Map<String, BigDecimal[]> readRows(String fileName) throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/flyway", fileName));
        Map<String, BigDecimal[]> rows = new LinkedHashMap<>();
        Matcher matcher = ROW.matcher(sql);
        while (matcher.find()) {
            rows.put(matcher.group(1), new BigDecimal[] {
                new BigDecimal(matcher.group(2)),
                new BigDecimal(matcher.group(3)),
                new BigDecimal(matcher.group(4)),
                new BigDecimal(matcher.group(5))
            });
        }
        return rows;
    }
}
