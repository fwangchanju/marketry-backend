package dev.eolmae.marketry.domain.stock.client;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import dev.eolmae.marketry.domain.stock.service.StockDailyPriceService;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 초기 적재는 운영 컨테이너에 포함하지 않고 첫 페이지 하나의 저장만 공통 처리에 맡긴다. */
record StockDailyPriceBackfillLoader(StockDailyPriceClient client, StockDailyPriceService prices) {
    StockDailyPriceService.SaveResult load(
            String code, ExchangeType exchange, LocalDate baseDate, LocalDate today, LocalDateTime collectedAt) {
        var rows = client.fetchFirstPage(code, exchange, baseDate);
        if (rows.isEmpty()) {
            // 빈 placeholder나 빈 목록은 정상 응답이다. 날짜를 만들어 저장하지 않고 실패로도 보지 않는다.
            return new StockDailyPriceService.SaveResult(0, 0);
        }
        if (rows.size() > 600) {
            throw new BadRequestException(ErrorCode.KIWOOM_RESPONSE_PARSE_FAILED, "첫 페이지 최대 건수 초과");
        }
        var selected = client.parsePrices(code, exchange, rows, collectedAt).stream()
                .filter(price -> price.getTradeDate().isBefore(today))
                .toList();
        if (selected.stream().anyMatch(price -> price.getTradeDate().isAfter(baseDate))) {
            throw new BadRequestException(ErrorCode.KIWOOM_RESPONSE_PARSE_FAILED, "일봉 기준일 이후 날짜");
        }
        if (selected.isEmpty()) {
            return new StockDailyPriceService.SaveResult(0, 0);
        }
        return prices.savePage(selected);
    }
}
