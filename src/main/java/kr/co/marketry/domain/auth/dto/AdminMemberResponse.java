package kr.co.marketry.domain.auth.dto;

import java.time.LocalDateTime;
import kr.co.marketry.domain.auth.enums.Role;

public record AdminMemberResponse(
        Long id,
        // 로그인 연동 제공자 식별값(구글은 https://accounts.google.com). 시스템·로컬 테스트 회원은 다른 값이다.
        String issuer,
        String email,
        String nickname,
        Role role,
        boolean special,
        LocalDateTime createdAt,
        LocalDateTime lastAccessAt,
        // 최근 30일(오늘 포함) 중 접속한 날 수. 접속 기록은 기록을 시작한 날부터만 있어서 처음에는 적게 나온다.
        Long accessDays) {

    public AdminMemberResponse withAccessDays(long accessDays) {
        return new AdminMemberResponse(id, issuer, email, nickname, role, special, createdAt, lastAccessAt, accessDays);
    }
}
