package kr.co.marketry.domain.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

public final class CurrentUser {

    private CurrentUser() {}

    public static Long requireId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserPrincipal user) {
            return user.userId();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    }

    public static AuthenticatedUserPrincipal current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserPrincipal user) {
            return user;
        }
        return null;
    }

    public static Long currentId() {
        AuthenticatedUserPrincipal user = current();
        return user == null ? null : user.userId();
    }
}
