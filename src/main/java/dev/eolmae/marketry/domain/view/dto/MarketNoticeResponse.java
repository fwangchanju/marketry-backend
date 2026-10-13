package dev.eolmae.marketry.domain.view.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 홈 안내 틀 응답. {@code items}는 그날 하루만 알리는 종목이고, 두 건수는 날짜와 무관한 현황이다.
 *
 * @param recentNewListingCount 기준일 포함 최근 3개월 안에 새로 상장한 종목 수
 * @param currentSuspensionCount 지금 거래정지 중인 종목 수(아직 지도에서 빠지기 전인 종목 포함)
 */
public record MarketNoticeResponse(
        LocalDate date, List<MarketNoticeItem> items, int recentNewListingCount, int currentSuspensionCount) {}
