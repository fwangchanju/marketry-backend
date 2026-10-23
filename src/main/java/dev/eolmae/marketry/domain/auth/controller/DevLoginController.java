package dev.eolmae.marketry.domain.auth.controller;

import dev.eolmae.marketry.domain.auth.dto.AuthSessionResponse;
import dev.eolmae.marketry.domain.auth.service.AppJwtService;
import dev.eolmae.marketry.domain.auth.service.AuthService;
import dev.eolmae.marketry.domain.auth.service.AuthService.IssuedTokens;
import dev.eolmae.marketry.domain.notification.properties.MarketryProperties;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 로컬 프론트엔드 개발 전용 로그인. IP 화이트리스트 인증 브릿지를 대체한다. marketry.owner-user-id로 로그인하며, 구글 로그인과 동일한 쿠키·응답 형태를
 * 사용한다.
 *
 * <p>운영 환경 차단은 두 겹이다. {@code @Profile("!prod")}로 prod 프로필에서는 빈 자체가 생성되지 않고,
 * {@code auth.dev-login.enabled}가 true일 때만 추가로 켜진다. 기본 application.properties는 이 값을
 * true로 고정하고(사실상 로컬 전용 설정 파일), application-prod.properties가 false로 덮어쓴다.
 */
@RestController
@RequestMapping("/api/auth")
@Profile("!prod")
@ConditionalOnProperty(name = "auth.dev-login.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DevLoginController {

    private final AuthService authService;
    private final AppJwtService appJwtService;
    private final AuthCookies authCookies;
    private final MarketryProperties marketryProperties;
    private final Environment environment;

    @PostConstruct
    void warnDevLoginActive() {
        log.warn("dev-login enabled — local development only");
    }

    @PostMapping("/dev-login")
    public AuthSessionResponse devLogin(HttpServletResponse response) {
        return createSession(response, authService.loginAsForDevelopment(marketryProperties.ownerUserId()));
    }

    /** 매번 새 회원을 만들기 때문에 기본 개발 로그인보다 좁은 local 프로필에서만 허용한다. */
    @PostMapping("/dev-signup")
    public AuthSessionResponse devSignup(HttpServletResponse response) {
        if (environment.matchesProfiles("local")) {
            return createSession(response, authService.signupForDevelopment());
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private AuthSessionResponse createSession(HttpServletResponse response, IssuedTokens tokens) {
        authCookies.setSessionCookies(response, tokens);
        return authService.session(appJwtService.parse(tokens.accessToken()));
    }
}
