package dev.eolmae.marketry.domain.stock.repository;

import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import java.time.LocalDate;
import java.util.List;

public interface StockDailyPriceRepositoryCustom {
    /** 캘린더 행이 있는 날짜 중 해당 거래소의 일봉이 하나라도 있는 날짜. 날짜마다 존재 여부(EXISTS)만 확인한다. */
    List<LocalDate> findCalendarDatesWithPrice(ExchangeType exchangeType, LocalDate from, LocalDate to);
}
