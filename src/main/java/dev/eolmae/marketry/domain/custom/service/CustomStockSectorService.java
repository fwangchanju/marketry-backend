package dev.eolmae.marketry.domain.custom.service;

import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.common.exception.NotFoundException;
import dev.eolmae.marketry.domain.auth.service.CurrentUser;
import dev.eolmae.marketry.domain.custom.dto.BulkAssignResponse;
import dev.eolmae.marketry.domain.custom.dto.StockSectorListItem;
import dev.eolmae.marketry.domain.custom.entity.CustomSector;
import dev.eolmae.marketry.domain.custom.entity.CustomStockAlias;
import dev.eolmae.marketry.domain.custom.entity.CustomStockAliasId;
import dev.eolmae.marketry.domain.custom.entity.CustomStockSector;
import dev.eolmae.marketry.domain.custom.entity.CustomValueTierThreshold;
import dev.eolmae.marketry.domain.custom.repository.CustomSectorRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomStockAliasRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomStockSectorRepository;
import dev.eolmae.marketry.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketry.domain.stock.entity.StockInfo;
import dev.eolmae.marketry.domain.stock.service.SectorPriceSnapshotService;
import dev.eolmae.marketry.domain.stock.service.StockIndustryNameResolver;
import dev.eolmae.marketry.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketry.domain.view.dto.SnapshotResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인 사용자의 종목 배정과 별칭을 관리한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class CustomStockSectorService {

    private final CustomStockSectorRepository customStockSectorRepository;
    private final CustomStockAliasRepository customStockAliasRepository;
    private final CustomSectorRepository customSectorRepository;
    private final StockInfoCacheService stockInfoCacheService;
    private final SectorPriceSnapshotService sectorPriceSnapshotService;
    private final CustomValueTierThresholdService customValueTierThresholdService;
    private final StockIndustryNameResolver stockIndustryNameResolver;
    private final JdbcTemplate jdbcTemplate;

    public void assign(String stockCode, Long sectorId) {
        Long userId = CurrentUser.requireId();
        requireActiveStock(stockCode);
        requireOwnedSector(sectorId, userId);
        // ON CONFLICT ... DO UPDATE upsert — JPQL/QueryDSL에 없는 문법이라 JdbcTemplate으로 직접 쓴다.
        jdbcTemplate.update("""
                INSERT INTO custom_stock_sector (user_id, stock_code, sector_id, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id, stock_code)
                DO UPDATE SET sector_id = EXCLUDED.sector_id, updated_at = CURRENT_TIMESTAMP
                """, userId, stockCode, sectorId);
        jdbcTemplate.update(
                "UPDATE users SET first_custom_classification_at = CURRENT_TIMESTAMP WHERE id = ? AND first_custom_classification_at IS NULL",
                userId);
    }

    public void unassign(String stockCode) {
        customStockSectorRepository.deleteByIdUserIdAndIdStockCode(CurrentUser.requireId(), stockCode);
    }

    /** 요청에 있는 유효 종목은 배정 행 유무와 관계없이 upsert한다. */
    public BulkAssignResponse bulkAssign(List<String> stockCodes, Long sectorId) {
        Long userId = CurrentUser.requireId();
        requireOwnedSector(sectorId, userId);
        Set<String> remaining = new LinkedHashSet<>(stockCodes);
        Map<String, StockInfo> stocks = stockInfoCacheService.getCache();
        List<String> validStockCodes = remaining.stream()
                .filter(stockCode -> {
                    StockInfo stock = stocks.get(stockCode);
                    return stock != null && stock.isActiveAndOrdinary();
                })
                .toList();
        remaining.removeAll(validStockCodes);

        if (!validStockCodes.isEmpty()) {
            // ON CONFLICT ... DO UPDATE upsert — 위와 동일한 이유로 배치 처리도 JdbcTemplate을 쓴다.
            jdbcTemplate.batchUpdate("""
                    INSERT INTO custom_stock_sector (user_id, stock_code, sector_id, created_at, updated_at)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (user_id, stock_code)
                    DO UPDATE SET sector_id = EXCLUDED.sector_id, updated_at = CURRENT_TIMESTAMP
                    """, validStockCodes, validStockCodes.size(), (statement, stockCode) -> {
                statement.setLong(1, userId);
                statement.setString(2, stockCode);
                statement.setLong(3, sectorId);
            });
            jdbcTemplate.update(
                    "UPDATE users SET first_custom_classification_at = CURRENT_TIMESTAMP WHERE id = ? AND first_custom_classification_at IS NULL",
                    userId);
        }
        return new BulkAssignResponse(new ArrayList<>(remaining), sectorId);
    }

    /** 별칭은 custom_stock_alias에만 저장한다. */
    public void updateAlias(String stockCode, String alias) {
        Long userId = CurrentUser.requireId();
        requireActiveStock(stockCode);
        CustomStockAliasId id = new CustomStockAliasId(userId, stockCode);
        if (alias == null || alias.isBlank()) {
            customStockAliasRepository.deleteById(id);
            return;
        }
        // ON CONFLICT ... DO UPDATE upsert — JPQL/QueryDSL에 없는 문법이라 JdbcTemplate으로 직접 쓴다.
        jdbcTemplate.update("""
                INSERT INTO custom_stock_alias (user_id, stock_code, alias, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id, stock_code)
                DO UPDATE SET alias = EXCLUDED.alias, updated_at = CURRENT_TIMESTAMP
                """, userId, stockCode, alias);
    }

    @Transactional(readOnly = true)
    public SnapshotResponse<StockSectorListItem> getStockSectors() {
        Long userId = CurrentUser.requireId();
        Map<Long, CustomSector> sectorById = customSectorRepository.findAllByUserId(userId).stream()
                .collect(Collectors.toMap(CustomSector::getId, Function.identity()));
        Map<String, CustomStockSector> assignmentByStockCode =
                customStockSectorRepository.findAllByIdUserId(userId).stream()
                        .collect(Collectors.toMap(CustomStockSector::getStockCode, Function.identity()));
        Map<String, String> aliasByStockCode = customStockAliasRepository.findAllByIdUserId(userId).stream()
                .collect(Collectors.toMap(CustomStockAlias::getStockCode, CustomStockAlias::getAlias));
        List<StockInfo> activeStocks = stockInfoCacheService.getCache().values().stream()
                .filter(StockInfo::isActiveAndOrdinary)
                .toList();
        Map<String, String> industryNameByStockCode = stockIndustryNameResolver.resolve(activeStocks);
        Map<String, SectorPriceSnapshot> latestPriceByStockCode =
                sectorPriceSnapshotService.findLatestPriceByStockCode();
        List<CustomValueTierThreshold> sortedTiers = customValueTierThresholdService.findAllSortedAscending();

        List<StockSectorListItem> items = activeStocks.stream()
                .map(stock -> toStockSectorListItem(
                        stock,
                        assignmentByStockCode.get(stock.getStockCode()),
                        aliasByStockCode.get(stock.getStockCode()),
                        sectorById,
                        industryNameByStockCode,
                        latestPriceByStockCode,
                        sortedTiers))
                .toList();

        LocalDateTime snapshotTime = latestPriceByStockCode.values().stream()
                .findFirst()
                .map(SectorPriceSnapshot::getSnapshotTime)
                .orElse(null);
        return new SnapshotResponse<>(snapshotTime, items);
    }

    private StockSectorListItem toStockSectorListItem(
            StockInfo stock,
            CustomStockSector assignment,
            String alias,
            Map<Long, CustomSector> sectorById,
            Map<String, String> industryNameByStockCode,
            Map<String, SectorPriceSnapshot> latestPriceByStockCode,
            List<CustomValueTierThreshold> sortedTiers) {
        CustomSector sector = assignment == null ? null : sectorById.get(assignment.getSectorId());
        CustomSector parent = sector == null || sector.hasNoParent() ? null : sectorById.get(sector.getParentId());
        SectorPriceSnapshot priceSnapshot = latestPriceByStockCode.get(stock.getStockCode());
        BigDecimal totalMarketValue = null;
        String marketValueTier = null;
        if (priceSnapshot != null) {
            totalMarketValue = priceSnapshot.getCurrentPrice().multiply(BigDecimal.valueOf(stock.getListCount()));
            marketValueTier = customValueTierThresholdService.resolveTier(sortedTiers, totalMarketValue);
        }
        return new StockSectorListItem(
                stock.getStockCode(),
                stock.getMarketType(),
                stock.getStockName(),
                stock.isNxtEnabled(),
                alias,
                totalMarketValue,
                marketValueTier,
                industryNameByStockCode.get(stock.getStockCode()),
                parent == null ? null : parent.getName(),
                sector == null ? null : sector.getName(),
                sector == null ? null : sector.getId());
    }

    private void requireOwnedSector(Long sectorId, Long userId) {
        if (customSectorRepository.findByIdAndUserId(sectorId, userId).isEmpty()) {
            throw new NotFoundException(ErrorCode.SECTOR_NOT_FOUND, sectorId);
        }
    }

    private void requireActiveStock(String stockCode) {
        StockInfo stock = stockInfoCacheService.getCache().get(stockCode);
        if (stock == null || !stock.isActiveAndOrdinary()) {
            throw new NotFoundException(ErrorCode.STOCK_SECTOR_NOT_FOUND, stockCode);
        }
    }
}
