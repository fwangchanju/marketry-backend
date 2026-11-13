package kr.co.marketry.domain.auth.service;

import kr.co.marketry.domain.auth.enums.Role;
import kr.co.marketry.domain.auth.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 관리자와 특별 회원만 통과시킨다. 특별 회원 여부는 토큰에 없어서 회원 정보를 읽어 확인하므로 지정을 해제하면 바로 막힌다.
 * 읽기 전용 캡처 토큰은 허용하지 않는다. 해외 화면을 공개할 때는 이 클래스의 조건만 바꾼다.
 */
@Component
@RequiredArgsConstructor
public class SpecialMemberGuard {
    private final UserAccountRepository userAccountRepository;

    public void require(AuthenticatedUserPrincipal principal) {
        if (principal.role() == Role.ADMIN) {
            return;
        }
        boolean special = userAccountRepository
                .findById(principal.userId())
                .map(user -> user.isSpecial())
                .orElse(false);
        if (special == false) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
