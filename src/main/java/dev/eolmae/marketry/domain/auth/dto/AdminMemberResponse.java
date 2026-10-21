package dev.eolmae.marketry.domain.auth.dto;

import dev.eolmae.marketry.domain.auth.enums.Role;
import java.time.LocalDateTime;

public record AdminMemberResponse(
        Long id,
        // 로그인 연동 제공자 식별값(구글은 https://accounts.google.com). 시스템·로컬 테스트 회원은 다른 값이다.
        String issuer,
        String email,
        String nickname,
        Role role,
        boolean special,
        LocalDateTime createdAt,
        LocalDateTime lastAccessAt) {}
