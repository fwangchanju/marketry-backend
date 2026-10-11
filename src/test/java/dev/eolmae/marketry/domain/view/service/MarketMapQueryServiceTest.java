package dev.eolmae.marketry.domain.view.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.auth.enums.Role;
import dev.eolmae.marketry.domain.auth.service.AuthenticatedUserPrincipal;
import dev.eolmae.marketry.domain.custom.entity.CustomSector;
import dev.eolmae.marketry.domain.custom.entity.CustomSnapshot;
import dev.eolmae.marketry.domain.custom.entity.CustomStockSector;
import dev.eolmae.marketry.domain.custom.entity.CustomValueTierThreshold;
import dev.eolmae.marketry.domain.custom.repository.CustomSectorRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomSnapshotRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomStockAliasRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomStockSectorRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomValueTierThresholdRepository;
import dev.eolmae.marketry.domain.custom.service.CustomValueTierThresholdService;
import dev.eolmae.marketry.domain.custom.service.SectorTierAggregationService;
import dev.eolmae.marketry.domain.notification.properties.MarketryProperties;
import dev.eolmae.marketry.domain.stock.entity.IndustryInfo;
import dev.eolmae.marketry.domain.stock.entity.MarketOverviewSnapshot;
import dev.eolmae.marketry.domain.stock.entity.StockInfo;
import dev.eolmae.marketry.domain.stock.repository.IndustryInfoRepository;
import dev.eolmae.marketry.domain.stock.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketry.domain.stock.repository.StockIndustryOverrideRepository;
import dev.eolmae.marketry.domain.stock.service.CalendarDayTimes;
import dev.eolmae.marketry.domain.stock.service.ClosingPriceReader;
import dev.eolmae.marketry.domain.stock.service.ClosingPrices;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarTimeService;
import dev.eolmae.marketry.domain.stock.service.SectorPriceCacheService;
import dev.eolmae.marketry.domain.stock.service.SectorPriceCacheService.CachedStockPrice;
import dev.eolmae.marketry.domain.stock.service.SectorPriceSnapshotService;
import dev.eolmae.marketry.domain.stock.service.StockDailyPriceService;
import dev.eolmae.marketry.domain.stock.service.StockIndustryNameResolver;
import dev.eolmae.marketry.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketry.domain.view.dto.MarketMapResponse;
import dev.eolmae.marketry.domain.view.dto.MarketMapSectorNode;
import dev.eolmae.marketry.domain.view.dto.SectorChangeRateMarketRanking;
import dev.eolmae.marketry.domain.view.dto.SectorRankingSummary;
import dev.eolmae.marketry.domain.view.dto.SnapshotResponse;
import dev.eolmae.marketry.domain.view.dto.StockCatalogItem;
import dev.eolmae.marketry.domain.view.dto.TopSectorItem;
import dev.eolmae.marketry.domain.view.enums.AverageMode;
import dev.eolmae.marketry.domain.view.enums.ChangeRateMode;
import dev.eolmae.marketry.domain.view.enums.MarketQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class MarketMapQueryServiceTest {

    private static final long LEGACY_OWNER_ID = 999999L;
    // 로그인 사용자(LEGACY_OWNER_ID)와 다른 값으로 둬서, 비로그인일 때·MARKETRY일 때 실제로 이 프로퍼티 값을
    // 읽는지(하드코딩된 값이 우연히 일치하는 게 아닌지)를 구분해 검증한다.
    private static final long PUBLISHED_PROPERTY_USER_ID = 555555L;

    private final StockInfoCacheService stockInfoCacheService = Mockito.mock(StockInfoCacheService.class);
    private final SectorPriceSnapshotRepository sectorPriceSnapshotRepository =
            Mockito.mock(SectorPriceSnapshotRepository.class);
    private final SectorPriceCacheService sectorPriceCacheService = Mockito.mock(SectorPriceCacheService.class);
    private final CustomSectorRepository marketMapSectorRepository = Mockito.mock(CustomSectorRepository.class);
    private final CustomStockSectorRepository marketMapStockSectorRepository =
            Mockito.mock(CustomStockSectorRepository.class);
    private final CustomStockAliasRepository customStockAliasRepository =
            Mockito.mock(CustomStockAliasRepository.class);
    private final CustomSnapshotRepository customSnapshotRepository = Mockito.mock(CustomSnapshotRepository.class);
    // 구간 리포지토리 하나를 합산 클래스·구간 서비스가 같이 본다 — 트리 기반 랭킹은 이 둘이 같은 구간
    // 목록을 보는 것을 전제로 한다(합산 클래스는 findAll()로 라벨→id, 구간 서비스는
    // findAllByUserIdOrderByThresholdValueAsc()로 종목의 구간을 정한다).
    private final CustomValueTierThresholdRepository marketValueTierThresholdRepository =
            Mockito.mock(CustomValueTierThresholdRepository.class);
    private final MarketryProperties marketryProperties =
            new MarketryProperties("http://localhost:8081", LEGACY_OWNER_ID, PUBLISHED_PROPERTY_USER_ID);
    // mock 대신 진짜 객체를 쓴다 — resolveTier가 실제로 실행돼야 트리 기반 테스트의 종목이 의도한 구간에
    // 들어간다(5-1).
    private final CustomValueTierThresholdService marketValueTierThresholdService =
            new CustomValueTierThresholdService(marketValueTierThresholdRepository);
    private final SectorTierAggregationService sectorTierAggregationService =
            new SectorTierAggregationService(marketValueTierThresholdService, marketryProperties);
    private final MarketOverviewSnapshotRepository marketOverviewSnapshotRepository =
            Mockito.mock(MarketOverviewSnapshotRepository.class);
    private final IndustryInfoRepository industryInfoRepository = Mockito.mock(IndustryInfoRepository.class);
    private final SectorPriceSnapshotService sectorPriceSnapshotService = new SectorPriceSnapshotService(
            sectorPriceSnapshotRepository,
            Mockito.mock(MarketCalendarTimeService.class),
            Mockito.mock(StockDailyPriceService.class));
    private final ClosingPriceReader closingPriceReader = Mockito.mock(ClosingPriceReader.class);
    private final MarketCalendarTimeService calendarTimeService = Mockito.mock(MarketCalendarTimeService.class);
    private final MarketMapQueryService service = new MarketMapQueryService(
            stockInfoCacheService,
            sectorPriceSnapshotService,
            sectorPriceCacheService,
            marketMapSectorRepository,
            marketMapStockSectorRepository,
            customStockAliasRepository,
            customSnapshotRepository,
            sectorTierAggregationService,
            marketValueTierThresholdService,
            marketOverviewSnapshotRepository,
            new StockIndustryNameResolver(industryInfoRepository, Mockito.mock(StockIndustryOverrideRepository.class)),
            marketryProperties,
            closingPriceReader,
            calendarTimeService);

    // 구간 스텁 공통 셋업 — 진짜 구간 서비스로 바뀌면서 트리를 빌드하는 모든 테스트에 구간이 필요해졌다
    // (5-1). 구간이 여럿 필요한 테스트는 이 기본값을 자기 stubTierThresholds 호출로 덮어쓴다.
    @BeforeEach
    void stubDefaultTier() {
        // 직전 거래일 종가를 구하지 못하는 기본 상태: 전일 종가는 종목 정보 값을 쓴다.
        Mockito.when(closingPriceReader.previousClosingPricesFor(ArgumentMatchers.any(LocalDate.class)))
                .thenAnswer(invocation -> new ClosingPrices(invocation.getArgument(0), Map.of()));
        Mockito.when(calendarTimeService.resolve(Mockito.any(LocalDate.class))).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(0);
            return new CalendarDayTimes(
                    false,
                    date.atTime(8, 0),
                    date.atTime(20, 0),
                    date.atTime(15, 30),
                    date.atTime(15, 30),
                    date.atTime(15, 40));
        });
        var principal = new AuthenticatedUserPrincipal(LEGACY_OWNER_ID, Role.ADMIN);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        Mockito.doAnswer(invocation -> marketMapSectorRepository.findAll())
                .when(marketMapSectorRepository)
                .findAllByUserId(ArgumentMatchers.anyLong());
        Mockito.doAnswer(invocation -> marketMapStockSectorRepository.findAll())
                .when(marketMapStockSectorRepository)
                .findAllByIdUserId(ArgumentMatchers.anyLong());
        Mockito.doReturn(List.of()).when(customStockAliasRepository).findAllByIdUserId(ArgumentMatchers.anyLong());
        stubTierThresholds(tierThreshold(10L, "대형", 0L, false));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 분류별_업데이트는_MARKETRY_수정시각과_거래소_동기화시각을_구분한다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 10, 7, 10, 0);
        LocalDateTime customUpdatedAt = LocalDateTime.of(2026, 10, 6, 18, 0);
        LocalDateTime exchangeUpdatedAt = LocalDateTime.of(2026, 10, 7, 7, 0);
        StockInfo kospi = stockInfo("005930", "삼성전자", 100L, BigDecimal.TEN);
        StockInfo kosdaq = StockInfo.create("035900", "JYP", Market.KOSDAQ, "10", null, 100L, BigDecimal.TEN, false);
        StockInfo inactive = stockInfo("999999", "비활성", 100L, BigDecimal.TEN);
        StockInfo etf = StockInfo.create("069500", "ETF", Market.KOSPI, "8", null, 100L, BigDecimal.TEN, false);
        ReflectionTestUtils.setField(kospi, "updatedAt", exchangeUpdatedAt.minusMinutes(1));
        ReflectionTestUtils.setField(kosdaq, "updatedAt", exchangeUpdatedAt);
        ReflectionTestUtils.setField(inactive, "active", false);
        ReflectionTestUtils.setField(inactive, "updatedAt", exchangeUpdatedAt.plusDays(1));
        ReflectionTestUtils.setField(etf, "updatedAt", exchangeUpdatedAt.plusDays(1));
        when(stockInfoCacheService.getCache())
                .thenReturn(Map.of(
                        kospi.getStockCode(), kospi,
                        kosdaq.getStockCode(), kosdaq,
                        inactive.getStockCode(), inactive,
                        etf.getStockCode(), etf));
        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(
                        Mockito.eq(List.of(Market.KOSPI)), Mockito.any(LocalDate.class)))
                .thenReturn(Optional.of(snapshotTime));
        when(marketMapStockSectorRepository.findLatestUpdatedAtByUserId(PUBLISHED_PROPERTY_USER_ID))
                .thenReturn(customUpdatedAt);

        MarketMapResponse exchange = service.getDefaultMarketMap(MarketQuery.KOSPI, null);
        MarketMapResponse nxt = service.getDefaultMarketMap(MarketQuery.KOSPI, null, true);
        MarketMapResponse custom = service.getPublishedMarketMap(MarketQuery.KOSPI, null, false, ChangeRateMode.DAILY);

        assertThat(exchange.taxonomyUpdatedAt()).isEqualTo(exchangeUpdatedAt);
        assertThat(nxt.taxonomyUpdatedAt()).isEqualTo(exchangeUpdatedAt);
        assertThat(custom.taxonomyUpdatedAt()).isEqualTo(customUpdatedAt);
    }

    @Test
    void MARKETRY_업데이트는_업종과_종목배정_중_더_최근의_고정본_수정시각이다() {
        LocalDateTime assignmentUpdatedAt = LocalDateTime.of(2026, 10, 6, 18, 0);
        LocalDateTime sectorUpdatedAt = LocalDateTime.of(2026, 10, 7, 9, 0);
        CustomSector ownerSector = CustomSector.createParent(PUBLISHED_PROPERTY_USER_ID, "수정된 업종");
        ReflectionTestUtils.setField(ownerSector, "updatedAt", sectorUpdatedAt);
        when(stockInfoCacheService.getCache()).thenReturn(Map.of());
        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(
                        Mockito.eq(List.of(Market.KOSPI)), Mockito.any(LocalDate.class)))
                .thenReturn(Optional.of(sectorUpdatedAt));
        when(marketMapStockSectorRepository.findLatestUpdatedAtByUserId(PUBLISHED_PROPERTY_USER_ID))
                .thenReturn(assignmentUpdatedAt);
        when(marketMapSectorRepository.findFirstByUserIdOrderByUpdatedAtDesc(PUBLISHED_PROPERTY_USER_ID))
                .thenReturn(Optional.of(ownerSector));

        assertThat(service.getPublishedMarketMap(MarketQuery.KOSPI, null, false, ChangeRateMode.DAILY)
                        .taxonomyUpdatedAt())
                .isEqualTo(sectorUpdatedAt);

        LocalDateTime newerAssignmentUpdatedAt = sectorUpdatedAt.plusHours(1);
        when(marketMapStockSectorRepository.findLatestUpdatedAtByUserId(PUBLISHED_PROPERTY_USER_ID))
                .thenReturn(newerAssignmentUpdatedAt);

        assertThat(service.getPublishedMarketMap(MarketQuery.KOSPI, null, false, ChangeRateMode.DAILY)
                        .taxonomyUpdatedAt())
                .isEqualTo(newerAssignmentUpdatedAt);
    }

    @Test
    void MARKETRY_업데이트는_지금_올라간_버전을_올린_시각이라_되돌리면_이전_버전_시각이_된다() {
        LocalDateTime versionCreatedAt = LocalDateTime.of(2026, 10, 6, 9, 0);
        LocalDateTime restoredDataUpdatedAt = LocalDateTime.of(2026, 10, 8, 18, 0);
        CustomSector liveSector = CustomSector.createParent(PUBLISHED_PROPERTY_USER_ID, "되돌린 업종");
        ReflectionTestUtils.setField(liveSector, "updatedAt", restoredDataUpdatedAt);
        ReflectionTestUtils.setField(liveSector, "snapshotId", 7L);
        CustomSnapshot version = CustomSnapshot.create(PUBLISHED_PROPERTY_USER_ID, "MARKETRY 2026-10-06 09:00", "{}");
        ReflectionTestUtils.setField(version, "createdAt", versionCreatedAt);
        when(stockInfoCacheService.getCache()).thenReturn(Map.of());
        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(
                        Mockito.eq(List.of(Market.KOSPI)), Mockito.any(LocalDate.class)))
                .thenReturn(Optional.of(restoredDataUpdatedAt));
        when(marketMapSectorRepository.findFirstByUserIdOrderByIdAsc(PUBLISHED_PROPERTY_USER_ID))
                .thenReturn(Optional.of(liveSector));
        when(marketMapSectorRepository.findFirstByUserIdOrderByUpdatedAtDesc(PUBLISHED_PROPERTY_USER_ID))
                .thenReturn(Optional.of(liveSector));
        when(customSnapshotRepository.findByIdAndUserId(7L, PUBLISHED_PROPERTY_USER_ID))
                .thenReturn(Optional.of(version));

        assertThat(service.getPublishedMarketMap(MarketQuery.KOSPI, null, false, ChangeRateMode.DAILY)
                        .taxonomyUpdatedAt())
                .isEqualTo(versionCreatedAt);
    }

    @Test
    void MARKETRY_업데이트는_종목배정이_없어도_고정본_업종_수정시각을_표시한다() {
        LocalDateTime sectorUpdatedAt = LocalDateTime.of(2026, 10, 7, 9, 0);
        CustomSector ownerSector = CustomSector.createParent(PUBLISHED_PROPERTY_USER_ID, "새 업종");
        ReflectionTestUtils.setField(ownerSector, "updatedAt", sectorUpdatedAt);
        when(stockInfoCacheService.getCache()).thenReturn(Map.of());
        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(
                        Mockito.eq(List.of(Market.KOSPI)), Mockito.any(LocalDate.class)))
                .thenReturn(Optional.of(sectorUpdatedAt));
        when(marketMapSectorRepository.findFirstByUserIdOrderByUpdatedAtDesc(PUBLISHED_PROPERTY_USER_ID))
                .thenReturn(Optional.of(ownerSector));

        assertThat(service.getPublishedMarketMap(MarketQuery.KOSPI, null, false, ChangeRateMode.DAILY)
                        .taxonomyUpdatedAt())
                .isEqualTo(sectorUpdatedAt);
    }

    @Test
    void 신규회원의_배정이_없어도_MARKETRY와_KRX_종목은_유지된다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 10, 9, 10, 0);
        when(stockInfoCacheService.getCache())
                .thenReturn(Map.of("005930", stockInfo("005930", "삼성전자", 100L, BigDecimal.TEN)));
        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(
                        Mockito.eq(List.of(Market.KOSPI)), Mockito.any(LocalDate.class)))
                .thenReturn(Optional.of(snapshotTime));
        when(sectorPriceCacheService.getCache(Market.KOSPI, snapshotTime))
                .thenReturn(Map.ofEntries(priceSnapshot("005930", snapshotTime, BigDecimal.TEN)));
        Mockito.doReturn(List.of()).when(marketMapSectorRepository).findAllByUserId(LEGACY_OWNER_ID);
        Mockito.doReturn(List.of()).when(marketMapStockSectorRepository).findAllByIdUserId(LEGACY_OWNER_ID);
        CustomSector publishedSector = CustomSector.createParent(PUBLISHED_PROPERTY_USER_ID, "공개 반도체");
        ReflectionTestUtils.setField(publishedSector, "id", 1L);
        Mockito.doReturn(List.of(publishedSector))
                .when(marketMapSectorRepository)
                .findAllByUserId(PUBLISHED_PROPERTY_USER_ID);
        Mockito.doReturn(List.of(CustomStockSector.create(PUBLISHED_PROPERTY_USER_ID, "005930", 1L)))
                .when(marketMapStockSectorRepository)
                .findAllByIdUserId(PUBLISHED_PROPERTY_USER_ID);

        MarketMapResponse mine = service.getCustomMarketMap(MarketQuery.KOSPI, null);
        MarketMapResponse published =
                service.getPublishedMarketMap(MarketQuery.KOSPI, null, false, ChangeRateMode.DAILY);
        MarketMapResponse krx = service.getDefaultMarketMap(MarketQuery.KOSPI, null);

        assertThat(mine.items()).isEmpty();
        assertThat(published.items()).hasSize(1);
        assertThat(published.items().get(0).sectorName()).isEqualTo("공개 반도체");
        assertThat(published.items().get(0).items()).extracting("stockCode").containsExactly("005930");
        assertThat(krx.items()).hasSize(1);
        assertThat(krx.items().get(0).items()).extracting("stockCode").containsExactly("005930");
    }

    @Test
    void getPublishedMarketMap_로그인_없이도_발행_사용자_분류로_조회한다() {
        SecurityContextHolder.clearContext();
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 10, 7, 10, 0);
        LocalDateTime publishedAt = LocalDateTime.of(2026, 10, 7, 9, 0);
        when(stockInfoCacheService.getCache()).thenReturn(Map.of());
        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(
                        Mockito.eq(List.of(Market.KOSPI)), Mockito.any(LocalDate.class)))
                .thenReturn(Optional.of(snapshotTime));
        when(marketMapStockSectorRepository.findLatestUpdatedAtByUserId(PUBLISHED_PROPERTY_USER_ID))
                .thenReturn(publishedAt);

        MarketMapResponse response =
                service.getPublishedMarketMap(MarketQuery.KOSPI, null, false, ChangeRateMode.DAILY);

        assertThat(response.taxonomyUpdatedAt()).isEqualTo(publishedAt);
    }

    @Test
    void 내_분류_업데이트는_고정본이_아니라_로그인한_본인의_수정시각이다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 10, 7, 10, 0);
        LocalDateTime myUpdatedAt = LocalDateTime.of(2026, 10, 7, 8, 0);
        when(stockInfoCacheService.getCache()).thenReturn(Map.of());
        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(
                        Mockito.eq(List.of(Market.KOSPI)), Mockito.any(LocalDate.class)))
                .thenReturn(Optional.of(snapshotTime));
        when(marketMapStockSectorRepository.findLatestUpdatedAtByUserId(LEGACY_OWNER_ID))
                .thenReturn(myUpdatedAt);
        when(marketMapStockSectorRepository.findLatestUpdatedAtByUserId(PUBLISHED_PROPERTY_USER_ID))
                .thenReturn(LocalDateTime.of(2026, 10, 7, 9, 0));

        assertThat(service.getCustomMarketMap(MarketQuery.KOSPI, null).taxonomyUpdatedAt())
                .isEqualTo(myUpdatedAt);
    }

    @Test
    void MARKETRY_수정기록이_없으면_다른_사용자의_업종시각으로_대체하지_않는다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 10, 7, 10, 0);
        when(stockInfoCacheService.getCache()).thenReturn(Map.of());
        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(
                        Mockito.eq(List.of(Market.KOSPI)), Mockito.any(LocalDate.class)))
                .thenReturn(Optional.of(snapshotTime));
        when(marketMapSectorRepository.findAll()).thenReturn(List.of(sector(1L, null, "내 업종")));

        assertThat(service.getPublishedMarketMap(MarketQuery.KOSPI, null, false, ChangeRateMode.DAILY)
                        .taxonomyUpdatedAt())
                .isNull();
    }

    @Test
    void 거래소_분류_데이터가_없으면_MARKETRY_수정시각으로_대체하지_않는다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 10, 7, 10, 0);
        when(stockInfoCacheService.getCache()).thenReturn(Map.of());
        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(
                        Mockito.eq(List.of(Market.KOSPI)), Mockito.any(LocalDate.class)))
                .thenReturn(Optional.of(snapshotTime));
        when(marketMapStockSectorRepository.findLatestUpdatedAtByUserId(PUBLISHED_PROPERTY_USER_ID))
                .thenReturn(LocalDateTime.of(2026, 10, 6, 18, 0));

        MarketMapResponse response = service.getDefaultMarketMap(MarketQuery.KOSPI, null);

        assertThat(response.taxonomyUpdatedAt()).isNull();
    }

    @Test
    void customDataUserId_비로그인이면_marketry_published_user_id_프로퍼티_값을_사용한다() {
        SecurityContextHolder.clearContext();

        Long userId = ReflectionTestUtils.invokeMethod(service, "customDataUserId");

        assertThat(userId).isEqualTo(PUBLISHED_PROPERTY_USER_ID);
    }

    @Test
    void getCustomMarketMap_트리집계와_섹터명_매칭이_정확히_반영된다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);

        CustomSector electronics = sector(1L, null, "전기/전자");
        CustomSector semiconductor = sector(2L, 1L, "반도체");
        CustomSector chemical = sector(3L, null, "화학");
        when(marketMapSectorRepository.findAll()).thenReturn(List.of(electronics, semiconductor, chemical));

        when(marketMapStockSectorRepository.findAll())
                .thenReturn(List.of(
                        CustomStockSector.create(LEGACY_OWNER_ID, "005930", 2L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "000660", 2L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "009150", 1L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "051910", 3L)));

        StockInfo samsung = stockInfo("005930", "삼성전자", 100L, BigDecimal.TEN);
        StockInfo skHynix = stockInfo("000660", "SK하이닉스", 50L, BigDecimal.valueOf(20));
        StockInfo lgElectronics = stockInfo("009150", "삼성전기", 200L, BigDecimal.valueOf(5));
        StockInfo lgChem = stockInfo("051910", "LG화학", 500L, BigDecimal.ONE);

        Map<String, StockInfo> stockInfoCache = List.of(samsung, skHynix, lgElectronics, lgChem).stream()
                .collect(Collectors.toMap(StockInfo::getStockCode, Function.identity()));
        when(stockInfoCacheService.getCache()).thenReturn(stockInfoCache);

        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(
                        Mockito.eq(List.of(Market.KOSPI)), Mockito.any(LocalDate.class)))
                .thenReturn(Optional.of(snapshotTime));
        when(sectorPriceCacheService.getCache(Market.KOSPI, snapshotTime))
                .thenReturn(Map.ofEntries(
                        priceSnapshot("005930", snapshotTime, BigDecimal.TEN),
                        priceSnapshot("000660", snapshotTime, BigDecimal.valueOf(20)),
                        priceSnapshot("009150", snapshotTime, BigDecimal.valueOf(5)),
                        priceSnapshot("051910", snapshotTime, BigDecimal.ONE)));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.KOSPI, null);

        assertThat(response.snapshotTime()).isEqualTo(snapshotTime);
        List<MarketMapSectorNode> nodes = response.items();
        assertThat(nodes).hasSize(2);

        MarketMapSectorNode electronicsNode = nodes.stream()
                .filter(node -> node.sectorName().equals("전기/전자"))
                .findFirst()
                .orElseThrow();
        assertThat(electronicsNode.items()).extracting("stockCode").containsExactly("009150");
        assertThat(electronicsNode.children()).hasSize(1);

        MarketMapSectorNode semiconductorNode = electronicsNode.children().get(0);
        assertThat(semiconductorNode.sectorName()).isEqualTo("반도체");
        assertThat(semiconductorNode.items()).extracting("stockCode").containsExactlyInAnyOrder("005930", "000660");
        assertThat(semiconductorNode.totalMarketValue()).isEqualByComparingTo(BigDecimal.valueOf(2000));

        // 전기/전자 총액 = 직속(009150: 5*200=1000) + 자식(반도체: 2000) = 3000
        assertThat(electronicsNode.totalMarketValue()).isEqualByComparingTo(BigDecimal.valueOf(3000));

        MarketMapSectorNode chemicalNode = nodes.stream()
                .filter(node -> node.sectorName().equals("화학"))
                .findFirst()
                .orElseThrow();
        assertThat(chemicalNode.children()).isEmpty();
        assertThat(chemicalNode.items()).extracting("stockCode").containsExactly("051910");
        assertThat(chemicalNode.totalMarketValue()).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    void getCustomMarketMap_nxtOnly면_내_분류에서_NXT_거래_가능_종목만_남긴다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        CustomSector semiconductor = sector(1L, null, "반도체");
        when(marketMapSectorRepository.findAll()).thenReturn(List.of(semiconductor));
        when(marketMapStockSectorRepository.findAll())
                .thenReturn(List.of(
                        CustomStockSector.create(LEGACY_OWNER_ID, "005930", 1L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "000660", 1L)));
        StockInfo samsung = StockInfo.create("005930", "삼성전자", Market.KOSPI, "0", null, 100L, BigDecimal.TEN, true);
        StockInfo skHynix = StockInfo.create("000660", "SK하이닉스", Market.KOSPI, "0", null, 50L, BigDecimal.TEN, false);
        when(stockInfoCacheService.getCache())
                .thenReturn(Map.of(samsung.getStockCode(), samsung, skHynix.getStockCode(), skHynix));
        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(
                        Mockito.eq(List.of(Market.KOSPI)), Mockito.any(LocalDate.class)))
                .thenReturn(Optional.of(snapshotTime));
        when(sectorPriceCacheService.getCache(Market.KOSPI, snapshotTime))
                .thenReturn(Map.ofEntries(
                        priceSnapshot("005930", snapshotTime, BigDecimal.TEN),
                        priceSnapshot("000660", snapshotTime, BigDecimal.TEN)));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        MarketMapResponse nxtOnly = service.getCustomMarketMap(MarketQuery.KOSPI, null, true);
        MarketMapResponse all = service.getCustomMarketMap(MarketQuery.KOSPI, null, false);

        assertThat(nxtOnly.items())
                .flatExtracting(MarketMapSectorNode::items)
                .extracting("stockCode")
                .containsExactly("005930");
        assertThat(all.items())
                .flatExtracting(MarketMapSectorNode::items)
                .extracting("stockCode")
                .containsExactlyInAnyOrder("005930", "000660");
    }

    @Test
    void getDefaultMarketMap_nxtOnly면_NXT_거래_가능_종목만_담는다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        StockInfo samsung = StockInfo.create("005930", "삼성전자", Market.KOSPI, "0", null, 100L, BigDecimal.TEN, true);
        StockInfo lgChem = StockInfo.create("051910", "LG화학", Market.KOSPI, "0", null, 500L, BigDecimal.ONE, false);
        when(stockInfoCacheService.getCache())
                .thenReturn(Map.of(samsung.getStockCode(), samsung, lgChem.getStockCode(), lgChem));
        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(
                        Mockito.eq(List.of(Market.KOSPI)), Mockito.any(LocalDate.class)))
                .thenReturn(Optional.of(snapshotTime));
        when(sectorPriceCacheService.getCache(Market.KOSPI, snapshotTime))
                .thenReturn(Map.ofEntries(
                        priceSnapshot("005930", snapshotTime, BigDecimal.TEN),
                        priceSnapshot("051910", snapshotTime, BigDecimal.ONE)));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        MarketMapResponse nxtOnly = service.getDefaultMarketMap(MarketQuery.KOSPI, null, true);
        MarketMapResponse all = service.getDefaultMarketMap(MarketQuery.KOSPI, null, false);

        assertThat(nxtOnly.items())
                .flatExtracting(MarketMapSectorNode::items)
                .extracting("stockCode")
                .containsExactly("005930");
        assertThat(all.items())
                .flatExtracting(MarketMapSectorNode::items)
                .extracting("stockCode")
                .containsExactlyInAnyOrder("005930", "051910");
    }

    @Test
    void getDefaultMarketMap_industry_info_id로_업종을_찾아_1뎁스_노드로_묶는다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);

        // 기본 마켓맵은 custom_sector를 쓰지 않고 industry_info를 조회한다.
        StockInfo samsung = stockInfo("005930", "삼성전자", 100L, BigDecimal.TEN);
        StockInfo skHynix = stockInfo("000660", "SK하이닉스", 50L, BigDecimal.valueOf(20));
        StockInfo lgChem = stockInfo("051910", "LG화학", 500L, BigDecimal.ONE);
        ReflectionTestUtils.setField(samsung, "industryId", 7L);
        ReflectionTestUtils.setField(skHynix, "industryId", 7L);
        IndustryInfo currentIndustry = IndustryInfo.create("반도체");
        ReflectionTestUtils.setField(currentIndustry, "id", 7L);
        when(industryInfoRepository.findAllById(Mockito.<Iterable<Long>>any())).thenReturn(List.of(currentIndustry));

        Map<String, StockInfo> stockInfoCache = List.of(samsung, skHynix, lgChem).stream()
                .collect(Collectors.toMap(StockInfo::getStockCode, Function.identity()));
        when(stockInfoCacheService.getCache()).thenReturn(stockInfoCache);

        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(
                        Mockito.eq(List.of(Market.KOSPI)), Mockito.any(LocalDate.class)))
                .thenReturn(Optional.of(snapshotTime));
        when(sectorPriceCacheService.getCache(Market.KOSPI, snapshotTime))
                .thenReturn(Map.ofEntries(
                        priceSnapshot("005930", snapshotTime, BigDecimal.TEN),
                        priceSnapshot("000660", snapshotTime, BigDecimal.valueOf(20)),
                        priceSnapshot("051910", snapshotTime, BigDecimal.ONE)));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        MarketMapResponse response = service.getDefaultMarketMap(MarketQuery.KOSPI, null);

        assertThat(response.snapshotTime()).isEqualTo(snapshotTime);
        List<MarketMapSectorNode> nodes = response.items();
        assertThat(nodes).hasSize(2);

        MarketMapSectorNode semiconductorNode = nodes.stream()
                .filter(node -> node.sectorName().equals("반도체"))
                .findFirst()
                .orElseThrow();
        assertThat(semiconductorNode.children()).isEmpty();
        assertThat(semiconductorNode.items()).extracting("stockCode").containsExactlyInAnyOrder("005930", "000660");
        assertThat(semiconductorNode.totalMarketValue()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        assertThat(semiconductorNode.sectorId()).isEqualTo(0L);
        assertThat(semiconductorNode.isExcluded()).isFalse();

        MarketMapSectorNode unsectoredNode = nodes.stream()
                .filter(node -> node.sectorName().equals("미분류"))
                .findFirst()
                .orElseThrow();
        assertThat(unsectoredNode.children()).isEmpty();
        assertThat(unsectoredNode.items()).extracting("stockCode").containsExactly("051910");
        assertThat(unsectoredNode.totalMarketValue()).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    void getCustomMarketMap_제외되었거나_빈_섹터도_필터링없이_그대로_응답된다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);

        CustomSector semiconductor = sector(1L, null, "반도체");
        CustomSector empty = sector(2L, null, "빈섹터");
        empty.exclude();
        when(marketMapSectorRepository.findAll()).thenReturn(List.of(semiconductor, empty));
        when(marketMapStockSectorRepository.findAll())
                .thenReturn(List.of(CustomStockSector.create(LEGACY_OWNER_ID, "005930", 1L)));

        StockInfo samsung = stockInfo("005930", "삼성전자", 100L, BigDecimal.TEN);
        Map<String, StockInfo> stockInfoCache =
                List.of(samsung).stream().collect(Collectors.toMap(StockInfo::getStockCode, Function.identity()));
        when(stockInfoCacheService.getCache()).thenReturn(stockInfoCache);

        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(
                        Mockito.eq(List.of(Market.KOSPI)), Mockito.any(LocalDate.class)))
                .thenReturn(Optional.of(snapshotTime));
        when(sectorPriceCacheService.getCache(Market.KOSPI, snapshotTime))
                .thenReturn(Map.ofEntries(priceSnapshot("005930", snapshotTime, BigDecimal.TEN)));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.KOSPI, null);

        List<MarketMapSectorNode> nodes = response.items();
        assertThat(nodes).hasSize(2);

        MarketMapSectorNode semiconductorNode = nodes.stream()
                .filter(node -> node.sectorName().equals("반도체"))
                .findFirst()
                .orElseThrow();
        assertThat(semiconductorNode.sectorId()).isEqualTo(1L);
        assertThat(semiconductorNode.isExcluded()).isFalse();

        MarketMapSectorNode emptyNode = nodes.stream()
                .filter(node -> node.sectorName().equals("빈섹터"))
                .findFirst()
                .orElseThrow();
        assertThat(emptyNode.sectorId()).isEqualTo(2L);
        assertThat(emptyNode.isExcluded()).isTrue();
        assertThat(emptyNode.items()).isEmpty();
        assertThat(emptyNode.children()).isEmpty();
    }

    @Test
    void getCustomMarketMap_자식_섹터가_있는_노드의_총액은_자기_items와_자식_합계다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);

        CustomSector parent = sector(1L, null, "전기/전자");
        CustomSector child = sector(2L, 1L, "반도체");
        when(marketMapSectorRepository.findAll()).thenReturn(List.of(parent, child));
        when(marketMapStockSectorRepository.findAll())
                .thenReturn(List.of(
                        CustomStockSector.create(LEGACY_OWNER_ID, "009150", 1L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "005930", 2L)));

        StockInfo lgElectronics = stockInfo("009150", "삼성전기", 200L, BigDecimal.valueOf(5));
        StockInfo samsung = stockInfo("005930", "삼성전자", 100L, BigDecimal.TEN);
        Map<String, StockInfo> stockInfoCache = List.of(lgElectronics, samsung).stream()
                .collect(Collectors.toMap(StockInfo::getStockCode, Function.identity()));
        when(stockInfoCacheService.getCache()).thenReturn(stockInfoCache);

        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(
                        Mockito.eq(List.of(Market.KOSPI)), Mockito.any(LocalDate.class)))
                .thenReturn(Optional.of(snapshotTime));
        when(sectorPriceCacheService.getCache(Market.KOSPI, snapshotTime))
                .thenReturn(Map.ofEntries(
                        priceSnapshot("009150", snapshotTime, BigDecimal.valueOf(5)),
                        priceSnapshot("005930", snapshotTime, BigDecimal.TEN)));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.KOSPI, null);

        MarketMapSectorNode parentNode = response.items().stream()
                .filter(node -> node.sectorName().equals("전기/전자"))
                .findFirst()
                .orElseThrow();
        // 직속(009150: 5*200=1000) + 자식(반도체: 10*100=1000) = 2000
        assertThat(parentNode.totalMarketValue()).isEqualByComparingTo(BigDecimal.valueOf(2000));
    }

    @Test
    void getCustomMarketMap_가격_스냅샷이_없는_종목은_제외된다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);

        CustomSector semiconductor = sector(1L, null, "반도체");
        when(marketMapSectorRepository.findAll()).thenReturn(List.of(semiconductor));
        when(marketMapStockSectorRepository.findAll())
                .thenReturn(List.of(
                        CustomStockSector.create(LEGACY_OWNER_ID, "005930", 1L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "000660", 1L)));

        StockInfo samsung = stockInfo("005930", "삼성전자", 100L, BigDecimal.TEN);
        StockInfo skHynix = stockInfo("000660", "SK하이닉스", 50L, BigDecimal.valueOf(20));
        Map<String, StockInfo> stockInfoCache = List.of(samsung, skHynix).stream()
                .collect(Collectors.toMap(StockInfo::getStockCode, Function.identity()));
        when(stockInfoCacheService.getCache()).thenReturn(stockInfoCache);

        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(
                        Mockito.eq(List.of(Market.KOSPI)), Mockito.any(LocalDate.class)))
                .thenReturn(Optional.of(snapshotTime));
        // 000660은 가격 스냅샷이 없다 — 수집 gap 등으로 그 시각에 데이터가 아예 없는 경우
        when(sectorPriceCacheService.getCache(Market.KOSPI, snapshotTime))
                .thenReturn(Map.ofEntries(priceSnapshot("005930", snapshotTime, BigDecimal.TEN)));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.KOSPI, null);

        MarketMapSectorNode semiconductorNode = response.items().stream()
                .filter(node -> node.sectorName().equals("반도체"))
                .findFirst()
                .orElseThrow();
        assertThat(semiconductorNode.items()).extracting("stockCode").containsExactly("005930");
    }

    @Test
    void getCustomMarketMap_가격이_있어도_미배정_종목은_표시하지_않는다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        when(marketMapSectorRepository.findAll()).thenReturn(List.of(sector(1L, null, "반도체")));
        when(marketMapStockSectorRepository.findAll())
                .thenReturn(List.of(CustomStockSector.create(LEGACY_OWNER_ID, "005930", 1L)));
        when(stockInfoCacheService.getCache())
                .thenReturn(Map.of(
                        "005930", stockInfo("005930", "삼성전자", 100L, BigDecimal.TEN),
                        "000660", stockInfo("000660", "SK하이닉스", 50L, BigDecimal.valueOf(20))));
        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(
                        Mockito.eq(List.of(Market.KOSPI)), Mockito.any(LocalDate.class)))
                .thenReturn(Optional.of(snapshotTime));
        when(sectorPriceCacheService.getCache(Market.KOSPI, snapshotTime))
                .thenReturn(Map.ofEntries(
                        priceSnapshot("005930", snapshotTime, BigDecimal.TEN),
                        priceSnapshot("000660", snapshotTime, BigDecimal.valueOf(20))));

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.KOSPI, null);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).items()).extracting("stockCode").containsExactly("005930");
        assertThat(response.items().get(0).totalMarketValue()).isEqualByComparingTo(BigDecimal.valueOf(1000));
    }

    @Test
    void getCustomMarketMap_섹터가_하나도_없으면_빈_리스트를_반환한다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);

        when(marketMapSectorRepository.findAll()).thenReturn(List.of());
        when(marketMapStockSectorRepository.findAll()).thenReturn(List.of());
        when(stockInfoCacheService.getCache())
                .thenReturn(Map.of("005930", stockInfo("005930", "삼성전자", 100L, BigDecimal.TEN)));
        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(
                        Mockito.eq(List.of(Market.KOSPI)), Mockito.any(LocalDate.class)))
                .thenReturn(Optional.of(snapshotTime));
        when(sectorPriceCacheService.getCache(Market.KOSPI, snapshotTime))
                .thenReturn(Map.ofEntries(priceSnapshot("005930", snapshotTime, BigDecimal.TEN)));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.KOSPI, null);

        assertThat(response.items()).isEmpty();
    }

    @Test
    void getCustomMarketMap_그_시각에_지수_스냅샷이_있으면_marketOverview로_붙는다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);

        when(marketMapSectorRepository.findAll()).thenReturn(List.of());
        when(marketMapStockSectorRepository.findAll()).thenReturn(List.of());
        when(stockInfoCacheService.getCache()).thenReturn(Map.of());
        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(
                        Mockito.eq(List.of(Market.KOSPI)), Mockito.any(LocalDate.class)))
                .thenReturn(Optional.of(snapshotTime));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime))
                .thenReturn(List.of(marketOverviewSnapshot(Market.KOSPI, snapshotTime, BigDecimal.valueOf(1.23))));

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.KOSPI, null);

        assertThat(response.marketOverview()).isNotNull();
        assertThat(response.marketOverview().market()).isEqualTo(Market.KOSPI);
        assertThat(response.marketOverview().changeRate()).isEqualByComparingTo(BigDecimal.valueOf(1.23));
    }

    @Test
    void getCustomMarketMap_마켓이_여럿이면_단일_지수값이_없어_marketOverview가_null이다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        List<Market> markets = List.of(Market.KOSPI, Market.KOSDAQ);

        when(marketMapSectorRepository.findAll()).thenReturn(List.of());
        when(marketMapStockSectorRepository.findAll()).thenReturn(List.of());
        when(stockInfoCacheService.getCache()).thenReturn(Map.of());
        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(
                        Mockito.eq(markets), Mockito.any(LocalDate.class)))
                .thenReturn(Optional.of(snapshotTime));

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.ALL_STOCK, null);

        // 마켓이 여럿이면 합쳐서 보여줄 단일 지수값이 없으므로, 지수 스냅샷 자체를 조회하지 않고 곧장 null.
        assertThat(response.marketOverview()).isNull();
        Mockito.verifyNoInteractions(marketOverviewSnapshotRepository);
    }

    @Test
    void getCustomMarketMap_snapshotTime을_명시하면_최신이_아니라_그_시각_그대로_쓴다() {
        LocalDateTime requestedTime = LocalDateTime.of(2026, 7, 31, 10, 5);
        when(marketMapSectorRepository.findAll()).thenReturn(List.of());
        when(marketMapStockSectorRepository.findAll()).thenReturn(List.of());
        when(stockInfoCacheService.getCache()).thenReturn(Map.of());
        when(sectorPriceSnapshotRepository.existsByMarketTypeAndSnapshotTime(Market.KOSPI, requestedTime))
                .thenReturn(true);
        when(marketOverviewSnapshotRepository.findBySnapshotTime(requestedTime)).thenReturn(List.of());

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.KOSPI, requestedTime);

        assertThat(response.snapshotTime()).isEqualTo(requestedTime);
        // 명시한 시각이 있으면 "최신 공통 시각" 조회는 부르지 않는다 — 가까운 시각으로 대체하지 않는다.
        Mockito.verify(sectorPriceSnapshotRepository, Mockito.never())
                .findLatestCommonSnapshotTime(Mockito.anyList(), Mockito.any(LocalDate.class));
    }

    // ALL_STOCK인데 그 시각에 코스피만 있고 코스닥은 없는 경우 — 반쪽 트리를 내려주면 안 된다(결정 4).
    @Test
    void getCustomMarketMap_snapshotTime에_요청_마켓_중_하나라도_없으면_빈_응답이다() {
        LocalDateTime requestedTime = LocalDateTime.of(2026, 7, 31, 10, 5);
        when(sectorPriceSnapshotRepository.existsByMarketTypeAndSnapshotTime(Market.KOSPI, requestedTime))
                .thenReturn(true);
        when(sectorPriceSnapshotRepository.existsByMarketTypeAndSnapshotTime(Market.KOSDAQ, requestedTime))
                .thenReturn(false);

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.ALL_STOCK, requestedTime);

        assertThat(response).isEqualTo(MarketMapResponse.empty());
    }

    @Test
    void getSectorChangeRates_랭킹과_같은_시각의_지수_등락률이_마켓별로_붙는다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        CustomSector semiconductor = sector(1L, null, "반도체");
        CustomSector pharma = sector(2L, null, "제약");
        stubSectorTree(
                List.of(semiconductor, pharma),
                List.of(
                        CustomStockSector.create(LEGACY_OWNER_ID, "A", 1L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "B", 2L)));
        stubStockCache(stock("A", Market.KOSPI), stock("B", Market.KOSDAQ));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));
        stubPrices(
                Market.KOSDAQ,
                snapshotTime,
                price("B", Market.KOSDAQ, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime))
                .thenReturn(List.of(
                        marketOverviewSnapshot(Market.KOSPI, snapshotTime, BigDecimal.valueOf(1.23)),
                        marketOverviewSnapshot(Market.KOSDAQ, snapshotTime, BigDecimal.valueOf(-0.45))));
        // beforeMinutes=60이므로 09:00 시각도 별도로 조회한다 — now/before 둘 다 값이 있는 일반적인 경우.
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime.minusMinutes(60)))
                .thenReturn(List.of(
                        marketOverviewSnapshot(Market.KOSPI, snapshotTime.minusMinutes(60), BigDecimal.valueOf(0.98)),
                        marketOverviewSnapshot(
                                Market.KOSDAQ, snapshotTime.minusMinutes(60), BigDecimal.valueOf(-0.20))));

        SnapshotResponse<SectorChangeRateMarketRanking> response =
                service.getSectorChangeRates(MarketQuery.ALL_STOCK, snapshotTime, 60);

        SectorChangeRateMarketRanking kospi = response.items().stream()
                .filter(ranking -> ranking.market() == Market.KOSPI)
                .findFirst()
                .orElseThrow();
        assertThat(kospi.index().now()).isEqualByComparingTo(BigDecimal.valueOf(1.23));
        assertThat(kospi.index().before()).isEqualByComparingTo(BigDecimal.valueOf(0.98));

        SectorChangeRateMarketRanking kosdaq = response.items().stream()
                .filter(ranking -> ranking.market() == Market.KOSDAQ)
                .findFirst()
                .orElseThrow();
        assertThat(kosdaq.index().now()).isEqualByComparingTo(BigDecimal.valueOf(-0.45));
        assertThat(kosdaq.index().before()).isEqualByComparingTo(BigDecimal.valueOf(-0.20));
    }

    @Test
    void getSectorChangeRates_그_시각에_지수_스냅샷이_없으면_index가_null이다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        CustomSector semiconductor = sector(1L, null, "반도체");
        stubSectorTree(List.of(semiconductor), List.of(CustomStockSector.create(LEGACY_OWNER_ID, "A", 1L)));
        stubStockCache(stock("A", Market.KOSPI));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));
        // 이번 수집 주기에 지수기여도랭킹 수집만 실패해서, 섹터 랭킹은 있는데 지수 스냅샷은 그 시각에
        // 없는 경우 — 다른 시각 값으로 조용히 대체하지 않고 index 전체가 null로 내려간다.
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        SnapshotResponse<SectorChangeRateMarketRanking> response =
                service.getSectorChangeRates(MarketQuery.KOSPI, snapshotTime, 60);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).index()).isNull();
    }

    @Test
    void getSectorChangeRates_before_시각에_지수_스냅샷이_없으면_index_before가_null이다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        CustomSector semiconductor = sector(1L, null, "반도체");
        stubSectorTree(List.of(semiconductor), List.of(CustomStockSector.create(LEGACY_OWNER_ID, "A", 1L)));
        stubStockCache(stock("A", Market.KOSPI));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));
        // now 시각(10:00)에는 지수 스냅샷이 있지만, before 시각(09:00, beforeMinutes=60)에는 없는
        // 경우 — 장 시작 직후나 수집 gap. 가까운 다른 시점 값으로 대체하지 않고 before만 null이다.
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime))
                .thenReturn(List.of(marketOverviewSnapshot(Market.KOSPI, snapshotTime, BigDecimal.valueOf(1.23))));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime.minusMinutes(60)))
                .thenReturn(List.of());

        SnapshotResponse<SectorChangeRateMarketRanking> response =
                service.getSectorChangeRates(MarketQuery.KOSPI, snapshotTime, 60);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).index().now()).isEqualByComparingTo(BigDecimal.valueOf(1.23));
        assertThat(response.items().get(0).index().before()).isNull();
    }

    @Test
    void getTopSectorRankings_자식_섹터는_랭킹에서_제외된다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        LocalDateTime beforeTime = snapshotTime.minusMinutes(60);
        CustomSector root = sector(1L, null, "반도체");
        CustomSector child = sector(2L, 1L, "반도체 소재");
        stubSectorTree(
                List.of(root, child),
                List.of(
                        CustomStockSector.create(LEGACY_OWNER_ID, "A1", 1L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "A2", 2L)));
        stubStockCache(stock("A1", Market.KOSPI), stock("A2", Market.KOSPI));
        // child(A2)가 root보다 등락률이 훨씬 높아도(부모 집계에 재귀로 포함되긴 하지만), 대분류가
        // 아니므로 결과에 별도로 나오면 안 된다.
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A1", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(5)),
                price("A2", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(90)));
        stubPrices(
                Market.KOSPI,
                beforeTime,
                price("A1", Market.KOSPI, beforeTime, BigDecimal.valueOf(10_000), BigDecimal.ZERO),
                price("A2", Market.KOSPI, beforeTime, BigDecimal.valueOf(10_000), BigDecimal.ZERO));

        List<SectorRankingSummary> summaries =
                service.getTopSectorRankings(MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).topSectors())
                .extracting(TopSectorItem::sectorName)
                .containsExactly("반도체");
    }

    @Test
    void getTopSectorRankings_TOP2까지만_등락률_내림차순으로_노출된다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        LocalDateTime beforeTime = snapshotTime.minusMinutes(60);
        CustomSector a = sector(1L, null, "반도체");
        CustomSector b = sector(2L, null, "화학");
        CustomSector c = sector(3L, null, "자동차");
        stubSectorTree(
                List.of(a, b, c),
                List.of(
                        CustomStockSector.create(LEGACY_OWNER_ID, "A", 1L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "B", 2L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "C", 3L)));
        stubStockCache(stock("A", Market.KOSPI), stock("B", Market.KOSPI), stock("C", Market.KOSPI));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN), // +10%p
                price("B", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(5)), // +5%p
                price(
                        "C",
                        Market.KOSPI,
                        snapshotTime,
                        BigDecimal.valueOf(10_000),
                        BigDecimal.valueOf(2))); // +2%p, 3위라 빠져야 함
        stubPrices(
                Market.KOSPI,
                beforeTime,
                price("A", Market.KOSPI, beforeTime, BigDecimal.valueOf(10_000), BigDecimal.ZERO),
                price("B", Market.KOSPI, beforeTime, BigDecimal.valueOf(10_000), BigDecimal.ZERO),
                price("C", Market.KOSPI, beforeTime, BigDecimal.valueOf(10_000), BigDecimal.ZERO));

        List<SectorRankingSummary> summaries =
                service.getTopSectorRankings(MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);

        assertThat(summaries.get(0).topSectors())
                .extracting(TopSectorItem::sectorName)
                .containsExactly("반도체", "화학");
    }

    // before가 없는 것을 0으로 치면 now가 그대로 델타가 되어, 실제로는 계산할 수 없는 섹터가
    // 1위로 올라온다. 순위에서 빼는 것이 맞다.
    @Test
    void getTopSectorRankings_before가_없는_섹터는_랭킹에서_빠진다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        LocalDateTime beforeTime = snapshotTime.minusMinutes(60);
        CustomSector a = sector(1L, null, "반도체");
        CustomSector b = sector(2L, null, "화학");
        stubSectorTree(
                List.of(a, b),
                List.of(
                        CustomStockSector.create(LEGACY_OWNER_ID, "A", 1L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "B", 2L)));
        stubStockCache(stock("A", Market.KOSPI), stock("B", Market.KOSPI));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(90)),
                price("B", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(5)));
        // before: A는 가격 행 자체가 없다(before 없음), B만 0%로 존재한다.
        stubPrices(
                Market.KOSPI,
                beforeTime,
                price("B", Market.KOSPI, beforeTime, BigDecimal.valueOf(10_000), BigDecimal.ZERO));

        List<SectorRankingSummary> summaries =
                service.getTopSectorRankings(MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);

        assertThat(summaries.get(0).topSectors())
                .extracting(TopSectorItem::sectorName)
                .containsExactly("화학");
    }

    // 그 시각 스냅샷이 통째로 없는 경우 — 매일 첫 발송(08:10의 before는 07:55인데 수집은 08:00부터)이
    // 여기 걸린다. 캡션 쪽에서 안내 문구로 바꿔 내보낸다.
    @Test
    void getTopSectorRankings_before가_전부_없으면_빈_목록이_된다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        CustomSector a = sector(1L, null, "반도체");
        stubSectorTree(List.of(a), List.of(CustomStockSector.create(LEGACY_OWNER_ID, "A", 1L)));
        stubStockCache(stock("A", Market.KOSPI));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));
        // before 시각은 존재 자체를 스텁하지 않는다 — 그 시각 스냅샷이 통째로 없는 경우.

        List<SectorRankingSummary> summaries =
                service.getTopSectorRankings(MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);

        assertThat(summaries.get(0).topSectors()).isEmpty();
    }

    @Test
    void getTopSectorRankings_기본_제외_구간은_평균_계산에서_빠진다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        LocalDateTime beforeTime = snapshotTime.minusMinutes(60);
        CustomSector root = sector(1L, null, "반도체");
        // 구간은 시가총액으로 정해진다 — 대형 1종목(시총 10,000, +10%)과 소형 4종목(합계 시총 10,000,
        // 각 -50%)으로 나눈다. 소형은 기본 제외 구간이라 평균에서 빠지면 +10%만 남는다.
        stubTierThresholds(tierThreshold(20L, "소형", 0L, true), tierThreshold(10L, "대형", 5_000L, false));
        stubSectorTree(
                List.of(root),
                List.of(
                        CustomStockSector.create(LEGACY_OWNER_ID, "BIG", 1L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "S1", 1L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "S2", 1L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "S3", 1L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "S4", 1L)));
        stubStockCache(
                stock("BIG", Market.KOSPI),
                stock("S1", Market.KOSPI),
                stock("S2", Market.KOSPI),
                stock("S3", Market.KOSPI),
                stock("S4", Market.KOSPI));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("BIG", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN),
                price("S1", Market.KOSPI, snapshotTime, BigDecimal.valueOf(2_500), BigDecimal.valueOf(-50)),
                price("S2", Market.KOSPI, snapshotTime, BigDecimal.valueOf(2_500), BigDecimal.valueOf(-50)),
                price("S3", Market.KOSPI, snapshotTime, BigDecimal.valueOf(2_500), BigDecimal.valueOf(-50)),
                price("S4", Market.KOSPI, snapshotTime, BigDecimal.valueOf(2_500), BigDecimal.valueOf(-50)));
        stubPrices(
                Market.KOSPI,
                beforeTime,
                price("BIG", Market.KOSPI, beforeTime, BigDecimal.valueOf(10_000), BigDecimal.ZERO),
                price("S1", Market.KOSPI, beforeTime, BigDecimal.valueOf(2_500), BigDecimal.ZERO),
                price("S2", Market.KOSPI, beforeTime, BigDecimal.valueOf(2_500), BigDecimal.ZERO),
                price("S3", Market.KOSPI, beforeTime, BigDecimal.valueOf(2_500), BigDecimal.ZERO),
                price("S4", Market.KOSPI, beforeTime, BigDecimal.valueOf(2_500), BigDecimal.ZERO));

        List<SectorRankingSummary> summaries =
                service.getTopSectorRankings(MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);

        assertThat(summaries.get(0).topSectors().get(0).changeRate()).isEqualByComparingTo(BigDecimal.TEN);
    }

    // 델타(now-before) 기준과 등락률(now) 기준이 서로 다른 섹터를 뽑을 수 있음을 보인다 — 08:10
    // 폴백이 델타 대신 이 랭킹을 쓰는 이유다.
    @Test
    void getTopSectorRankingsByChangeRate_등락률_기준으로_델타_기준과_다른_섹터를_뽑는다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        LocalDateTime beforeTime = snapshotTime.minusMinutes(60);
        CustomSector a = sector(1L, null, "반도체");
        CustomSector b = sector(2L, null, "화학");
        CustomSector c = sector(3L, null, "자동차");
        stubSectorTree(
                List.of(a, b, c),
                List.of(
                        CustomStockSector.create(LEGACY_OWNER_ID, "A", 1L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "B", 2L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "C", 3L)));
        stubStockCache(stock("A", Market.KOSPI), stock("B", Market.KOSPI), stock("C", Market.KOSPI));
        // a: now +20%, before +19% → 델타 +1%p. b: now +5%, before -10% → 델타 +15%p.
        // c: now +10%, before +8% → 델타 +2%p.
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(20)),
                price("B", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(5)),
                price("C", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));
        stubPrices(
                Market.KOSPI,
                beforeTime,
                price("A", Market.KOSPI, beforeTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(19)),
                price("B", Market.KOSPI, beforeTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(-10)),
                price("C", Market.KOSPI, beforeTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(8)));

        List<SectorRankingSummary> deltaRankings =
                service.getTopSectorRankings(MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);
        List<SectorRankingSummary> changeRateRankings = service.getTopSectorRankingsByChangeRate(
                MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);

        assertThat(deltaRankings.get(0).topSectors())
                .extracting(TopSectorItem::sectorName)
                .containsExactly("화학", "자동차");
        assertThat(changeRateRankings.get(0).topSectors())
                .extracting(TopSectorItem::sectorName)
                .containsExactly("반도체", "자동차");
    }

    @Test
    void getMergedTopSectorRanking_두_마켓의_원시값을_합친_기준으로_TOP2를_뽑는다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        CustomSector a = sector(1L, null, "반도체");
        CustomSector b = sector(2L, null, "화학");
        CustomSector c = sector(3L, null, "자동차");
        stubSectorTree(
                List.of(a, b, c),
                List.of(
                        CustomStockSector.create(LEGACY_OWNER_ID, "A1", 1L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "A2", 1L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "B1", 2L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "B2", 2L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "C1", 3L)));
        stubStockCache(
                stock("A1", Market.KOSPI),
                stock("A2", Market.KOSDAQ),
                stock("B1", Market.KOSPI),
                stock("B2", Market.KOSDAQ),
                stock("C1", Market.KOSPI));
        // a는 KOSPI 단독으로 보면 압도적 1위(+100%)지만 KOSDAQ에서 -90%라 합치면 +5%로 밀린다.
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A1", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(100)),
                price("B1", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(8)),
                price("C1", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(6)));
        stubPrices(
                Market.KOSDAQ,
                snapshotTime,
                price("A2", Market.KOSDAQ, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(-90)),
                price("B2", Market.KOSDAQ, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(8)));

        List<TopSectorItem> merged =
                service.getMergedTopSectorRanking(MarketQuery.ALL_STOCK, snapshotTime, AverageMode.WEIGHTED, false);

        // 병합 평균: a=(1,000,000-900,000)/20,000=+5%, b=(80,000+80,000)/20,000=+8%, c=60,000/10,000=+6%
        assertThat(merged).extracting(TopSectorItem::sectorName).containsExactly("화학", "자동차");
    }

    @Test
    void getMergedTopSectorRanking_원시값을_합산한_뒤_한_번만_나눈다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        CustomSector a = sector(1L, null, "반도체");
        stubSectorTree(
                List.of(a),
                List.of(
                        CustomStockSector.create(LEGACY_OWNER_ID, "A1", 1L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "A2", 1L)));
        stubStockCache(stock("A1", Market.KOSPI), stock("A2", Market.KOSDAQ));
        // KOSPI 시총 10,000에 +10%p, KOSDAQ 시총 40,000에 -2%p — 단순 평균이면 (10-2)/2=+4가 되지만,
        // KOSDAQ 쪽 시총 비중이 훨씬 커서 원시값을 합산한 뒤 나누면 +0.4가 맞다.
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A1", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));
        stubPrices(
                Market.KOSDAQ,
                snapshotTime,
                price("A2", Market.KOSDAQ, snapshotTime, BigDecimal.valueOf(40_000), BigDecimal.valueOf(-2)));

        List<TopSectorItem> merged =
                service.getMergedTopSectorRanking(MarketQuery.ALL_STOCK, snapshotTime, AverageMode.WEIGHTED, false);

        assertThat(merged.get(0).changeRate()).isEqualByComparingTo(BigDecimal.valueOf(0.4));
    }

    // 등락률 수집만 실패한 tick이 이 모양이다 — 맵 페이지는 sector_price_snapshot으로 그려져서 멀쩡히
    // 나오는데 여기는 빈 목록이 된다. 호출부(MarketMapAlbumReportSender)가 이걸 "캡처할 마켓이 없다"로
    // 읽으면 한 장도 안 찍고 에스컬레이션하므로, 빈 목록이 정상 반환이라는 것을 못박아둔다.
    @Test
    void getMergedTopSectorRanking_그_시각_스냅샷이_없으면_빈_목록이다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        stubSectorTree(List.of(sector(1L, null, "반도체")), List.of());
        // KOSPI·KOSDAQ 둘 다 그 시각 스냅샷 자체를 스텁하지 않는다 — 캡처가 아예 안 된 tick.

        assertThat(service.getMergedTopSectorRanking(MarketQuery.ALL_STOCK, snapshotTime, AverageMode.WEIGHTED, false))
                .isEmpty();
    }

    // 결정 2가 새로 더한 규칙 — 한 마켓만 데이터가 없어도(다른 마켓은 멀쩡해도) 전체가 빈 목록이어야
    // 한다. 위 테스트(둘 다 없음)만으로는 "하나라도"인지 "전부"인지 구분이 안 된다.
    @Test
    void getMergedTopSectorRanking_한_마켓만_합산이_비어도_빈_목록이다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        CustomSector a = sector(1L, null, "반도체");
        stubSectorTree(List.of(a), List.of(CustomStockSector.create(LEGACY_OWNER_ID, "A1", 1L)));
        stubStockCache(stock("A1", Market.KOSPI));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A1", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));
        // KOSDAQ은 그 시각 스냅샷 자체가 없다 — KOSPI만 있어도 전체가 빈 목록이어야 한다.

        assertThat(service.getMergedTopSectorRanking(MarketQuery.ALL_STOCK, snapshotTime, AverageMode.WEIGHTED, false))
                .isEmpty();
    }

    // 맵 앨범 캡션(getMergedTopSectorRanking)에서도 섹터 제외 on/off로 TOP2가 달라진다 — 지시서 결정
    // 3이 고치는 두 캡션 경로(섹터/맵) 중 나머지 하나.
    @Test
    void getMergedTopSectorRanking_섹터_제외를_켜면_isExcluded_섹터가_빠진다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        CustomSector excluded = sector(1L, null, "반도체");
        excluded.exclude();
        CustomSector included = sector(2L, null, "화학");
        stubSectorTree(
                List.of(excluded, included),
                List.of(
                        CustomStockSector.create(LEGACY_OWNER_ID, "A1", 1L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "B1", 2L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "B2", 2L)));
        stubStockCache(stock("A1", Market.KOSPI), stock("B1", Market.KOSPI), stock("B2", Market.KOSDAQ));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A1", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(90)),
                price("B1", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(5)));
        // KOSDAQ에 아무 종목도 없으면 "하나라도 비면 빈 목록" 규칙에 걸려 전체가 빈다 — 화학에 KOSDAQ
        // 종목을 하나 더한다. 등락률이 같아(+5%) 병합 뒤 값은 그대로다.
        stubPrices(
                Market.KOSDAQ,
                snapshotTime,
                price("B2", Market.KOSDAQ, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(5)));

        List<TopSectorItem> filtered =
                service.getMergedTopSectorRanking(MarketQuery.ALL_STOCK, snapshotTime, AverageMode.WEIGHTED, true);
        List<TopSectorItem> unfiltered =
                service.getMergedTopSectorRanking(MarketQuery.ALL_STOCK, snapshotTime, AverageMode.WEIGHTED, false);

        assertThat(filtered).extracting(TopSectorItem::sectorName).containsExactly("화학");
        assertThat(unfiltered).extracting(TopSectorItem::sectorName).containsExactlyInAnyOrder("반도체", "화학");
    }

    // 가중평균과 산술평균이 실제로 다른 값이 나오는 것을 보여준다 — 종목 하나짜리 픽스처는 itemCount가
    // 항상 1이라 두 평균이 우연히 같아서 이 분기를 검증하지 못한다.
    @Test
    void getTopSectorRankingsByChangeRate_평균_방식에_따라_결과가_달라진다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        CustomSector a = sector(1L, null, "반도체");
        stubSectorTree(
                List.of(a),
                List.of(
                        CustomStockSector.create(LEGACY_OWNER_ID, "BIG", 1L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "SMALL", 1L)));
        stubStockCache(stock("BIG", Market.KOSPI), stock("SMALL", Market.KOSPI));
        // 시총 90,000짜리 종목 +30%, 시총 10,000짜리 종목 +10% — 가중평균은 시총이 큰 쪽에 끌려 +28%,
        // 산술평균은 종목당 등락률을 그대로 평균내 +20%.
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("BIG", Market.KOSPI, snapshotTime, BigDecimal.valueOf(90_000), BigDecimal.valueOf(30)),
                price("SMALL", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));

        List<SectorRankingSummary> weighted = service.getTopSectorRankingsByChangeRate(
                MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);
        List<SectorRankingSummary> simple = service.getTopSectorRankingsByChangeRate(
                MarketQuery.KOSPI, snapshotTime, 60, AverageMode.SIMPLE, false);

        assertThat(weighted.get(0).topSectors().get(0).changeRate()).isEqualByComparingTo(BigDecimal.valueOf(28));
        assertThat(simple.get(0).topSectors().get(0).changeRate()).isEqualByComparingTo(BigDecimal.valueOf(20));
    }

    // 평상시(매 tick) 경로 — getTopSectorRankings → toTopSectorItem은 avgOf를 now·before 두 번
    // 불러 그 차이를 쓴다. ByChangeRate 폴백 경로만 덮으면 두 호출 중 하나가 다른 모드를 써도 못 잡는다.
    @Test
    void getTopSectorRankings_평균_방식에_따라_델타_결과가_달라진다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        LocalDateTime beforeTime = snapshotTime.minusMinutes(60);
        CustomSector a = sector(1L, null, "반도체");
        stubSectorTree(
                List.of(a),
                List.of(
                        CustomStockSector.create(LEGACY_OWNER_ID, "BIG", 1L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "SMALL", 1L)));
        stubStockCache(stock("BIG", Market.KOSPI), stock("SMALL", Market.KOSPI));
        // now:    시총 90,000 +30% / 10,000 +10%  → 가중 +28%, 산술 +20%
        // before: 시총 80,000 +10% / 20,000   0%  → 가중  +8%, 산술  +5%
        // → 가중 델타는 +20%p, 산술 델타는 +15%p로 서로 다르다.
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("BIG", Market.KOSPI, snapshotTime, BigDecimal.valueOf(90_000), BigDecimal.valueOf(30)),
                price("SMALL", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));
        stubPrices(
                Market.KOSPI,
                beforeTime,
                price("BIG", Market.KOSPI, beforeTime, BigDecimal.valueOf(80_000), BigDecimal.TEN),
                price("SMALL", Market.KOSPI, beforeTime, BigDecimal.valueOf(20_000), BigDecimal.ZERO));

        List<SectorRankingSummary> weighted =
                service.getTopSectorRankings(MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);
        List<SectorRankingSummary> simple =
                service.getTopSectorRankings(MarketQuery.KOSPI, snapshotTime, 60, AverageMode.SIMPLE, false);

        assertThat(weighted.get(0).topSectors().get(0).changeRate()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(simple.get(0).topSectors().get(0).changeRate()).isEqualByComparingTo(BigDecimal.valueOf(15));
    }

    // 섹터 제외(custom_sector.is_excluded)를 켜고 끄면 TOP2에 들어오는 섹터가 달라진다.
    @Test
    void getTopSectorRankingsByChangeRate_섹터_제외를_켜면_isExcluded_섹터가_빠진다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        CustomSector excluded = sector(1L, null, "반도체");
        excluded.exclude();
        CustomSector included = sector(2L, null, "화학");
        stubSectorTree(
                List.of(excluded, included),
                List.of(
                        CustomStockSector.create(LEGACY_OWNER_ID, "A", 1L),
                        CustomStockSector.create(LEGACY_OWNER_ID, "B", 2L)));
        stubStockCache(stock("A", Market.KOSPI), stock("B", Market.KOSPI));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(90)),
                price("B", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(5)));

        List<SectorRankingSummary> filtered = service.getTopSectorRankingsByChangeRate(
                MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, true);
        List<SectorRankingSummary> unfiltered = service.getTopSectorRankingsByChangeRate(
                MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);

        assertThat(filtered.get(0).topSectors())
                .extracting(TopSectorItem::sectorName)
                .containsExactly("화학");
        assertThat(unfiltered.get(0).topSectors())
                .extracting(TopSectorItem::sectorName)
                .containsExactly("반도체", "화학");
    }

    // 텔레그램 섹터 캡션의 "#코스피 +x.xx%"가 SectorRankingSummary.indexChangeRate를 읽는다
    // (toSectorRankingSummary의 marketRanking.index().now()) — 캡션이 실제로 읽는 자리에 지수 값이
    // 실리는지 보는 테스트가 지금까지 없었다.
    @Test
    void getTopSectorRankings_지수_등락률이_함께_담긴다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        CustomSector a = sector(1L, null, "반도체");
        stubSectorTree(List.of(a), List.of(CustomStockSector.create(LEGACY_OWNER_ID, "A", 1L)));
        stubStockCache(stock("A", Market.KOSPI));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime))
                .thenReturn(List.of(marketOverviewSnapshot(Market.KOSPI, snapshotTime, BigDecimal.valueOf(1.23))));

        List<SectorRankingSummary> summaries =
                service.getTopSectorRankings(MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);

        assertThat(summaries.get(0).indexChangeRate()).isEqualByComparingTo(BigDecimal.valueOf(1.23));
    }

    private void stubTierThresholds(CustomValueTierThreshold... tiers) {
        List<CustomValueTierThreshold> sorted = List.of(tiers).stream()
                .sorted(Comparator.comparing(CustomValueTierThreshold::getThresholdValue))
                .toList();
        when(marketValueTierThresholdRepository.findAll()).thenReturn(List.of(tiers));
        when(marketValueTierThresholdRepository.findAllByUserIdOrderByThresholdValueAsc(999999L))
                .thenReturn(sorted);
    }

    private CustomValueTierThreshold tierThreshold(
            Long id, String label, long thresholdValue, boolean excludedByDefault) {
        CustomValueTierThreshold threshold =
                CustomValueTierThreshold.create(LEGACY_OWNER_ID, label, thresholdValue, excludedByDefault);
        ReflectionTestUtils.setField(threshold, "id", id);
        return threshold;
    }

    private void stubSectorTree(List<CustomSector> sectors, List<CustomStockSector> assignments) {
        when(marketMapSectorRepository.findAll()).thenReturn(sectors);
        when(marketMapStockSectorRepository.findAll()).thenReturn(assignments);
    }

    private void stubStockCache(StockInfo... stocks) {
        when(stockInfoCacheService.getCache())
                .thenReturn(List.of(stocks).stream()
                        .collect(Collectors.toMap(StockInfo::getStockCode, Function.identity())));
    }

    @SafeVarargs
    private final void stubPrices(Market market, LocalDateTime time, Map.Entry<String, CachedStockPrice>... prices) {
        when(sectorPriceSnapshotRepository.existsByMarketTypeAndSnapshotTime(market, time))
                .thenReturn(true);
        when(sectorPriceCacheService.getCache(market, time)).thenReturn(Map.ofEntries(prices));
    }

    /** 트리 기반 랭킹 테스트용 종목 — listCount를 1로 고정해 price()의 totalMarketValue를 그대로
     * 시가총액으로 쓴다(currentPrice * listCount = currentPrice). */
    private StockInfo stock(String stockCode, Market market) {
        return StockInfo.create(stockCode, stockCode, market, "0", null, 1L, BigDecimal.TEN, false);
    }

    /** market은 stubPrices가 이미 (market, time) 단위로 캐시를 스텁하는 키라 여기서는 안 쓴다 —
     * 호출부를 그대로 두기 위해 시그니처만 유지한다. */
    private Map.Entry<String, CachedStockPrice> price(
            String stockCode, Market market, LocalDateTime time, BigDecimal totalMarketValue, BigDecimal changeRate) {
        return Map.entry(stockCode, new CachedStockPrice(totalMarketValue, changeRate, time));
    }

    private MarketOverviewSnapshot marketOverviewSnapshot(
            Market market, LocalDateTime snapshotTime, BigDecimal changeRate) {
        return MarketOverviewSnapshot.create(
                market,
                snapshotTime,
                BigDecimal.valueOf(2500),
                BigDecimal.ONE,
                changeRate,
                BigDecimal.ZERO,
                "OPEN",
                0,
                0,
                0,
                0,
                0,
                snapshotTime);
    }

    private CustomSector sector(Long id, Long parentId, String name) {
        CustomSector sector =
                parentId == null ? CustomSector.createParent(LEGACY_OWNER_ID, name) : sectorWithParent(parentId, name);
        ReflectionTestUtils.setField(sector, "id", id);
        return sector;
    }

    private CustomSector sectorWithParent(Long parentId, String name) {
        CustomSector parent = CustomSector.createParent(LEGACY_OWNER_ID, "parent-placeholder");
        ReflectionTestUtils.setField(parent, "id", parentId);
        return CustomSector.createChild(LEGACY_OWNER_ID, name, parent);
    }

    private StockInfo stockInfo(String stockCode, String stockName, Long listCount, BigDecimal lastPrice) {
        return StockInfo.create(stockCode, stockName, Market.KOSPI, "0", null, listCount, lastPrice, false);
    }

    @Test
    void 종목_공통_정보는_활성_보통주만_시장_NXT_업종명으로_내려준다() {
        StockInfo samsung = stockInfo("005930", "삼성전자", 100L, BigDecimal.TEN);
        StockInfo inactive = stockInfo("999999", "비활성", 100L, BigDecimal.TEN);
        StockInfo etf = StockInfo.create("069500", "ETF", Market.KOSPI, "8", null, 100L, BigDecimal.TEN, false);
        ReflectionTestUtils.setField(samsung, "industryId", 7L);
        ReflectionTestUtils.setField(samsung, "nxtEnabled", true);
        ReflectionTestUtils.setField(inactive, "active", false);
        IndustryInfo industry = IndustryInfo.create("반도체");
        ReflectionTestUtils.setField(industry, "id", 7L);
        when(industryInfoRepository.findAllById(Mockito.<Iterable<Long>>any())).thenReturn(List.of(industry));
        when(stockInfoCacheService.getCache())
                .thenReturn(Map.of(
                        samsung.getStockCode(), samsung,
                        inactive.getStockCode(), inactive,
                        etf.getStockCode(), etf));

        assertThat(service.getStockCatalog())
                .containsExactly(new StockCatalogItem("005930", Market.KOSPI, true, "반도체"));
    }

    private Map.Entry<String, CachedStockPrice> priceSnapshot(
            String stockCode, LocalDateTime snapshotTime, BigDecimal currentPrice) {
        return Map.entry(stockCode, new CachedStockPrice(currentPrice, BigDecimal.ZERO, snapshotTime));
    }

    @Test
    void 시간외_기준은_스냅샷_날짜의_종가_끝_경계부터_적용한다() {
        LocalDate date = LocalDate.of(2025, 11, 13);
        when(calendarTimeService.resolve(date))
                .thenReturn(new CalendarDayTimes(
                        false,
                        date.atTime(10, 0),
                        date.atTime(20, 0),
                        date.atTime(16, 30),
                        date.atTime(16, 30),
                        date.atTime(16, 40)));
        StockInfo stock = stockInfo("005930", "삼성전자", 100L, BigDecimal.TEN);
        when(stockInfoCacheService.getCache()).thenReturn(Map.of(stock.getStockCode(), stock));
        when(closingPriceReader.closingPricesFor(Mockito.eq(date), Mockito.any()))
                .thenReturn(new ClosingPrices(date, Map.of("005930", BigDecimal.valueOf(100))));
        for (LocalDateTime time : List.of(date.atTime(16, 35), date.atTime(16, 40))) {
            when(sectorPriceSnapshotRepository.existsByMarketTypeAndSnapshotTime(Market.KOSPI, time))
                    .thenReturn(true);
            when(sectorPriceCacheService.getCache(Market.KOSPI, time))
                    .thenReturn(Map.of("005930", new CachedStockPrice(BigDecimal.valueOf(110), BigDecimal.ONE, time)));
        }
        MarketMapResponse before =
                service.getDefaultMarketMap(MarketQuery.KOSPI, date.atTime(16, 35), false, ChangeRateMode.AFTER_HOURS);
        MarketMapResponse boundary =
                service.getDefaultMarketMap(MarketQuery.KOSPI, date.atTime(16, 40), false, ChangeRateMode.AFTER_HOURS);
        assertThat(before.items().get(0).items().get(0).changeRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(boundary.items().get(0).items().get(0).changeRate()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void 휴장_스냅샷의_시간외_요청은_일간_등락률을_유지한다() {
        LocalDateTime time = LocalDate.of(2026, 10, 9).atTime(18, 0);
        when(calendarTimeService.resolve(time.toLocalDate()))
                .thenReturn(new CalendarDayTimes(true, null, null, null, null, null));
        StockInfo stock = stockInfo("005930", "삼성전자", 100L, BigDecimal.TEN);
        when(stockInfoCacheService.getCache()).thenReturn(Map.of(stock.getStockCode(), stock));
        when(sectorPriceSnapshotRepository.existsByMarketTypeAndSnapshotTime(Market.KOSPI, time))
                .thenReturn(true);
        when(sectorPriceCacheService.getCache(Market.KOSPI, time))
                .thenReturn(Map.of("005930", new CachedStockPrice(BigDecimal.TEN, BigDecimal.ONE, time)));
        assertThat(service.getDefaultMarketMap(MarketQuery.KOSPI, time, false, ChangeRateMode.AFTER_HOURS)
                        .items()
                        .get(0)
                        .items()
                        .get(0)
                        .changeRate())
                .isEqualByComparingTo(BigDecimal.ONE);
    }

    private static final LocalDate PREVIOUS_DAY = LocalDate.of(2026, 10, 7);
    private static final LocalDate OFFICIAL_DAY = LocalDate.of(2026, 10, 8);

    private void stubOfficialClose(Map<String, BigDecimal> closes, Map<String, BigDecimal> previousCloses) {
        when(closingPriceReader.hasOfficialClose(OFFICIAL_DAY)).thenReturn(true);
        when(closingPriceReader.closingPricesFor(Mockito.eq(OFFICIAL_DAY), Mockito.any()))
                .thenReturn(new ClosingPrices(OFFICIAL_DAY, closes));
        when(closingPriceReader.previousClosingPricesFor(OFFICIAL_DAY))
                .thenReturn(new ClosingPrices(PREVIOUS_DAY, previousCloses));
    }

    @Test
    void 종가_데이터가_있는_날짜는_현재가_데이터_없이_종가와_직전_거래일_종가로_지도를_만든다() {
        LocalDateTime time = OFFICIAL_DAY.atTime(15, 40);
        StockInfo stock = stockInfo("005930", "삼성전자", 10L, BigDecimal.valueOf(1));
        when(stockInfoCacheService.getCache()).thenReturn(Map.of(stock.getStockCode(), stock));
        stubOfficialClose(Map.of("005930", BigDecimal.valueOf(110)), Map.of("005930", BigDecimal.valueOf(100)));

        MarketMapResponse response = service.getDefaultMarketMap(MarketQuery.KOSPI, time);

        var item = response.items().get(0).items().get(0);
        assertThat(response.snapshotTime()).isEqualTo(time);
        assertThat(item.currentPrice()).isEqualByComparingTo("110");
        assertThat(item.lastPrice()).isEqualByComparingTo("100");
        assertThat(item.changeRate()).isEqualByComparingTo("10");
        assertThat(item.snapshotTime()).isEqualTo(time);
        Mockito.verifyNoInteractions(sectorPriceCacheService);
    }

    @Test
    void 일봉이_있는_날짜는_전일_종가가_없거나_0이거나_일봉이_없는_종목을_응답에서_뺀다() {
        LocalDateTime time = OFFICIAL_DAY.atTime(15, 40);
        StockInfo listedToday = stockInfo("111111", "신규", 10L, BigDecimal.ONE);
        StockInfo zeroBase = stockInfo("222222", "기준가0", 10L, BigDecimal.ONE);
        StockInfo noPrice = stockInfo("333333", "일봉없음", 10L, BigDecimal.ONE);
        StockInfo normal = stockInfo("444444", "정상", 10L, BigDecimal.ONE);
        when(stockInfoCacheService.getCache())
                .thenReturn(Map.of("111111", listedToday, "222222", zeroBase, "333333", noPrice, "444444", normal));
        stubOfficialClose(
                Map.of(
                        "111111", BigDecimal.valueOf(100),
                        "222222", BigDecimal.valueOf(100),
                        "444444", BigDecimal.valueOf(50)),
                Map.of("222222", BigDecimal.ZERO, "333333", BigDecimal.valueOf(10), "444444", BigDecimal.valueOf(40)));

        MarketMapResponse response = service.getDefaultMarketMap(MarketQuery.KOSPI, time);

        var items = response.items().get(0).items();
        assertThat(items).extracting(item -> item.stockCode()).containsExactly("444444");
        assertThat(items.get(0).changeRate()).isEqualByComparingTo("25");
    }

    @Test
    void 종가_데이터가_있는_날짜는_현재가_데이터_존재_확인_없이_조회하고_시간외_기준을_적용하지_않는다() {
        LocalDateTime time = OFFICIAL_DAY.atTime(15, 40);
        StockInfo stock = stockInfo("005930", "삼성전자", 10L, BigDecimal.ONE);
        when(stockInfoCacheService.getCache()).thenReturn(Map.of(stock.getStockCode(), stock));
        stubOfficialClose(Map.of("005930", BigDecimal.valueOf(120)), Map.of("005930", BigDecimal.valueOf(100)));

        MarketMapResponse response =
                service.getDefaultMarketMap(MarketQuery.KOSPI, time, false, ChangeRateMode.AFTER_HOURS);

        assertThat(response.items().get(0).items().get(0).changeRate()).isEqualByComparingTo("20");
        Mockito.verify(sectorPriceSnapshotRepository, Mockito.never())
                .existsByMarketTypeAndSnapshotTime(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void 종가_데이터가_없는_날짜는_현재가_데이터의_현재가와_등락률을_쓰고_전일_종가만_직전_거래일_종가로_바꾼다() {
        LocalDateTime time = OFFICIAL_DAY.atTime(10, 0);
        StockInfo withClose = stockInfo("005930", "삼성전자", 10L, BigDecimal.valueOf(1));
        StockInfo withoutClose = stockInfo("000660", "SK하이닉스", 10L, BigDecimal.valueOf(7));
        when(stockInfoCacheService.getCache()).thenReturn(Map.of("005930", withClose, "000660", withoutClose));
        when(closingPriceReader.previousClosingPricesFor(OFFICIAL_DAY))
                .thenReturn(new ClosingPrices(PREVIOUS_DAY, Map.of("005930", BigDecimal.valueOf(100))));
        when(sectorPriceSnapshotRepository.existsByMarketTypeAndSnapshotTime(Market.KOSPI, time))
                .thenReturn(true);
        when(sectorPriceCacheService.getCache(Market.KOSPI, time))
                .thenReturn(Map.of(
                        "005930", new CachedStockPrice(BigDecimal.valueOf(103), BigDecimal.valueOf(3), time),
                        "000660", new CachedStockPrice(BigDecimal.valueOf(9), BigDecimal.valueOf(2), time)));

        MarketMapResponse response = service.getDefaultMarketMap(MarketQuery.KOSPI, time);

        var items = response.items().stream()
                .flatMap(node -> node.items().stream())
                .collect(Collectors.toMap(item -> item.stockCode(), Function.identity()));
        assertThat(items.get("005930").currentPrice()).isEqualByComparingTo("103");
        assertThat(items.get("005930").changeRate()).isEqualByComparingTo("3");
        assertThat(items.get("005930").lastPrice()).isEqualByComparingTo("100");
        // 직전 거래일 종가를 구하지 못한 종목은 종목 정보의 전일 종가를 쓴다.
        assertThat(items.get("000660").lastPrice()).isEqualByComparingTo("7");
    }
}
