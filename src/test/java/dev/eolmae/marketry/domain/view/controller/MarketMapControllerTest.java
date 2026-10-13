package dev.eolmae.marketry.domain.view.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.eolmae.marketry.domain.custom.service.CustomScaleService;
import dev.eolmae.marketry.domain.custom.service.CustomSectorService;
import dev.eolmae.marketry.domain.custom.service.CustomValueTierThresholdService;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.view.dto.MarketMapResponse;
import dev.eolmae.marketry.domain.view.dto.MarketNoticeResponse;
import dev.eolmae.marketry.domain.view.dto.MarketTradingScheduleResponse;
import dev.eolmae.marketry.domain.view.dto.MarketTradingScheduleResponse.TimeWindow;
import dev.eolmae.marketry.domain.view.enums.ChangeRateMode;
import dev.eolmae.marketry.domain.view.enums.MarketQuery;
import dev.eolmae.marketry.domain.view.service.MarketMapQueryService;
import dev.eolmae.marketry.domain.view.service.MarketNoticeService;
import dev.eolmae.marketry.domain.view.service.MarketTradingScheduleService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

// 이 레포엔 컨트롤러 테스트도 @WebMvcTest 의존성도 없다(테스트 기준 문서 참고) — 여기서만 standaloneSetup으로
// 날짜 바인딩과 시간표 응답 형식을 좁게 확인한다. @WebMvcTest 등 새 의존성은 더하지 않는다.
class MarketMapControllerTest {

    private final MarketMapQueryService marketMapQueryService = mock(MarketMapQueryService.class);
    private final MarketTradingScheduleService tradingScheduleService = mock(MarketTradingScheduleService.class);
    private final MarketNoticeService marketNoticeService = mock(MarketNoticeService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new MarketMapController(
                    marketMapQueryService,
                    mock(CustomSectorService.class),
                    mock(CustomScaleService.class),
                    mock(CustomValueTierThresholdService.class),
                    tradingScheduleService,
                    marketNoticeService))
            .build();

    @Test
    void 시간표_조회는_날짜와_한국시각_문자열을_프론트에_전달한다() throws Exception {
        LocalDate date = LocalDate.of(2025, 11, 13);
        TimeWindow window = new TimeWindow(date.atTime(16, 40), date.atTime(17, 0));
        when(tradingScheduleService.getTradingSchedule(date))
                .thenReturn(new MarketTradingScheduleResponse(
                        date,
                        MarketCalendarStatus.TRADING_DAY,
                        null,
                        new TimeWindow(date.atTime(10, 0), date.atTime(16, 30)),
                        new TimeWindow(date.atTime(16, 40), date.atTime(20, 0)),
                        List.of(window)));

        mockMvc.perform(get("/api/map/trading-schedule?date=2025-11-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2025-11-13"))
                .andExpect(jsonPath("$.status").value("TRADING_DAY"))
                .andExpect(jsonPath("$.regularMarket.endTime").value("2025-11-13T16:30:00"))
                .andExpect(jsonPath("$.nxtOnlyWindows[0].startTime").value("2025-11-13T16:40:00"))
                .andExpect(jsonPath("$.nxtOnlyWindows[0].endTime").value("2025-11-13T17:00:00"));
    }

