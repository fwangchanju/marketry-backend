package kr.co.marketry.domain.custom.repository;

import java.time.LocalDateTime;
import java.util.List;

public interface CustomStockSectorRepositoryCustom {

    /** PK(userId, stockCode) 단건 삭제지만 파생 {@code deleteBy}로 바꾸지 않는다 — 파생 삭제는 엔티티를
     * 로드한 뒤 remove()로 지워 flush 시점까지 지연되는데, 여기는 즉시 실행되는 단일 DELETE가 필요하다. */
    void deleteByIdUserIdAndIdStockCode(Long userId, String stockCode);

    /** sectorId 하위 트리를 지울 때 그 섹터를 참조하는 배정 행을 custom_sector 삭제보다 먼저,
     * 그리고 즉시 지워야 FK 위반을 피한다(CustomSectorService.delete). 파생 deleteBy는 flush 시점까지
     * 지연돼 순서를 보장하지 못한다. */
    void deleteByUserIdAndSectorIdIn(Long userId, List<Long> sectorIds);

    /** CustomSectorTreeService.restore는 이 삭제가 즉시 반영된 뒤 다른 리포지토리의 큐잉된 삭제를
     * flush()로 몰아서 내보내는 순서에 의존한다. 파생 deleteBy로 바꾸면 이 삭제도 큐잉되어 순서가
     * 달라진다. */
    void deleteAllByIdUserId(Long userId);

    /** 해당 사용자의 종목 분류가 마지막으로 배정·변경된 시각. 서버가 "신규 상장" 업종에 자동으로 배정한 행은 뺀다. 센 행이 하나도 없으면 null. */
    LocalDateTime findLatestUpdatedAtByUserId(Long userId);

    /** 회원 탈퇴 — 한 회원의 종목 배정을 모두 지운다. 업종보다 먼저 지워야 FK를 어기지 않는다. */
    void deleteAllByUserId(Long userId);
}
