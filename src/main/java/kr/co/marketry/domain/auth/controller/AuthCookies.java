package kr.co.marketry.domain.auth.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import kr.co.marketry.domain.auth.service.AuthService.IssuedTokens;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 로그인 세션 쿠키(mm_access, mm_refresh) 기록과 쿠키 발급/만료 자체의 공통 로직을 한 곳에 모은다.
 * AuthController(구글 로그인)와 DevLoginController(로컬 개발용 로그인)가 공유한다.
 */
@Component
public class AuthCookies {

    private static final String ACCESS_COOKIE = "mm_access";
    private static final String REFRESH_COOKIE = "mm_refresh";

    public void setSessionCookies(HttpServletResponse response, IssuedTokens tokens) {
        add(response, ACCESS_COOKIE, tokens.accessToken(), "/", Duration.ofMinutes(15));
        add(response, REFRESH_COOKIE, tokens.refreshToken(), "/api/auth", Duration.ofDays(14));
    }

    public void expireSessionCookies(HttpServletResponse response) {
        expire(response, ACCESS_COOKIE, "/");
        expire(response, REFRESH_COOKIE, "/api/auth");
    }

    public void add(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(name, value)
                        .httpOnly(true)
                        .secure(true)
                        .sameSite("Lax")
                        .path(path)
                        .maxAge(maxAge)
                        .build()
                        .toString());
    }

    public void expire(HttpServletResponse response, String name, String path) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(name, "")
                        .httpOnly(true)
                        .secure(true)
                        .sameSite("Lax")
                        .path(path)
                        .maxAge(Duration.ZERO)
                        .build()
                        .toString());
    }
}
