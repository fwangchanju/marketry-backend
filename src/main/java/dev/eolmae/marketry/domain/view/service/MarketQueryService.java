package dev.eolmae.marketry.domain.view.service;

import dev.eolmae.marketry.domain.stock.entity.MarketOverviewSnapshot;
import dev.eolmae.marketry.domain.stock.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketry.domain.view.dto.MarketOverviewItem;
import dev.eolmae.marketry.domain.view.dto.MarketSummaryResponse;
import dev.eolmae.marketry.domain.view.dto.SnapshotResponse;
import java.time.LocalDateTime;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MarketQueryService {

    private final MarketOverviewSnapshotRepository marketOverviewSnapshotRepository;

    /** 홈이 쓰는 시장 종합 현황(지수·등락·상하한가) 반환 */
    public MarketSummaryResponse getMarketSummary() {
        return new MarketSummaryResponse(getMarketOverviews());
    }

    private SnapshotResponse<MarketOverviewItem> getMarketOverviews() {
        LocalDateTime latestSnapshotTime = marketOverviewSnapshotRepository
                .findFirstByOrderBySnapshotTimeDesc()
                .map(MarketOverviewSnapshot::getSnapshotTime)
                .orElse(null);
        if (latestSnapshotTime == null) {
            return SnapshotResponse.empty();
        }

        // marketType은 @Enumerated(STRING)이라 DB ORDER BY는 문자열 순(KOSDAQ < KOSPI)이 됨 -
        // enum 선언 순서(KOSPI, KOSDAQ)대로 나오도록 Java에서 재정렬.
        var snapshots = marketOverviewSnapshotRepository.findBySnapshotTime(latestSnapshotTime).stream()
                .sorted(Comparator.comparing(MarketOverviewSnapshot::getMarketType))
                .toList();

        return new SnapshotResponse<>(
                latestSnapshotTime,
                snapshots.stream().map(MarketOverviewItem::from).toList());
    }
}
