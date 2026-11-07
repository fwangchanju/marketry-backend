package kr.co.marketry.domain.custom.repository;

import java.time.LocalDateTime;

public interface CustomSectorRepositoryCustom {

    /** 해당 사용자가 업종을 만든 뒤 고친(이름·위치·제외 변경 등) 가장 최근 시각. 만들기만 하고 고친 적 없는 업종은 뺀다 —
     * 새 업종·신규 상장 업종을 서버가 모든 사용자에게 자동으로 만들 때(생성 시각 = 수정 시각)가 사용자가 고친 것으로 보이지 않게 하려는 것이다.
     * 고친 업종이 하나도 없으면 null. */
    LocalDateTime findLatestEditedAtByUserId(Long userId);
}
