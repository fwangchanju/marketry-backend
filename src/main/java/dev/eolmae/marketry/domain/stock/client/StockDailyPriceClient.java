package dev.eolmae.marketry.domain.stock.client;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.stock.entity.StockDailyPrice;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockDailyPriceClient {
    private final KiwoomApiClient apiClient;

    /**
     * 일봉 첫 페이지의 행을 반환한다. 모든 값이 빈 placeholder 행은 데이터 없음의 정상 응답이라 버리므로 빈 목록이 정상 skip이다. 목록
     * 구조 자체가 없거나 깨진 응답은 예외로 구분한다.
     */
    public List<Map<String, Object>> fetchFirstPage(String stockCode, ExchangeType exchange, LocalDate date) {
        String requestCode =
                switch (exchange) {
                    case KRX -> stockCode;
                    case NXT -> stockCode + "_NX";
                    case SOR -> throw new BadRequestException(ErrorCode.INVALID_INPUT, "일봉은 KRX/NXT 개별 저장");
                };
        // ka11081: KRX 정규장 일봉. ka10081은 시간외 체결까지 포함하므로 기존 종가 기준과 다르다.
        Map<String, Object> body = apiClient.postFirstPage(
                "ka11081",
                "/api/dostk/chart",
                Map.of(
                        "stk_cd",
                        requestCode,
                        "base_dt",
                        date.format(DateTimeFormatter.BASIC_ISO_DATE),
                        "upd_stkpc_tp",
                        "0"));
        Object rows = body.get("result_list");
        if (rows instanceof List<?> list) {
            List<Map<String, Object>> mapped = new ArrayList<>();
            for (Object row : list) {
                if (row instanceof Map<?, ?> == false) {
                    throw invalid("일봉 행 구조");
                }
                @SuppressWarnings("unchecked")
                var values = (Map<String, Object>) row;
                if (isEmptyPlaceholder(values) == false) {
                    mapped.add(values);
                }
            }
            return mapped;
        }
        throw invalid("일봉 목록 없음");
    }

    private boolean isEmptyPlaceholder(Map<String, Object> row) {
        return row.values().stream()
                .allMatch(value -> value == null || String.valueOf(value).isBlank());
    }

    public List<StockDailyPrice> parsePrices(
            String stockCode, ExchangeType exchange, List<Map<String, Object>> rows, LocalDateTime collectedAt) {
        List<StockDailyPrice> prices = new ArrayList<>();
        var dates = new HashSet<LocalDate>();
        for (var row : rows) {
            try {
                LocalDate date = LocalDate.parse(String.valueOf(row.get("dt")), DateTimeFormatter.BASIC_ISO_DATE);
                if (dates.add(date) == false) {
                    throw invalid("첫 페이지 중복 날짜");
                }
                long volume = new BigDecimal(String.valueOf(row.get("trde_qty")).replace(",", "")).longValueExact();
                if (volume < 0) {
                    throw invalid("음수 거래량");
                }
                prices.add(StockDailyPrice.create(
                        stockCode,
                        exchange,
                        date,
                        parsePrice(row, "open_pric"),
                        parsePrice(row, "high_pric"),
                        parsePrice(row, "low_pric"),
                        parsePrice(row, "cur_prc"),
                        volume,
                        collectedAt));
            } catch (NumberFormatException | ArithmeticException | java.time.DateTimeException e) {
                throw invalid("일봉 숫자 또는 날짜");
            }
        }
        return prices;
    }

    private BigDecimal parsePrice(Map<String, Object> row, String field) {
        String value = String.valueOf(row.get(field)).replace(",", "");
        if (value.startsWith("--")) {
            value = value.substring(1);
        }
        // 키움 가격의 +/-는 등락 방향이며, 수정주가가 아닌 원주가를 저장한다.
        BigDecimal price = new BigDecimal(value).abs().setScale(4, RoundingMode.UNNECESSARY);
        if (price.precision() > 19) {
            throw invalid("가격 정밀도 초과");
        }
        return price;
    }

    private BadRequestException invalid(String context) {
        return new BadRequestException(ErrorCode.KIWOOM_RESPONSE_PARSE_FAILED, context);
    }
}
