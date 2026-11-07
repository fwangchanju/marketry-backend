package kr.co.marketry.domain.stock.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import kr.co.marketry.domain.stock.client.KiwoomApiClient;
import kr.co.marketry.domain.stock.client.StockDailyPriceClient;
import kr.co.marketry.domain.stock.enums.ExchangeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 키움 업종일봉(ka20006)과 종목 일봉(ka11081)을 어디까지 받을 수 있는지 조회만 한다. DB에 쓰지 않는다.
@Service
@RequiredArgsConstructor
public class MarketIndexDailyPreviewService {
    private static final String API_ID = "ka20006";
    private static final String PATH = "/api/dostk/chart";
    private static final String ROWS_KEY = "inds_dt_pole_qry";
    private static final int MAX_CALLS = 40;
    private static final Map<String, String> INDEX_CODES = Map.of("KOSPI", "001", "KOSDAQ", "101");

    private final KiwoomApiClient apiClient;
    private final StockDailyPriceClient stockClient;

    public List<IndexPreview> preview(LocalDate from, LocalDate to) {
        return INDEX_CODES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> previewOne(entry.getKey(), base -> fetch(entry.getValue(), base), from, to))
                .toList();
    }

    public IndexPreview previewStock(String stockCode, LocalDate from, LocalDate to) {
        return previewOne(stockCode, base -> stockClient.fetchFirstPage(stockCode, ExchangeType.KRX, base), from, to);
    }

    private IndexPreview previewOne(
            String market, Function<LocalDate, List<Map<String, Object>>> fetcher, LocalDate from, LocalDate to) {
        TreeMap<String, Map<String, Object>> byDate = new TreeMap<>();
        LocalDate base = to;
        int calls = 0;
        int firstPageRows = 0;
        while (calls < MAX_CALLS && base.isBefore(from) == false) {
            List<Map<String, Object>> rows = fetcher.apply(base);
            calls++;
            if (calls == 1) {
                firstPageRows = rows.size();
            }
            int before = byDate.size();
            String oldest = null;
            for (Map<String, Object> row : rows) {
                String date = String.valueOf(row.get("dt"));
                if (date.isBlank() || "null".equals(date)) {
                    continue;
                }
                byDate.put(date, row);
                oldest = oldest == null || date.compareTo(oldest) < 0 ? date : oldest;
            }
            if (oldest == null || byDate.size() == before) {
                break;
            }
            base = LocalDate.parse(oldest, DateTimeFormatter.BASIC_ISO_DATE).minusDays(1);
        }
        return new IndexPreview(
                market,
                calls,
                firstPageRows,
                byDate.size(),
                byDate.isEmpty() ? null : byDate.firstKey(),
                byDate.isEmpty() ? null : byDate.lastKey(),
                byDate.isEmpty() ? null : byDate.firstEntry().getValue(),
                byDate.isEmpty() ? null : byDate.lastEntry().getValue());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetch(String code, LocalDate base) {
        Map<String, Object> body = apiClient.postFirstPage(
                API_ID, PATH, Map.of("inds_cd", code, "base_dt", base.format(DateTimeFormatter.BASIC_ISO_DATE)));
        Object rows = body.get(ROWS_KEY);
        List<Map<String, Object>> result = new ArrayList<>();
        if (rows instanceof List<?> list) {
            for (Object row : list) {
                if (row instanceof Map<?, ?>) {
                    result.add((Map<String, Object>) row);
                }
            }
        }
        return result;
    }

    public record IndexPreview(
            String market,
            int calls,
            int firstPageRows,
            int days,
            String oldest,
            String newest,
            Map<String, Object> oldestRow,
            Map<String, Object> newestRow) {}
}
