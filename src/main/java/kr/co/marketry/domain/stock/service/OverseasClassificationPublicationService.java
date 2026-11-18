package kr.co.marketry.domain.stock.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import kr.co.marketry.domain.stock.entity.OverseasClassificationPublication;
import kr.co.marketry.domain.stock.repository.OverseasClassificationPublicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** MARKETRY 분류를 업데이트하거나 롤백한 시각을 기록하고, 설정창 맨 아래 "업데이트" 시각으로 돌려준다. */
@Service
@RequiredArgsConstructor
public class OverseasClassificationPublicationService {
    private final OverseasClassificationPublicationRepository publications;
    private final Clock clock = Clock.system(ZoneId.of("Asia/Seoul"));

    @Transactional(readOnly = true)
    public Optional<LocalDateTime> findPublishedAt(String market) {
        return publications.findById(market).map(OverseasClassificationPublication::getPublishedAt);
    }

    @Transactional
    public void recordPublished(String market) {
        LocalDateTime now = LocalDateTime.now(clock);
        OverseasClassificationPublication publication =
                publications.findById(market).orElse(null);
        if (publication == null) {
            publications.save(OverseasClassificationPublication.create(market, now));
            return;
        }
        publication.overwrite(now);
    }
}
