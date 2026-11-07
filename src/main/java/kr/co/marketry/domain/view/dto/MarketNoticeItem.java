package kr.co.marketry.domain.view.dto;

import java.time.LocalDate;
import kr.co.marketry.common.enums.Market;
import kr.co.marketry.domain.view.enums.MarketNoticeType;

/** 안내 한 건. baseDate는 그 일이 시작된 날(제외가 시작된 날, 상장한 날)이다. */
public record MarketNoticeItem(
        MarketNoticeType type, String stockCode, String stockName, Market market, LocalDate baseDate) {}
