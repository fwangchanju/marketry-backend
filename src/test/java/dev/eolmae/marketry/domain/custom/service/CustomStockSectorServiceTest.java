package dev.eolmae.marketry.domain.custom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.auth.enums.Role;
import dev.eolmae.marketry.domain.auth.service.AuthenticatedUserPrincipal;
import dev.eolmae.marketry.domain.custom.entity.CustomSector;
import dev.eolmae.marketry.domain.custom.entity.CustomStockSector;
import dev.eolmae.marketry.domain.custom.repository.CustomSectorRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomStockAliasRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomStockSectorRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomValueTierThresholdRepository;
import dev.eolmae.marketry.domain.stock.entity.StockInfo;
import dev.eolmae.marketry.domain.stock.repository.IndustryInfoRepository;
import dev.eolmae.marketry.domain.stock.repository.StockIndustryOverrideRepository;
import dev.eolmae.marketry.domain.stock.service.SectorPriceSnapshotService;
import dev.eolmae.marketry.domain.stock.service.StockIndustryNameResolver;
import dev.eolmae.marketry.domain.stock.service.StockInfoCacheService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class CustomStockSectorServiceTest {

    private static final long USER_ID = 41L;
    private static final long SECTOR_ID = 12L;

    private final CustomStockSectorRepository stockSectorRepository = Mockito.mock(CustomStockSectorRepository.class);
    private final CustomStockAliasRepository stockAliasRepository = Mockito.mock(CustomStockAliasRepository.class);
    private final CustomSectorRepository sectorRepository = Mockito.mock(CustomSectorRepository.class);
    private final StockInfoCacheService stockInfoCacheService = Mockito.mock(StockInfoCacheService.class);
    private final SectorPriceSnapshotService sectorPriceSnapshotService =
            Mockito.mock(SectorPriceSnapshotService.class);
    private final CustomValueTierThresholdService valueTierService =
            new CustomValueTierThresholdService(Mockito.mock(CustomValueTierThresholdRepository.class));
    private final IndustryInfoRepository industryInfoRepository = Mockito.mock(IndustryInfoRepository.class);
    private final JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
    private final ProtectedSectorService protectedSectorService = Mockito.mock(ProtectedSectorService.class);
    private final CustomStockSectorService service = new CustomStockSectorService(
            stockSectorRepository,
            stockAliasRepository,
            sectorRepository,
            stockInfoCacheService,
            sectorPriceSnapshotService,
            valueTierService,
            new StockIndustryNameResolver(industryInfoRepository, Mockito.mock(StockIndustryOverrideRepository.class)),
            jdbcTemplate,
            protectedSectorService);

    private final CustomSector sector = CustomSector.createParent(USER_ID, "산업");
    private final StockInfo stock =
            StockInfo.create("005930", "삼성전자", Market.KOSPI, "0", null, 100L, BigDecimal.TEN, false);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sector, "id", SECTOR_ID);
        var principal = new AuthenticatedUserPrincipal(USER_ID, Role.USER);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(sectorRepository.findByIdAndUserId(SECTOR_ID, USER_ID)).thenReturn(Optional.of(sector));
        when(stockInfoCacheService.getCache()).thenReturn(Map.of(stock.getStockCode(), stock));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getStockSectors_신규회원에게도_미배정_종목을_반환한다() {
        var response = service.getStockSectors();

        assertThat(response.items()).hasSize(1);
        var item = response.items().get(0);
        assertThat(item.stockCode()).isEqualTo("005930");
        assertThat(item.stockName()).isEqualTo("삼성전자");
        assertThat(item.sectorId()).isNull();
        assertThat(item.sectorName()).isNull();
        assertThat(item.parentSectorName()).isNull();
        Mockito.verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void getStockSectors_배정여부와_무관하게_활성_주권만_반환한다() {
        StockInfo unassigned =
                StockInfo.create("000660", "SK하이닉스", Market.KOSPI, "0", null, 100L, BigDecimal.TEN, false);
        StockInfo inactive = StockInfo.create("999999", "비활성종목", Market.KOSPI, "0", null, 100L, BigDecimal.TEN, false);
        inactive.markInactive();
        StockInfo etf = StockInfo.create("069500", "KODEX 200", Market.KOSPI, "8", null, 100L, BigDecimal.TEN, false);
        when(stockInfoCacheService.getCache())
                .thenReturn(Map.of(
                        stock.getStockCode(), stock,
                        unassigned.getStockCode(), unassigned,
                        inactive.getStockCode(), inactive,
                        etf.getStockCode(), etf));
        when(sectorRepository.findAllByUserId(USER_ID)).thenReturn(List.of(sector));
        when(stockSectorRepository.findAllByIdUserId(USER_ID))
                .thenReturn(List.of(CustomStockSector.create(USER_ID, stock.getStockCode(), SECTOR_ID)));

        var response = service.getStockSectors();

        assertThat(response.items()).extracting("stockCode").containsExactlyInAnyOrder("005930", "000660");
        var assignedItem = response.items().stream()
                .filter(item -> item.stockCode().equals("005930"))
                .findFirst()
                .orElseThrow();
        assertThat(assignedItem.sectorId()).isEqualTo(SECTOR_ID);
        assertThat(assignedItem.sectorName()).isEqualTo("산업");
        var unassignedItem = response.items().stream()
                .filter(item -> item.stockCode().equals("000660"))
                .findFirst()
                .orElseThrow();
        assertThat(unassignedItem.sectorId()).isNull();
        assertThat(unassignedItem.sectorName()).isNull();
    }

    @Test
    void assign_사용자와_종목_키로_배정행을_atomic_upsert한다() {
        service.assign(stock.getStockCode(), SECTOR_ID);

        verify(jdbcTemplate)
                .update(contains("INSERT INTO custom_stock_sector"), eq(USER_ID), eq("005930"), eq(SECTOR_ID));
    }

    @Test
    void bulkAssign_유효한_종목만_upsert하고_나머지는_응답에_반환한다() {
        var response = service.bulkAssign(List.of("005930", "999999", "005930"), SECTOR_ID);

        assertThat(response.failedStockCodes()).containsExactly("999999");
        assertThat(response.sectorId()).isEqualTo(SECTOR_ID);
        verify(jdbcTemplate)
                .batchUpdate(
                        contains("INSERT INTO custom_stock_sector"),
                        eq(List.of("005930")),
                        eq(1),
                        Mockito.<ParameterizedPreparedStatementSetter<String>>any());
    }

    @Test
    void updateAlias_배정이_없어도_사용자_별칭을_upsert한다() {
        service.updateAlias(stock.getStockCode(), "삼전");

        verify(jdbcTemplate).update(contains("INSERT INTO custom_stock_alias"), eq(USER_ID), eq("005930"), eq("삼전"));
    }
}