    @Test
    void 안내_조회는_날짜를_바인딩하고_종목_안내를_내려준다() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 14);
        when(marketNoticeService.getNotices(date))
                .thenReturn(new MarketNoticeResponse(
                        date,
                        List.of(new dev.eolmae.marketry.domain.view.dto.MarketNoticeItem(
                                dev.eolmae.marketry.domain.view.enums.MarketNoticeType.TRADING_SUSPENSION,
                                "005930",
                                "삼성전자",
                                dev.eolmae.marketry.common.enums.Market.KOSPI,
                                date)),
                        3,
                        2,
                        123_000_000L,
                        45_000_000L));

        mockMvc.perform(get("/api/map/notices?date=2026-10-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-10-14"))
                .andExpect(jsonPath("$.items[0].type").value("TRADING_SUSPENSION"))
                .andExpect(jsonPath("$.items[0].stockCode").value("005930"))
                .andExpect(jsonPath("$.recentNewListingCount").value(3))
                .andExpect(jsonPath("$.currentSuspensionCount").value(2))
                .andExpect(jsonPath("$.recentNewListingMarketValue").value(123_000_000L))
                .andExpect(jsonPath("$.currentSuspensionMarketValue").value(45_000_000L));
    }

    @Test
    void 안내_조회는_날짜가_없으면_오늘_날짜로_조회한다() throws Exception {
        when(marketNoticeService.getNotices(org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(new MarketNoticeResponse(LocalDate.of(2026, 10, 14), List.of(), 0, 0, 0L, 0L));

        mockMvc.perform(get("/api/map/notices")).andExpect(status().isOk());

        verify(marketNoticeService).getNotices(org.mockito.ArgumentMatchers.any(LocalDate.class));
    }

    @Test
    void getMarketMap_snapshotTime이_LocalDateTime으로_바인딩된다() throws Exception {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 9, 22, 10, 5, 0);
        when(marketMapQueryService.getCustomMarketMap(MarketQuery.KOSPI, snapshotTime, false, ChangeRateMode.DAILY))
                .thenReturn(MarketMapResponse.empty());

        mockMvc.perform(get("/api/map?market=KOSPI&isCustom=true&snapshotTime=2026-09-22T10:05:00"));

        verify(marketMapQueryService).getCustomMarketMap(MarketQuery.KOSPI, snapshotTime, false, ChangeRateMode.DAILY);
    }

    @Test
    void getMarketMap_nxtOnly가_내_분류에도_전달된다() throws Exception {
        when(marketMapQueryService.getCustomMarketMap(
                        eq(MarketQuery.KOSPI), isNull(), eq(true), eq(ChangeRateMode.DAILY)))
                .thenReturn(MarketMapResponse.empty());

        mockMvc.perform(get("/api/map?market=KOSPI&isCustom=true&nxtOnly=true"));

        verify(marketMapQueryService)
                .getCustomMarketMap(eq(MarketQuery.KOSPI), isNull(), eq(true), eq(ChangeRateMode.DAILY));
    }

    @Test
    void getMarketMap_snapshotTime이_없으면_null로_바인딩된다() throws Exception {
        when(marketMapQueryService.getDefaultMarketMap(
                        eq(MarketQuery.KOSPI), isNull(), eq(false), eq(ChangeRateMode.DAILY)))
                .thenReturn(MarketMapResponse.empty());

        mockMvc.perform(get("/api/map?market=KOSPI&isCustom=false"));

        verify(marketMapQueryService)
                .getDefaultMarketMap(eq(MarketQuery.KOSPI), isNull(), eq(false), eq(ChangeRateMode.DAILY));
    }

    @Test
    void getMarketMap_nxtOnly가_거래소_분류에_전달된다() throws Exception {
        when(marketMapQueryService.getDefaultMarketMap(
                        eq(MarketQuery.KOSPI), isNull(), eq(true), eq(ChangeRateMode.DAILY)))
                .thenReturn(MarketMapResponse.empty());

        mockMvc.perform(get("/api/map?market=KOSPI&isCustom=false&nxtOnly=true"));

        verify(marketMapQueryService)
                .getDefaultMarketMap(eq(MarketQuery.KOSPI), isNull(), eq(true), eq(ChangeRateMode.DAILY));
    }

    @Test
    void getMarketMap_basis가_afterHours면_시간외_기준으로_전달된다() throws Exception {
        when(marketMapQueryService.getDefaultMarketMap(
                        eq(MarketQuery.KOSPI), isNull(), eq(false), eq(ChangeRateMode.AFTER_HOURS)))
                .thenReturn(MarketMapResponse.empty());

        mockMvc.perform(get("/api/map?market=KOSPI&isCustom=false&basis=afterHours"));

        verify(marketMapQueryService)
                .getDefaultMarketMap(eq(MarketQuery.KOSPI), isNull(), eq(false), eq(ChangeRateMode.AFTER_HOURS));
    }

    @Test
    void getMarketMap_모르는_basis_값은_기본_기준으로_본다() throws Exception {
        when(marketMapQueryService.getDefaultMarketMap(
                        eq(MarketQuery.KOSPI), isNull(), eq(false), eq(ChangeRateMode.DAILY)))
                .thenReturn(MarketMapResponse.empty());

        mockMvc.perform(get("/api/map?market=KOSPI&isCustom=false&basis=whatever"));

        verify(marketMapQueryService)
                .getDefaultMarketMap(eq(MarketQuery.KOSPI), isNull(), eq(false), eq(ChangeRateMode.DAILY));
    }
}
