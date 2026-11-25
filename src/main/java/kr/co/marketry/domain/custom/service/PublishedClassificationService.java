package kr.co.marketry.domain.custom.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.co.marketry.domain.custom.entity.CustomSector;
import kr.co.marketry.domain.custom.entity.CustomSnapshot;
import kr.co.marketry.domain.custom.enums.ClassificationMarket;
import kr.co.marketry.domain.custom.repository.CustomSectorRepository;
import kr.co.marketry.domain.custom.repository.CustomSnapshotRepository;
import kr.co.marketry.domain.custom.repository.CustomStockSectorRepository;
import kr.co.marketry.domain.notification.properties.MarketryProperties;
import kr.co.marketry.domain.stock.repository.OverseasClassificationCategoryRepository;
import kr.co.marketry.domain.stock.service.OverseasClassificationCategoryService;
import kr.co.marketry.domain.stock.service.OverseasClassificationPublicationService;
import kr.co.marketry.domain.stock.service.OverseasStockClassificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일본·미국 MARKETRY 분류를 읽는다. 복사 스크립트로 한국과 같은 자리(발행 계정의 그 시장 업종)에 옮긴 뒤에는 그 자리를 읽고,
 * 아직 옮기지 않았으면 옛 자리를 그대로 읽는다. 그래서 이 코드는 복사 전에 배포해도 화면이 바뀌지 않는다.
 * 응답 모양은 옛 주소와 같고, 새 자리에서는 업종의 고정 이름표와 옛 업종 번호를 함께 준다(브라우저에 저장된 옛 번호를 옮기는 데 쓴다).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PublishedClassificationService {

    private final MarketryProperties marketryProperties;
    private final CustomSectorRepository customSectorRepository;
    private final CustomStockSectorRepository customStockSectorRepository;
    private final CustomSnapshotRepository customSnapshotRepository;
    private final OverseasClassificationCategoryRepository legacyCategories;
    private final OverseasClassificationCategoryService legacyCategoryService;
    private final OverseasStockClassificationService legacyStockService;
    private final OverseasClassificationPublicationService legacyPublications;

    public record Category(Long id, Long parentId, String name, UUID stableKey, Long legacyId) {}

    public record Classification(String sector, String industry, String subIndustry) {}

    /** 일본·미국 분류를 새 자리로 옮겼는지 — 발행 계정에 그 시장 업종이 하나라도 있으면 옮긴 것이다. */
    public boolean isMoved(ClassificationMarket market) {
        return !publishedSectors(market).isEmpty();
    }

    public List<Category> findCategories(ClassificationMarket market) {
        List<CustomSector> sectors = publishedSectors(market);
        if (sectors.isEmpty()) {
            return legacyCategoryService.findAll(market.name()).stream()
                    .map(category -> new Category(category.id(), category.parentId(), category.name(), null, null))
                    .toList();
        }
        Map<UUID, Long> legacyIdByKey = new HashMap<>();
        legacyCategories
                .findAllByMarket(market.name())
                .forEach(category -> legacyIdByKey.put(legacyStableKey(market, category.getId()), category.getId()));
        return sectors.stream()
                .map(sector -> new Category(
                        sector.getId(),
                        sector.getParentId(),
                        sector.getName(),
                        sector.getStableKey(),
                        legacyIdByKey.get(sector.getStableKey())))
                .toList();
    }

    public Map<String, Classification> findClassifications(ClassificationMarket market) {
        List<CustomSector> sectors = publishedSectors(market);
        if (sectors.isEmpty()) {
            return legacyStockService.findAll(market.name()).entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> new Classification(
                                    entry.getValue().sector(),
                                    entry.getValue().industry(),
                                    entry.getValue().subIndustry())));
        }
        Map<Long, CustomSector> byId =
                sectors.stream().collect(Collectors.toMap(CustomSector::getId, Function.identity()));
        Map<String, Classification> result = new HashMap<>();
        customStockSectorRepository
                .findAllByIdUserIdAndMarket(marketryProperties.publishedUserId(), market)
                .forEach(assignment -> {
                    List<String> path = path(byId, byId.get(assignment.getSectorId()));
                    result.put(
                            assignment.getStockCode(),
                            new Classification(
                                    path.isEmpty() ? null : path.get(0),
                                    path.size() > 1 ? path.get(1) : null,
                                    path.size() > 2 ? path.get(2) : null));
                });
        return result;
    }

    /** 설정창 맨 아래 업데이트 시각 — 한국 MARKETRY처럼 지금 보이는 버전을 올린 시각이다. */
    public Optional<LocalDateTime> findPublishedAt(ClassificationMarket market) {
        List<CustomSector> sectors = publishedSectors(market);
        if (sectors.isEmpty()) {
            return legacyPublications.findPublishedAt(market.name());
        }
        Long publishedUserId = marketryProperties.publishedUserId();
        return sectors.stream()
                .map(CustomSector::getSnapshotId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .flatMap(snapshotId ->
                        customSnapshotRepository.findByIdAndUserIdAndMarket(snapshotId, publishedUserId, market))
                .map(CustomSnapshot::getCreatedAt);
    }

    private List<CustomSector> publishedSectors(ClassificationMarket market) {
        if (market == ClassificationMarket.KOREA) {
            throw new IllegalArgumentException("한국 MARKETRY는 지도 응답으로 읽는다");
        }
        return customSectorRepository.findAllByUserIdAndMarket(marketryProperties.publishedUserId(), market);
    }

    private static List<String> path(Map<Long, CustomSector> byId, CustomSector leaf) {
        List<String> path = new ArrayList<>();
        for (CustomSector current = leaf; current != null; current = byId.get(current.getParentId())) {
            path.addFirst(current.getName());
            if (current.getParentId() == null) {
                break;
            }
        }
        return path;
    }

    /** 복사 스크립트가 옛 업종에 붙인 고정 이름표와 같은 값(md5("시장:live:번호")를 그대로 UUID로 쓴 것)이다. */
    static UUID legacyStableKey(ClassificationMarket market, long legacyId) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest((market.name() + ":live:" + legacyId).getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.wrap(digest);
            return new UUID(buffer.getLong(), buffer.getLong());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
