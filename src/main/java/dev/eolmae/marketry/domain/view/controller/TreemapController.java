package dev.eolmae.marketry.domain.view.controller;

import dev.eolmae.marketry.common.util.KstClock;
import dev.eolmae.marketry.domain.custom.dto.CustomScaleResponse;
import dev.eolmae.marketry.domain.custom.dto.CustomValueTierItem;
import dev.eolmae.marketry.domain.custom.service.CustomScaleService;
import dev.eolmae.marketry.domain.custom.service.CustomSectorService;
import dev.eolmae.marketry.domain.custom.service.CustomValueTierThresholdService;
import dev.eolmae.marketry.domain.view.dto.MarketNoticeResponse;
import dev.eolmae.marketry.domain.view.dto.MarketTradingScheduleResponse;
import dev.eolmae.marketry.domain.view.dto.StockCatalogItem;
import dev.eolmae.marketry.domain.view.dto.TradingDayGapResponse;
import dev.eolmae.marketry.domain.view.dto.TreemapResponse;
import dev.eolmae.marketry.domain.view.dto.TreemapSnapshotDay;
import dev.eolmae.marketry.domain.view.enums.ChangeRateMode;
import dev.eolmae.marketry.domain.view.enums.ClassificationSource;
import dev.eolmae.marketry.domain.view.enums.MarketQuery;
import dev.eolmae.marketry.domain.view.service.MarketNoticeService;
import dev.eolmae.marketry.domain.view.service.MarketTradingScheduleService;
import dev.eolmae.marketry.domain.view.service.TreemapQueryService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/map")
@RestController
@RequiredArgsConstructor
public class TreemapController {

    private final TreemapQueryService treemapQueryService;
    private final CustomSectorService customSectorService;
    private final CustomScaleService customScaleService;
    private final CustomValueTierThresholdService customValueTierThresholdService;
    private final MarketTradingScheduleService marketTradingScheduleService;
    private final MarketNoticeService marketNoticeService;

    @GetMapping
    public TreemapResponse getTreemap(
            @RequestParam MarketQuery market,
            @RequestParam(defaultValue = "false") boolean isCustom,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime snapshotTime,
            @RequestParam(defaultValue = "false") boolean nxtOnly,
            @RequestParam(defaultValue = "daily") String basis) {
        // nxtOnly는 모든 분류에서 NXT 거래 가능 종목만 남긴다.
        // basis=afterHours면 등락률을 그날 정규장 종가 대비로 계산한다(그 스냅샷 날짜의 15:40 이후에만 적용).
        // source가 없으면 옛 프런트 호환으로 isCustom(true=내 분류, false=거래소)을 따른다.
        ChangeRateMode changeRateMode = ChangeRateMode.parse(basis);
        return switch (ClassificationSource.resolve(source, isCustom)) {
            case KRX -> treemapQueryService.getDefaultTreemap(market, snapshotTime, nxtOnly, changeRateMode);
            case MARKETRY -> treemapQueryService.getPublishedTreemap(market, snapshotTime, nxtOnly, changeRateMode);
            case MINE -> treemapQueryService.getCustomTreemap(market, snapshotTime, nxtOnly, changeRateMode);
        };
    }

    /** 홈의 안내 틀에 띄울 그날의 특수한 종목(거래정지로 빠지기 시작한 종목, 신규 상장) — date가 없으면 오늘(KST)이다. */
    @GetMapping("/notices")
    public MarketNoticeResponse getNotices(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return marketNoticeService.getNotices(
                date != null ? date : KstClock.now().toLocalDate());
    }

    /** 달력에 쓰는 날짜 목록 — month는 yyyy-MM. */
    @GetMapping("/snapshot-days")
    public List<TreemapSnapshotDay> getSnapshotDays(
            @RequestParam MarketQuery market, @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return treemapQueryService.getClosingSnapshotDays(market, month);
    }

    /** from(포함하지 않음)~to(포함) 사이의 거래일 수 — 날짜는 yyyy-MM-dd. */
    @GetMapping("/trading-day-gap")
    public TradingDayGapResponse getTradingDayGap(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return treemapQueryService.getTradingDayGap(from, to);
    }

    @GetMapping("/stock-catalog")
    public List<StockCatalogItem> getStockCatalog() {
        return treemapQueryService.getStockCatalog();
    }

    @GetMapping("/trading-schedule")
    public MarketTradingScheduleResponse getTradingSchedule(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return marketTradingScheduleService.getTradingSchedule(date);
    }

    @GetMapping("/value-tiers")
    public List<CustomValueTierItem> getValueTiers() {
        return customValueTierThresholdService.getDefaultValueTiers();
    }

    @GetMapping("/scale")
    public CustomScaleResponse getScale() {
        return customScaleService.getDefaultScale();
    }

    @PostMapping("/excluded-sectors/{sectorId}")
    public void registerExcludedSector(@PathVariable Long sectorId) {
        customSectorService.exclude(sectorId);
    }

    @DeleteMapping("/excluded-sectors/{sectorId}")
    public void unregisterExcludedSector(@PathVariable Long sectorId) {
        customSectorService.include(sectorId);
    }

    @DeleteMapping("/excluded-sectors")
    public void deleteExcludedSectors() {
        customSectorService.resetExcludes();
    }

    @DeleteMapping("/reset")
    public void resetTreemapCustomizations() {
        customSectorService.resetExcludes();
    }
}
