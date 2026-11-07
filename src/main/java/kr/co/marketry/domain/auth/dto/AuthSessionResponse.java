package kr.co.marketry.domain.auth.dto;

import kr.co.marketry.domain.auth.enums.Role;

public record AuthSessionResponse(
        boolean authenticated,
        Long userId,
        String email,
        Role role,
        String nickname,
        Long profileImageVersion,
        Long nicknameChangeableAt,
        boolean special,
        String issuer) {

    public static AuthSessionResponse anonymous() {
        return new AuthSessionResponse(false, null, null, null, null, null, null, false, null);
    }
}
