package kr.co.marketry.domain.stock.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import kr.co.marketry.domain.stock.entity.OverseasClassificationDraftEdit;
import kr.co.marketry.domain.stock.repository.OverseasClassificationDraftEditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 기준(초안)을 고칠 때마다 시각을 기록한다. 설정창 맨 아래 시각으로 쓴다(관리자만 쓰는 화면). */
@Service
@RequiredArgsConstructor
public class OverseasClassificationDraftEditService {
    private final OverseasClassificationDraftEditRepository edits;
    private final Clock clock = Clock.system(ZoneId.of("Asia/Seoul"));

    @Transactional(readOnly = true)
    public Optional<LocalDateTime> findEditedAt(String market) {
        return edits.findById(market).map(OverseasClassificationDraftEdit::getEditedAt);
    }

    @Transactional
    public void recordEdited(String market) {
        LocalDateTime now = LocalDateTime.now(clock);
        OverseasClassificationDraftEdit edit = edits.findById(market).orElse(null);
        if (edit == null) {
            edits.save(OverseasClassificationDraftEdit.create(market, now));
            return;
        }
        edit.overwrite(now);
    }
}
